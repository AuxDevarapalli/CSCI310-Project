package com.team04.splitsmart.ui;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.PopupMenu;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.history.HistoryQuery;
import com.team04.splitsmart.logic.validation.DateProvider;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.TransactionType;
import com.team04.splitsmart.model.User;
import com.team04.splitsmart.viewmodel.HistoryViewModel;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * The group history (FR 5.4 to FR 5.6, design Figure 7): every expense and settlement, newest
 * first by default, with keyword search, type / category / member filters and sorting.
 */
public class HistoryFragment extends BaseFragment implements Refreshable {
    private static final String ARG_GROUP_ID = "groupId";

    private HistoryViewModel viewModel;
    private TransactionAdapter adapter;
    private String groupId;

    private SwipeRefreshLayout refreshLayout;
    private TextInputEditText searchInput;
    private ChipGroup typeGroup;
    private Chip categoryChip;
    private Chip memberChip;
    private Chip sortChip;
    private Chip matchCaseChip;
    private RecyclerView list;
    private Chip monthChip;
    private MaterialButton exportButton;
    private final ActivityResultLauncher<String> createCsv = registerForActivityResult(
            new ActivityResultContracts.CreateDocument("text/csv"), this::finishExport);
    private TextView countText;
    private MaterialButton clearFiltersButton;
    private View emptyView;
    private TextView emptyTitle;
    private TextView emptyMessage;
    private View errorView;
    private TextView errorText;
    /** True while chips are being updated from the query, so listeners do not echo back. */
    private boolean renderingQuery;

    public static HistoryFragment newInstance(String groupId) {
        HistoryFragment fragment = new HistoryFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_history, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        groupId = requireArguments().getString(ARG_GROUP_ID);
        viewModel = obtainViewModel(HistoryViewModel.class);

        refreshLayout = view.findViewById(R.id.history_refresh);
        searchInput = view.findViewById(R.id.history_search);
        typeGroup = view.findViewById(R.id.history_type_group);
        categoryChip = view.findViewById(R.id.chip_category);
        memberChip = view.findViewById(R.id.chip_member);
        sortChip = view.findViewById(R.id.chip_sort);
        matchCaseChip = view.findViewById(R.id.chip_match_case);
        monthChip = view.findViewById(R.id.chip_month);
        exportButton = view.findViewById(R.id.history_export);
        countText = view.findViewById(R.id.history_count);
        clearFiltersButton = view.findViewById(R.id.history_clear_filters);
        emptyView = view.findViewById(R.id.history_empty);
        emptyTitle = view.findViewById(R.id.history_empty_title);
        emptyMessage = view.findViewById(R.id.history_empty_message);
        errorView = view.findViewById(R.id.history_error);
        errorText = view.findViewById(R.id.history_error_text);

        adapter = new TransactionAdapter(viewModel.getCurrentUserId(), transaction ->
                startActivity(TransactionDetailActivity.newIntent(requireContext(), groupId,
                        transaction.getId())));
        list = view.findViewById(R.id.history_list);
        list.setAdapter(adapter);

        refreshLayout.setOnRefreshListener(this::refresh);
        view.findViewById(R.id.history_retry).setOnClickListener(v -> refresh());
        searchInput.addTextChangedListener(new SimpleTextWatcher(text -> {
            if (!renderingQuery) {
                viewModel.setKeyword(text);
            }
        }));
        searchInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                UiUtils.hideKeyboard(v);
                return true;
            }
            return false;
        });
        typeGroup.setOnCheckedStateChangeListener((group, checkedIds) -> {
            if (renderingQuery) {
                return;
            }
            int checked = checkedIds.isEmpty() ? R.id.chip_type_all : checkedIds.get(0);
            if (checked == R.id.chip_type_expenses) {
                viewModel.setTypeFilter(TransactionType.EXPENSE);
            } else if (checked == R.id.chip_type_settlements) {
                viewModel.setTypeFilter(TransactionType.SETTLEMENT);
            } else {
                viewModel.setTypeFilter(null);
            }
        });
        categoryChip.setOnClickListener(v -> showCategoryMenu());
        categoryChip.setOnCloseIconClickListener(v -> viewModel.setCategoryFilter(null));
        memberChip.setOnClickListener(v -> showMemberMenu());
        memberChip.setOnCloseIconClickListener(v -> viewModel.setMemberFilter(null));
        monthChip.setOnClickListener(v -> showMonthFilter());
        monthChip.setOnCloseIconClickListener(v -> viewModel.setMonthFilter(null));
        exportButton.setOnClickListener(v -> startExport());
        sortChip.setOnClickListener(v -> viewModel.toggleSortOrder());
        matchCaseChip.setOnCheckedChangeListener((chip, checked) -> {
            if (!renderingQuery) {
                viewModel.setMatchCase(checked);
            }
        });
        clearFiltersButton.setOnClickListener(v -> {
            renderingQuery = true;
            searchInput.setText("");
            renderingQuery = false;
            viewModel.clearFilters();
        });

        viewModel.getVisible().observe(getViewLifecycleOwner(), this::renderList);
        viewModel.getQuery().observe(getViewLifecycleOwner(), this::renderQuery);
        viewModel.getUsers().observe(getViewLifecycleOwner(), users -> {
            adapter.setUsers(users);
            renderQuery(viewModel.getQuery().getValue());
        });
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            refreshLayout.setRefreshing(Boolean.TRUE.equals(loading));
            updateExportButton();
        });
        viewModel.getLoadError().observe(getViewLifecycleOwner(), error -> {
            updateExportButton();
            boolean showError = error != null && adapter.getItemCount() == 0;
            UiUtils.setVisible(errorView, showError);
            errorText.setText(error);
            if (showError) {
                UiUtils.setVisible(emptyView, false);
            } else if (error != null) {
                showMessage(error);
            }
        });
        observeMessages(viewModel);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    @Override
    public void refresh() {
        if (viewModel != null && groupId != null) {
            viewModel.load(groupId);
        }
    }

    private void renderList(List<Transaction> transactions) {
        UiUtils.submitKeepingTop(adapter, list, transactions);
        updateExportButton();
        if (transactions == null) {
            return;
        }
        UiUtils.setVisible(errorView, false);
        Integer total = viewModel.getTotalCount().getValue();
        int totalCount = total == null ? 0 : total;
        HistoryQuery query = viewModel.getQuery().getValue();
        boolean filtered = query != null && query.isFiltered();
        if (filtered) {
            countText.setText(getString(R.string.history_count_filtered,
                    transactions.size(), totalCount));
        } else {
            countText.setText(getResources().getQuantityString(R.plurals.transaction_count,
                    totalCount, totalCount));
        }
        boolean empty = transactions.isEmpty();
        UiUtils.setVisible(emptyView, empty);
        if (empty) {
            emptyTitle.setText(totalCount == 0
                    ? R.string.empty_history_title : R.string.empty_filtered_title);
            emptyMessage.setText(totalCount == 0
                    ? R.string.empty_history_message : R.string.empty_filtered_message);
        }
    }

    /** Shows which filters are active on the chips (FR 5.6). */
    private void renderQuery(HistoryQuery query) {
        if (query == null) {
            return;
        }
        renderingQuery = true;
        if (!UiUtils.textOf(searchInput).trim().equals(query.getKeyword())) {
            searchInput.setText(query.getKeyword());
            searchInput.setSelection(searchInput.length());
        }
        int typeChip;
        if (query.getTypeFilter() == TransactionType.EXPENSE) {
            typeChip = R.id.chip_type_expenses;
        } else if (query.getTypeFilter() == TransactionType.SETTLEMENT) {
            typeChip = R.id.chip_type_settlements;
        } else {
            typeChip = R.id.chip_type_all;
        }
        if (typeGroup.getCheckedChipId() != typeChip) {
            typeGroup.check(typeChip);
        }

        Category category = query.getCategoryFilter();
        categoryChip.setText(category == null
                ? getString(R.string.filter_category) : category.getDisplayName());
        categoryChip.setCloseIconVisible(category != null);
        categoryChip.setCloseIconResource(R.drawable.ic_close);

        String member = query.getMemberFilter();
        memberChip.setText(member == null ? getString(R.string.filter_member) : memberName(member));
        memberChip.setCloseIconVisible(member != null);
        memberChip.setCloseIconResource(R.drawable.ic_close);

        String month = query.getMonthFilter();
        monthChip.setText(month == null
                ? getString(R.string.filter_month) : DateProvider.formatMonthLabel(month));
        monthChip.setCloseIconVisible(month != null);

        sortChip.setText(query.getSortOrder().getDisplayName());
        if (matchCaseChip.isChecked() != query.isMatchCase()) {
            matchCaseChip.setChecked(query.isMatchCase());
        }
        UiUtils.setVisible(clearFiltersButton, query.isFiltered());
        renderingQuery = false;
    }

    private void showMonthFilter() {
        List<String> months = viewModel.getAvailableMonths();
        String selected = viewModel.getQuery().getValue().getMonthFilter();
        String[] labels = new String[months.size() + 1];
        labels[0] = getString(R.string.filter_all_months);
        int checked = selected == null ? 0 : -1;
        for (int i = 0; i < months.size(); i++) {
            String month = months.get(i);
            labels[i + 1] = DateProvider.formatMonthLabel(month);
            if (month.equals(selected)) {
                checked = i + 1;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.filter_month_title)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    viewModel.setMonthFilter(which == 0 ? null : months.get(which - 1));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void updateExportButton() {
        if (getView() != null) {
            exportButton.setEnabled(viewModel.canExport());
        }
    }

    private void startExport() {
        if (!viewModel.prepareExport(getString(R.string.history_export_unknown_member))) {
            return;
        }
        updateExportButton();
        String month = viewModel.getQuery().getValue().getMonthFilter();
        String fileName = month == null ? "SplitSmart-history.csv" : "SplitSmart-" + month + ".csv";
        try {
            createCsv.launch(fileName);
        } catch (ActivityNotFoundException e) {
            viewModel.takeExport();
            updateExportButton();
            showMessage(getString(R.string.history_export_error));
        }
    }

    private void finishExport(Uri destination) {
        String csv = viewModel.takeExport();
        updateExportButton();
        if (destination == null) {
            return;
        }
        if (csv == null) {
            showMessage(getString(R.string.history_export_retry));
            return;
        }
        Context context = requireContext().getApplicationContext();
        new Thread(() -> {
            int message = writeCsv(context, destination, csv)
                    ? R.string.history_export_success : R.string.history_export_error;
            new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(context, message, Toast.LENGTH_SHORT).show());
        }, "history-csv-export").start();
    }

    private static boolean writeCsv(Context context, Uri destination, String csv) {
        try (OutputStream output = context.getContentResolver().openOutputStream(destination, "wt")) {
            if (output == null) {
                return false;
            }
            // UTF-8 signature helps spreadsheet applications recognize non-ASCII names.
            output.write("﻿".getBytes(StandardCharsets.UTF_8));
            output.write(csv.getBytes(StandardCharsets.UTF_8));
            return true;
        } catch (IOException | SecurityException e) {
            return false;
        }
    }

    private String memberName(String uid) {
        Map<String, User> users = viewModel.getUsers().getValue();
        return UiUtils.nameOf(users, uid, viewModel.getCurrentUserId());
    }

    private void showCategoryMenu() {
        PopupMenu popup = new PopupMenu(requireContext(), categoryChip);
        Category[] categories = Category.values();
        popup.getMenu().add(Menu.NONE, 0, 0, R.string.filter_all_categories);
        for (int i = 0; i < categories.length; i++) {
            popup.getMenu().add(Menu.NONE, i + 1, i + 1, categories[i].getDisplayName());
        }
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            viewModel.setCategoryFilter(id == 0 ? null : categories[id - 1]);
            return true;
        });
        popup.show();
    }

    private void showMemberMenu() {
        List<User> members = viewModel.getMembers().getValue();
        if (members == null) {
            return;
        }
        PopupMenu popup = new PopupMenu(requireContext(), memberChip);
        popup.getMenu().add(Menu.NONE, 0, 0, R.string.filter_anyone);
        for (int i = 0; i < members.size(); i++) {
            User member = members.get(i);
            String label = member.getUid().equals(viewModel.getCurrentUserId())
                    ? getString(R.string.member_you, member.getName()) : member.getName();
            popup.getMenu().add(Menu.NONE, i + 1, i + 1, label);
        }
        popup.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            viewModel.setMemberFilter(id == 0 ? null : members.get(id - 1).getUid());
            return true;
        });
        popup.show();
    }
}
