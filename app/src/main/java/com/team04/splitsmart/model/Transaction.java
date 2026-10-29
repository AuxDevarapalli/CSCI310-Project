package com.team04.splitsmart.model;

import java.util.List;

/**
 * Common base of the two records in a group's history: {@link Expense} and {@link Settlement}.
 * Instances are immutable; editing a record creates a new instance with the same id, creator,
 * date and creation time.
 */
public abstract class Transaction {
    private final String id;
    private final String groupId;
    private final String creatorId;
    private final String date;
    private final long createdAt;
    private final String note;

    protected Transaction(String id, String groupId, String creatorId, String date,
                          long createdAt, String note) {
        this.id = id;
        this.groupId = groupId;
        this.creatorId = creatorId;
        this.date = date == null ? "" : date;
        this.createdAt = createdAt;
        this.note = note == null ? "" : note.trim();
    }

    /** Firestore document id, or {@code null} for a record that has not been saved yet. */
    public String getId() {
        return id;
    }

    public String getGroupId() {
        return groupId;
    }

    /** The user who recorded this transaction; only they may edit or delete it (FR 2.7, 5.3). */
    public String getCreatorId() {
        return creatorId;
    }

    /** Creation date in YYYY-MM-DD form, recorded automatically from the device (FR 2.4). */
    public String getDate() {
        return date;
    }

    /** Creation time in epoch milliseconds; used to order the history precisely. */
    public long getCreatedAt() {
        return createdAt;
    }

    /** Optional note; empty string when absent. */
    public String getNote() {
        return note;
    }

    public boolean hasNote() {
        return !note.isEmpty();
    }

    public boolean isCreatedBy(String uid) {
        return uid != null && uid.equals(creatorId);
    }

    /** True when {@code uid} takes part in this transaction (history member filter, FR 5.6). */
    public boolean involves(String uid) {
        return uid != null && getInvolvedUserIds().contains(uid);
    }

    public abstract TransactionType getType();

    /** Expense total or settlement amount, in cents. */
    public abstract long getAmountCents();

    /** Every user taking part, without duplicates (FR 5.4 "users involved"). */
    public abstract List<String> getInvolvedUserIds();
}
