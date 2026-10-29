package com.team04.splitsmart.viewmodel;

/**
 * Summary line under the participant list, e.g. "Amounts add up to $28.00, not $30.00".
 * The expense can only be saved while {@link #isValid()} is true (FR 3.6).
 */
public class SplitStatus {
    private final String message;
    private final boolean valid;

    public SplitStatus(String message, boolean valid) {
        this.message = message;
        this.valid = valid;
    }

    public String getMessage() {
        return message;
    }

    public boolean isValid() {
        return valid;
    }
}
