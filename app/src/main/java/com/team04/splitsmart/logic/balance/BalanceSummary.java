package com.team04.splitsmart.logic.balance;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Everything the balance page shows for the logged-in user (FR 4.1): the total they need to
 * pay, the total they should receive, and the individual balance with every other member.
 * The two totals are deliberately kept separate and are never replaced by a single net amount
 * (FR 4.3).
 */
public class BalanceSummary {
    private final long totalToPayCents;
    private final long totalToReceiveCents;
    private final List<PairBalance> pairs;

    public BalanceSummary(long totalToPayCents, long totalToReceiveCents, List<PairBalance> pairs) {
        this.totalToPayCents = totalToPayCents;
        this.totalToReceiveCents = totalToReceiveCents;
        this.pairs = pairs == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(pairs));
    }

    /** Sum of every individual balance the user owes (FR 4.3). */
    public long getTotalToPayCents() {
        return totalToPayCents;
    }

    /** Sum of every individual balance owed to the user (FR 4.3). */
    public long getTotalToReceiveCents() {
        return totalToReceiveCents;
    }

    /** One entry per other member of the group. */
    public List<PairBalance> getPairs() {
        return pairs;
    }

    /** True when the user neither owes nor is owed anything in the group. */
    public boolean isAllSettled() {
        return totalToPayCents == 0 && totalToReceiveCents == 0;
    }
}
