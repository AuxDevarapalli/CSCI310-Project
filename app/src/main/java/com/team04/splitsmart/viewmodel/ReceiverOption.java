package com.team04.splitsmart.viewmodel;

import com.team04.splitsmart.model.User;

/** A member the user could pay back, with how much the user currently owes them. */
public class ReceiverOption {
    private final User user;
    private final long owedCents;

    public ReceiverOption(User user, long owedCents) {
        this.user = user;
        this.owedCents = owedCents;
    }

    public User getUser() {
        return user;
    }

    /** What the current user owes this member right now; 0 when nothing is owed. */
    public long getOwedCents() {
        return owedCents;
    }
}
