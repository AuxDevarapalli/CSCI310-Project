package com.team04.splitsmart.logic.history;

/** History ordering options (FR 5.6). Newest first is the default (FR 5.4). */
public enum SortOrder {
    NEWEST_FIRST("Newest first"),
    OLDEST_FIRST("Oldest first");

    private final String displayName;

    SortOrder(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public SortOrder toggled() {
        return this == NEWEST_FIRST ? OLDEST_FIRST : NEWEST_FIRST;
    }
}
