package com.team04.splitsmart.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A cost one member (the payer) paid that two or more members share (FR 2.2). Every
 * participant except the payer owes their share to the payer (FR 3.7).
 */
public class Expense extends Transaction {
    private final String title;
    private final long totalCents;
    private final Category category;
    private final String payerId;
    private final SplitMethod splitMethod;
    private final List<Share> shares;

    public Expense(String id, String groupId, String creatorId, String date, long createdAt,
                   String note, String title, long totalCents, Category category,
                   String payerId, SplitMethod splitMethod, List<Share> shares) {
        super(id, groupId, creatorId, date, createdAt, note);
        this.title = title == null ? "" : title.trim();
        this.totalCents = totalCents;
        this.category = category == null ? Category.DEFAULT : category;
        this.payerId = payerId;
        this.splitMethod = splitMethod == null ? SplitMethod.EQUAL : splitMethod;
        this.shares = shares == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(shares));
    }

    @Override
    public TransactionType getType() {
        return TransactionType.EXPENSE;
    }

    @Override
    public long getAmountCents() {
        return totalCents;
    }

    public String getTitle() {
        return title;
    }

    public long getTotalCents() {
        return totalCents;
    }

    public Category getCategory() {
        return category;
    }

    public String getPayerId() {
        return payerId;
    }

    public SplitMethod getSplitMethod() {
        return splitMethod;
    }

    /** One share per participant, in the order participants were listed on the form. */
    public List<Share> getShares() {
        return shares;
    }

    public List<String> getParticipantIds() {
        List<String> ids = new ArrayList<>(shares.size());
        for (Share share : shares) {
            ids.add(share.getParticipantId());
        }
        return ids;
    }

    public boolean isParticipant(String uid) {
        return findShare(uid) != null;
    }

    /** The share belonging to {@code uid}, or {@code null} if they are not a participant. */
    public Share findShare(String uid) {
        if (uid == null) {
            return null;
        }
        for (Share share : shares) {
            if (uid.equals(share.getParticipantId())) {
                return share;
            }
        }
        return null;
    }

    /** Amount {@code uid} is responsible for, in cents; 0 when not a participant. */
    public long getShareFor(String uid) {
        Share share = findShare(uid);
        return share == null ? 0 : share.getAmountCents();
    }

    @Override
    public List<String> getInvolvedUserIds() {
        Set<String> ids = new LinkedHashSet<>();
        if (payerId != null) {
            ids.add(payerId);
        }
        ids.addAll(getParticipantIds());
        return new ArrayList<>(ids);
    }
}
