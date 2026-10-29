package com.team04.splitsmart.model;

/** The three ways an expense total can be divided into shares (FR 3.1). */
public enum SplitMethod {
    EQUAL("Equal split"),
    EXACT("Exact amounts"),
    PERCENTAGE("Percentages");

    private final String displayName;

    SplitMethod(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /** Parses a stored enum name, falling back to {@link #EQUAL} for unknown values. */
    public static SplitMethod fromName(String name) {
        if (name != null) {
            for (SplitMethod method : values()) {
                if (method.name().equals(name)) {
                    return method;
                }
            }
        }
        return EQUAL;
    }
}
