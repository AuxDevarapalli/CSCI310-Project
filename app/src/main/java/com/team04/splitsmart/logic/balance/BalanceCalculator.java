package com.team04.splitsmart.logic.balance;

import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Computes pairwise balances on demand from a group's transactions (FR 4.2 to FR 4.4).
 * Balances are never stored, so editing or deleting a transaction is reflected the next time
 * they are computed, and debts are never simplified across different pairs of users.
 */
public class BalanceCalculator {

    /**
     * Net amount between two users; positive means {@code otherUid} owes {@code uid}.
     * <ul>
     *   <li>An expense {@code uid} paid adds the other member's share.</li>
     *   <li>An expense the other member paid subtracts {@code uid}'s share.</li>
     *   <li>A settlement {@code uid} paid to the other member adds its amount.</li>
     *   <li>A settlement {@code uid} received from the other member subtracts its amount.</li>
     *   <li>Transactions involving a third member count for nothing (FR 4.4).</li>
     * </ul>
     */
    public long netBetween(String uid, String otherUid, List<? extends Transaction> transactions) {
        if (uid == null || otherUid == null || uid.equals(otherUid) || transactions == null) {
            return 0;
        }
        long net = 0;
        for (Transaction transaction : transactions) {
            if (transaction instanceof Expense) {
                Expense expense = (Expense) transaction;
                if (uid.equals(expense.getPayerId())) {
                    net += expense.getShareFor(otherUid);
                } else if (otherUid.equals(expense.getPayerId())) {
                    net -= expense.getShareFor(uid);
                }
            } else if (transaction instanceof Settlement) {
                Settlement settlement = (Settlement) transaction;
                if (uid.equals(settlement.getPayerId())
                        && otherUid.equals(settlement.getReceiverId())) {
                    net += settlement.getAmountCents();
                } else if (otherUid.equals(settlement.getPayerId())
                        && uid.equals(settlement.getReceiverId())) {
                    net -= settlement.getAmountCents();
                }
            }
        }
        return net;
    }

    /**
     * How much {@code debtorUid} currently owes {@code creditorUid}; zero when they owe nothing.
     * This is the upper limit for a settlement between them (FR 5.2).
     */
    public long amountOwed(String debtorUid, String creditorUid,
                           List<? extends Transaction> transactions) {
        return Math.max(0, -netBetween(debtorUid, creditorUid, transactions));
    }

    /**
     * Builds the balance page for {@code uid} (FR 4.1, FR 4.3): one {@link PairBalance} per
     * other member, plus the total to pay (sum of what the user owes) and the total to receive
     * (sum of what is owed to the user).
     */
    public BalanceSummary computeSummary(String uid, List<User> members,
                                         List<? extends Transaction> transactions) {
        List<PairBalance> pairs = new ArrayList<>();
        long totalToPay = 0;
        long totalToReceive = 0;
        if (members != null) {
            for (User member : members) {
                if (member.getUid().equals(uid)) {
                    continue;
                }
                long net = netBetween(uid, member.getUid(), transactions);
                pairs.add(new PairBalance(member, net));
                if (net > 0) {
                    totalToReceive += net;
                } else {
                    totalToPay += -net;
                }
            }
        }
        return new BalanceSummary(totalToPay, totalToReceive, pairs);
    }

    /**
     * Totals only, for screens that do not need member details (the group list dashboard).
     * Returns {@code {totalToPayCents, totalToReceiveCents}}.
     */
    public long[] computeTotals(String uid, List<String> memberIds,
                                List<? extends Transaction> transactions) {
        long totalToPay = 0;
        long totalToReceive = 0;
        if (memberIds != null) {
            for (String memberId : memberIds) {
                long net = netBetween(uid, memberId, transactions);
                if (net > 0) {
                    totalToReceive += net;
                } else {
                    totalToPay += -net;
                }
            }
        }
        return new long[]{totalToPay, totalToReceive};
    }
}
