package com.team04.splitsmart.viewmodel;

import com.team04.splitsmart.model.User;

/**
 * Another member the user could record a payment with, and how much is owed between them in
 * the direction chosen on the settlement form (the user paying them, or them paying the user).
 */
public class MemberOption {
    private final User user;
    private final long owedCents;

    public MemberOption(User user, long owedCents) {
        this.user = user;
        this.owedCents = owedCents;
    }

    public User getUser() {
        return user;
    }

    /** What the payer owes the receiver right now in the chosen direction; 0 when nothing is owed. */
    public long getOwedCents() {
        return owedCents;
    }
}
