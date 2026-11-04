package com.team04.splitsmart.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Menu;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.team04.splitsmart.R;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.viewmodel.GroupViewModel;

/**
 * A group's page (FR 1.6): History, Balances, Summary and Members tabs, plus buttons to add an
 * expense or record a settlement. The owner also gets Edit and Delete in the menu (FR 1.7).
 */
public class GroupDetailActivity extends BaseActivity {
    public static final int TAB_HISTORY = 0;
    public static final int TAB_BALANCES = 1;
    public static final int TAB_SUMMARY = 2;
    public static final int TAB_MEMBERS = 3;
    public static final int TAB_COUNT = 4;

    private static final String EXTRA_GROUP_ID = "groupId";
    private static final String EXTRA_START_TAB = "startTab";
    private static final String EXTRA_JUST_CREATED = "justCreated";

    private GroupViewModel viewModel;
    private MaterialToolbar toolbar;
    private String groupId;

    public static Intent newIntent(Context context, String groupId) {
        return newIntent(context, groupId, TAB_HISTORY, false);
    }

    public static Intent newIntent(Context context, String groupId, int startTab,
                                   boolean justCreated) {
        return new Intent(context, GroupDetailActivity.class)
                .putExtra(EXTRA_GROUP_ID, groupId)
                .putExtra(EXTRA_START_TAB, startTab)
                .putExtra(EXTRA_JUST_CREATED, justCreated);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSignedIn()) {
            return;
        }
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        if (groupId == null) {
            finish();
            return;
        }
        setContentWithInsets(R.layout.activity_group_detail);
        viewModel = obtainViewModel(GroupViewModel.class);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            int id = item.getItemId();
            if (id == R.id.action_refresh) {
                refreshAll();
                return true;
            }
            if (id == R.id.action_edit_group) {
                startActivity(GroupFormActivity.newEditIntent(this, groupId));
                return true;
            }
            if (id == R.id.action_delete_group) {
                confirmDelete();
                return true;
            }
            if (id == R.id.action_leave_group) {
                confirmLeave();
                return true;
            }
            return false;
        });

        ViewPager2 pager = findViewById(R.id.group_pager);
        TabLayout tabs = findViewById(R.id.group_tabs);
        pager.setAdapter(new GroupPagerAdapter(this, groupId));
        int[] titles = {R.string.tab_history, R.string.tab_balances, R.string.tab_summary,
                R.string.tab_members};
        new TabLayoutMediator(tabs, pager, (tab, position) -> tab.setText(titles[position]))
                .attach();
        if (savedInstanceState == null) {
            pager.setCurrentItem(getIntent().getIntExtra(EXTRA_START_TAB, TAB_HISTORY), false);
        }

        findViewById(R.id.add_expense_button).setOnClickListener(
                v -> startActivity(ExpenseFormActivity.newCreateIntent(this, groupId)));
        findViewById(R.id.settle_up_button).setOnClickListener(
                v -> startActivity(SettlementFormActivity.newCreateIntent(this, groupId, null)));

        View progress = findViewById(R.id.group_detail_progress);
        viewModel.getLoading().observe(this,
                loading -> UiUtils.setVisible(progress, Boolean.TRUE.equals(loading)));
        viewModel.getGroup().observe(this, this::renderGroup);
        viewModel.getLoadError().observe(this, error -> {
            if (error != null) {
                showMessage(error);
            }
        });
        viewModel.getGroupGone().observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                finish();
            }
        });
        viewModel.getGroupDeleted().observe(this, event -> {
            if (event.getContentIfNotHandled() != null) {
                Toast.makeText(this, R.string.message_group_deleted, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        viewModel.getLeftGroup().observe(this, event -> {
            if (event.getContentIfNotHandled() != null) {
                Toast.makeText(this, R.string.message_left_group, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        // Live updates: another member's changes reload every tab automatically.
        viewModel.getRemoteChange().observe(this, event -> {
            if (event.getContentIfNotHandled() != null) {
                refreshAll();
            }
        });
        viewModel.startLiveUpdates(groupId);
        observeMessages(viewModel);

        if (savedInstanceState == null && getIntent().getBooleanExtra(EXTRA_JUST_CREATED, false)) {
            showMessage(getString(R.string.message_group_created));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            // The visible tab reloads itself in its own onResume.
            viewModel.load(groupId);
        }
    }

    private void renderGroup(Group group) {
        if (group == null) {
            return;
        }
        toolbar.setTitle(group.getName());
        Bitmap bitmap = GroupPhoto.decode(group.getPhoto());
        if (bitmap == null) {
            toolbar.setLogo(null);
        } else {
            int size = Math.round(32 * getResources().getDisplayMetrics().density);
            Bitmap icon = Bitmap.createScaledBitmap(bitmap, size, size, true);
            icon.setDensity(Bitmap.DENSITY_NONE);
            androidx.core.graphics.drawable.RoundedBitmapDrawable logo =
                    androidx.core.graphics.drawable.RoundedBitmapDrawableFactory.create(getResources(), icon);
            logo.setCircular(true);
            toolbar.setLogo(logo);
        }
        String members = getResources().getQuantityString(R.plurals.member_count,
                group.getMemberCount(), group.getMemberCount());
        toolbar.setSubtitle(viewModel.isOwner()
                ? getString(R.string.group_meta_owner, members) : members);
        Menu menu = toolbar.getMenu();
        boolean owner = viewModel.isOwner();
        menu.findItem(R.id.action_edit_group).setVisible(owner);
        menu.findItem(R.id.action_delete_group).setVisible(owner);
        menu.findItem(R.id.action_leave_group).setVisible(!owner);
    }

    private void confirmLeave() {
        Group group = viewModel.getGroup().getValue();
        String name = group == null ? "" : group.getName();
        new ConfirmDialog(this,
                getString(R.string.confirm_leave_title, name),
                getString(R.string.confirm_leave_message),
                getString(R.string.action_leave),
                viewModel::leaveGroup).show();
    }

    private void confirmDelete() {
        Group group = viewModel.getGroup().getValue();
        String name = group == null ? "" : group.getName();
        new ConfirmDialog(this,
                getString(R.string.confirm_delete_group_title, name),
                getString(R.string.confirm_delete_group_message),
                getString(R.string.action_delete),
                viewModel::deleteGroup).show();
    }

    /** Reloads the group and every tab that is currently created. */
    private void refreshAll() {
        viewModel.load(groupId);
        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            if (fragment instanceof Refreshable) {
                ((Refreshable) fragment).refresh();
            }
        }
    }
}
