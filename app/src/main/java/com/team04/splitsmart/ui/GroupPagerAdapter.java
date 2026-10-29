package com.team04.splitsmart.ui;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

/** The four tabs of a group: History, Balances, Summary and Members. */
public class GroupPagerAdapter extends FragmentStateAdapter {
    private final String groupId;

    public GroupPagerAdapter(@NonNull FragmentActivity activity, String groupId) {
        super(activity);
        this.groupId = groupId;
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        if (position == GroupDetailActivity.TAB_HISTORY) {
            return HistoryFragment.newInstance(groupId);
        }
        if (position == GroupDetailActivity.TAB_BALANCES) {
            return BalanceFragment.newInstance(groupId);
        }
        if (position == GroupDetailActivity.TAB_SUMMARY) {
            return SummaryFragment.newInstance(groupId);
        }
        return MembersFragment.newInstance(groupId);
    }

    @Override
    public int getItemCount() {
        return GroupDetailActivity.TAB_COUNT;
    }
}
