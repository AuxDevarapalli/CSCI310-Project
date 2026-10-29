package com.team04.splitsmart.logic.balance;

import com.team04.splitsmart.model.User;

/**
 * The individual balance between the logged-in user and one other member (FR 4.1, FR 4.2).
 * A positive {@code netCents} means the other member owes the current user; a negative value
 * means the current user owes the other member.
 */
public class PairBalance {
    private final User otherUser;
    private final long netCents;

    public PairBalance(User otherUser, long netCents) {
        this.otherUser = otherUser;
        this.netCents = netCents;
    }

    public User getOtherUser() {
        return otherUser;
    }

    public String getOtherUserId() {
        return otherUser.getUid();
    }

    public long getNetCents() {
        return netCents;
    }

    /** True when neither user owes the other anything. */
    public boolean isSettled() {
        return netCents == 0;
    }

    /** True when the other member owes the current user. */
    public boolean owesCurrentUser() {
        return netCents > 0;
    }

    /** True when the current user owes the other member. */
    public boolean currentUserOwes() {
        return netCents < 0;
    }

    /** The size of the debt in either direction, always zero or positive. */
    public long getAbsoluteCents() {
        return Math.abs(netCents);
    }
}
