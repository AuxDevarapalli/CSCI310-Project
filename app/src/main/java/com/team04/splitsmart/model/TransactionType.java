package com.team04.splitsmart.model;

/** The two kinds of records shown in a group's history (FR 5.4). */
public enum TransactionType {
    EXPENSE("Expense"),
    SETTLEMENT("Settlement");

    private final String displayName;

    TransactionType(String displayName) {
        this.displayName = displayName;
    }

    /** The label shown in the history, e.g. "Settlement". */
    public String getDisplayName() {
        return displayName;
    }

    /** Parses a stored enum name; returns {@code null} for unknown values. */
    public static TransactionType fromName(String name) {
        if (name != null) {
            for (TransactionType type : values()) {
                if (type.name().equals(name)) {
                    return type;
                }
            }
        }
        return null;
    }
}
