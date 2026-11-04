package com.team04.splitsmart.viewmodel;

import com.team04.splitsmart.model.User;

/** A member the user can settle with, along with their current pair balance. */
public class ReceiverOption {
    private final User user;
    private final long netCents;

    public ReceiverOption(User user, long netCents) {
        this.user = user;
        this.netCents = netCents;
    }

    public User getUser() {
        return user;
    }

    /** Positive when this member owes the current user, negative when the current user owes them. */
    public long getNetCents() {
        return netCents;
    }
}
