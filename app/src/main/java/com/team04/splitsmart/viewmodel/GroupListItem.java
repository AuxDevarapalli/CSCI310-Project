package com.team04.splitsmart.viewmodel;

import com.team04.splitsmart.model.Group;

/**
 * One row of the group list: the group plus the logged-in user's totals in it. The totals are
 * loaded after the group itself, so {@link #isBalanceLoaded()} is false for a moment.
 */
public class GroupListItem {
    private final Group group;
    private final boolean balanceLoaded;
    private final long totalToPayCents;
    private final long totalToReceiveCents;

    public GroupListItem(Group group) {
        this(group, false, 0, 0);
    }

    public GroupListItem(Group group, boolean balanceLoaded, long totalToPayCents,
                         long totalToReceiveCents) {
        this.group = group;
        this.balanceLoaded = balanceLoaded;
        this.totalToPayCents = totalToPayCents;
        this.totalToReceiveCents = totalToReceiveCents;
    }

    public Group getGroup() {
        return group;
    }

    public boolean isBalanceLoaded() {
        return balanceLoaded;
    }

    public long getTotalToPayCents() {
        return totalToPayCents;
    }

    public long getTotalToReceiveCents() {
        return totalToReceiveCents;
    }

    public boolean isSettled() {
        return balanceLoaded && totalToPayCents == 0 && totalToReceiveCents == 0;
    }
}
