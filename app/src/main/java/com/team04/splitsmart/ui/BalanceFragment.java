package com.team04.splitsmart.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.balance.BalanceSummary;
import com.team04.splitsmart.logic.balance.PairBalance;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.viewmodel.BalanceViewModel;

/**
 * The balance page (FR 4.1 to FR 4.5, design Figure 5): total to pay, total to receive, and the
 * individual balance with each member. Settlement records are not listed here; they appear in
 * the history. Reloads every time it is shown.
 */
public class BalanceFragment extends BaseFragment implements Refreshable {
    private static final String ARG_GROUP_ID = "groupId";

    private BalanceViewModel viewModel;
    private String groupId;
    private SwipeRefreshLayout refreshLayout;
    private TextView totalPay;
    private TextView totalReceive;
    private LinearLayout rows;
    private View settledBanner;
    private View emptyView;
    private View errorView;
    private TextView errorText;

    public static BalanceFragment newInstance(String groupId) {
        BalanceFragment fragment = new BalanceFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_balance, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        groupId = requireArguments().getString(ARG_GROUP_ID);
        viewModel = obtainViewModel(BalanceViewModel.class);

        refreshLayout = view.findViewById(R.id.balance_refresh);
        totalPay = view.findViewById(R.id.balance_total_pay);
        totalReceive = view.findViewById(R.id.balance_total_receive);
        rows = view.findViewById(R.id.balance_rows);
        settledBanner = view.findViewById(R.id.balance_settled_banner);
        emptyView = view.findViewById(R.id.balance_empty);
        errorView = view.findViewById(R.id.balance_error);
        errorText = view.findViewById(R.id.balance_error_text);

        refreshLayout.setOnRefreshListener(this::refresh);
        view.findViewById(R.id.balance_retry).setOnClickListener(v -> refresh());
        view.findViewById(R.id.balance_share).setOnClickListener(v -> shareBalances());

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
            viewModel.loadBalances(groupId);
        }
    }

    private void render(BalanceSummary summary) {
        if (summary == null) {
            return;
        }
        totalPay.setText(Money.format(summary.getTotalToPayCents()));
        totalReceive.setText(Money.format(summary.getTotalToReceiveCents()));
        UiUtils.setVisible(settledBanner, summary.isAllSettled());
        rows.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (PairBalance pair : summary.getPairs()) {
            View row = inflater.inflate(R.layout.item_balance, rows, false);
            bindRow(row, pair);
            rows.addView(row);
        }
        UiUtils.setVisible(emptyView, summary.getPairs().isEmpty());
    }

    private void bindRow(View row, PairBalance pair) {
        TextView avatar = row.findViewById(R.id.balance_avatar);
        TextView name = row.findViewById(R.id.balance_name);
        TextView status = row.findViewById(R.id.balance_status);
        TextView settleButton = row.findViewById(R.id.balance_settle_button);

        UiUtils.bindAvatar(avatar, pair.getOtherUser());
        name.setText(pair.getOtherUser().getName());
        status.setText(describe(pair));
        int color;
        if (pair.owesCurrentUser()) {
            color = R.color.amount_positive;
        } else if (pair.currentUserOwes()) {
            color = R.color.amount_negative;
        } else {
            color = R.color.amount_neutral;
        }
        status.setTextColor(UiUtils.color(requireContext(), color));
        // Extra: shortcut that opens the settlement form for this member, from either side.
        UiUtils.setVisible(settleButton, pair.currentUserOwes() || pair.owesCurrentUser());
        if (pair.owesCurrentUser()) {
            settleButton.setText(R.string.action_record_payment);
            settleButton.setOnClickListener(v -> startActivity(
                    SettlementFormActivity.newReceivedIntent(requireContext(), groupId,
                            pair.getOtherUserId())));
        } else {
            settleButton.setText(R.string.action_settle_up);
            settleButton.setOnClickListener(v -> startActivity(
                    SettlementFormActivity.newCreateIntent(requireContext(), groupId,
                            pair.getOtherUserId())));
        }
    }

    /** "Bob owes you $10.00", "You owe David $4.00" or "You and Erin are settled up". */
    private String describe(PairBalance pair) {
        String name = pair.getOtherUser().getName();
        String amount = Money.format(pair.getAbsoluteCents());
        if (pair.owesCurrentUser()) {
            return getString(R.string.balance_owes_you, name, amount);
        }
        if (pair.currentUserOwes()) {
            return getString(R.string.balance_you_owe, name, amount);
        }
        return getString(R.string.balance_settled_up, name);
    }

    /** Extra feature: share the balances as text through any messaging app. */
    private void shareBalances() {
        BalanceSummary summary = viewModel.getSummary().getValue();
        if (summary == null) {
            return;
        }
        Group group = viewModel.getGroup().getValue();
        StringBuilder text = new StringBuilder();
        text.append(getString(R.string.share_balances_header,
                group == null ? "" : group.getName())).append('\n');
        text.append(getString(R.string.label_total_to_pay)).append(": ")
                .append(Money.format(summary.getTotalToPayCents())).append('\n');
        text.append(getString(R.string.label_total_to_receive)).append(": ")
                .append(Money.format(summary.getTotalToReceiveCents())).append('\n');
        for (PairBalance pair : summary.getPairs()) {
            text.append("• ").append(describe(pair)).append('\n');
        }
        Intent send = new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT, text.toString().trim());
        startActivity(Intent.createChooser(send, getString(R.string.action_share_balances)));
    }
}
