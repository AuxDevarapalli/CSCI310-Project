package com.team04.splitsmart.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.appbar.MaterialToolbar;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.viewmodel.GroupListItem;
import com.team04.splitsmart.viewmodel.GroupListViewModel;

import java.util.List;

/**
 * Home screen: every group the user belongs to (FR 1.4, FR 1.6), plus how much the user owes
 * or is owed in each one. Reloads whenever it is shown so new groups appear (NFR 3).
 */
public class GroupListActivity extends BaseActivity {
    private GroupListViewModel viewModel;
    private GroupAdapter adapter;
    private SwipeRefreshLayout refreshLayout;
    private View emptyView;
    private View errorView;
    private TextView errorText;
    private View overallCard;
    private TextView overallToPay;
    private TextView overallToReceive;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSignedIn()) {
            return;
        }
        setContentWithInsets(R.layout.activity_group_list);
        viewModel = obtainViewModel(GroupListViewModel.class);
        String currentUid = viewModel.getCurrentUserId();

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                return true;
            }
            return false;
        });
        refreshLayout = findViewById(R.id.groups_refresh);
        emptyView = findViewById(R.id.groups_empty);
        errorView = findViewById(R.id.groups_error);
        errorText = findViewById(R.id.groups_error_text);
        overallCard = findViewById(R.id.overall_card);
        overallToPay = findViewById(R.id.overall_to_pay);
        overallToReceive = findViewById(R.id.overall_to_receive);

        adapter = new GroupAdapter(currentUid, item -> startActivity(
                GroupDetailActivity.newIntent(this, item.getGroup().getId())));
        RecyclerView list = findViewById(R.id.groups_list);
        list.setAdapter(adapter);

        refreshLayout.setOnRefreshListener(viewModel::loadGroups);
        findViewById(R.id.groups_retry).setOnClickListener(v -> viewModel.loadGroups());
        findViewById(R.id.groups_new_fab).setOnClickListener(
                v -> startActivity(GroupFormActivity.newCreateIntent(this)));

        viewModel.getGroups().observe(this, this::render);
        viewModel.getLoading().observe(this,
                loading -> refreshLayout.setRefreshing(Boolean.TRUE.equals(loading)));
        viewModel.getLoadError().observe(this, error -> {
            UiUtils.setVisible(errorView, error != null && adapter.getItemCount() == 0);
            errorText.setText(error);
            if (error != null && adapter.getItemCount() > 0) {
                showMessage(error);
            }
        });
        observeMessages(viewModel);
        viewModel.startLiveUpdates();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            viewModel.loadGroups();
        }
    }

    private void render(List<GroupListItem> items) {
        adapter.submitList(items);
        boolean empty = items == null || items.isEmpty();
        UiUtils.setVisible(emptyView, empty);
        if (!empty) {
            UiUtils.setVisible(errorView, false);
        }
        UiUtils.setVisible(overallCard, !empty);
        overallToPay.setText(Money.format(GroupListViewModel.totalToPay(items)));
        overallToReceive.setText(Money.format(GroupListViewModel.totalToReceive(items)));
    }
}
