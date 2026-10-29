package com.team04.splitsmart.model;

/**
 * The five predefined expense categories (FR 2.5). Creating, editing or deleting categories is
 * not supported. {@link #OTHER} is the default for every new expense.
 */
public enum Category {
    FOOD("Food"),
    HOUSING("Housing"),
    TRANSPORTATION("Transportation"),
    ENTERTAINMENT("Entertainment"),
    OTHER("Other");

    /** Category selected when a new expense form opens (FR 2.5). */
    public static final Category DEFAULT = OTHER;

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * Parses a stored enum name. Unknown or missing values fall back to {@link #DEFAULT} so a
     * malformed document never crashes the app.
     */
    public static Category fromName(String name) {
        if (name != null) {
            for (Category category : values()) {
                if (category.name().equals(name)) {
                    return category;
                }
            }
        }
        return DEFAULT;
    }
}
