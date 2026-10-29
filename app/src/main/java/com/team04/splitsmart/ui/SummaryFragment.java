package com.team04.splitsmart.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.history.MonthlySummary;
import com.team04.splitsmart.logic.validation.DateProvider;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.viewmodel.SummaryViewModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Monthly expense summary (FR 5.7): pick a month to see how many expenses were created, the
 * group's total spending and the total for each category. Settlements are not included.
 */
public class SummaryFragment extends BaseFragment implements Refreshable {
    private static final String ARG_GROUP_ID = "groupId";

    private SummaryViewModel viewModel;
    private String groupId;
    private SwipeRefreshLayout refreshLayout;
    private TextView monthLabel;
    private TextView countText;
    private TextView totalText;
    private View emptyNote;
    private View errorView;
    private TextView errorText;
    private final Map<Category, View> categoryRows = new EnumMap<>(Category.class);

    public static SummaryFragment newInstance(String groupId) {
        SummaryFragment fragment = new SummaryFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_summary, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        groupId = requireArguments().getString(ARG_GROUP_ID);
        viewModel = obtainViewModel(SummaryViewModel.class);

        refreshLayout = view.findViewById(R.id.summary_refresh);
        monthLabel = view.findViewById(R.id.summary_month);
        countText = view.findViewById(R.id.summary_count);
        totalText = view.findViewById(R.id.summary_total);
        emptyNote = view.findViewById(R.id.summary_empty_note);
        errorView = view.findViewById(R.id.summary_error);
        errorText = view.findViewById(R.id.summary_error_text);
        buildCategoryRows(view.findViewById(R.id.summary_categories));

        refreshLayout.setOnRefreshListener(this::refresh);
        view.findViewById(R.id.summary_retry).setOnClickListener(v -> refresh());
        view.findViewById(R.id.summary_prev).setOnClickListener(v -> viewModel.previousMonth());
        view.findViewById(R.id.summary_next).setOnClickListener(v -> viewModel.nextMonth());
        view.findViewById(R.id.summary_month_picker).setOnClickListener(v -> showMonthPicker());

        viewModel.getMonth().observe(getViewLifecycleOwner(),
                month -> monthLabel.setText(DateProvider.formatMonthLabel(month)));
        viewModel.getSummary().observe(getViewLifecycleOwner(), this::render);
        viewModel.getLoading().observe(getViewLifecycleOwner(),
                loading -> refreshLayout.setRefreshing(Boolean.TRUE.equals(loading)));
        viewModel.getLoadError().observe(getViewLifecycleOwner(), error -> {
            UiUtils.setVisible(errorView, error != null);
            errorText.setText(error);
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

    private void buildCategoryRows(LinearLayout container) {
        container.removeAllViews();
        categoryRows.clear();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (Category category : Category.values()) {
            View row = inflater.inflate(R.layout.item_summary_category, container, false);
            ImageView icon = row.findViewById(R.id.category_icon);
            TextView name = row.findViewById(R.id.category_name);
            LinearProgressIndicator progress = row.findViewById(R.id.category_progress);
            UiUtils.bindIconBadge(icon, UiUtils.iconFor(category), UiUtils.colorFor(category));
            name.setText(category.getDisplayName());
            int accent = UiUtils.color(requireContext(), UiUtils.colorFor(category));
            progress.setIndicatorColor(accent);
            progress.setTrackColor(ColorUtils.setAlphaComponent(accent, 48));
            container.addView(row);
            categoryRows.put(category, row);
        }
    }

    private void render(MonthlySummary summary) {
        if (summary == null) {
            return;
        }
        countText.setText(String.valueOf(summary.getExpenseCount()));
        totalText.setText(Money.format(summary.getTotalCents()));
        long total = summary.getTotalCents();
        for (Map.Entry<Category, View> entry : categoryRows.entrySet()) {
            long amount = summary.getCategoryTotal(entry.getKey());
            View row = entry.getValue();
            TextView amountText = row.findViewById(R.id.category_amount);
            TextView percentText = row.findViewById(R.id.category_percent);
            LinearProgressIndicator progress = row.findViewById(R.id.category_progress);
            int percent = total == 0 ? 0 : (int) Math.round(amount * 100.0 / total);
            amountText.setText(Money.format(amount));
            percentText.setText(getString(R.string.percent_value, percent));
            progress.setProgressCompat(percent, true);
            row.setAlpha(amount == 0 ? 0.6f : 1f);
        }
        UiUtils.setVisible(emptyNote, summary.isEmpty());
    }

    /** Lets the user jump to any month that has expenses, or the month shown now. */
    private void showMonthPicker() {
        List<String> months = new ArrayList<>();
        List<String> withExpenses = viewModel.getMonthsWithExpenses().getValue();
        if (withExpenses != null) {
            months.addAll(withExpenses);
        }
        String selected = viewModel.getMonth().getValue();
        if (selected != null && !months.contains(selected)) {
            months.add(selected);
        }
        Collections.sort(months, Collections.reverseOrder());
        String[] labels = new String[months.size()];
        int checked = -1;
        for (int i = 0; i < months.size(); i++) {
            labels[i] = DateProvider.formatMonthLabel(months.get(i));
            if (months.get(i).equals(selected)) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.title_choose_month)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    viewModel.loadMonth(months.get(which));
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }
}
