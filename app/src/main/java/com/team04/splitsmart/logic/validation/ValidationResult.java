package com.team04.splitsmart.logic.validation;

/**
 * Outcome of a validation check. A failed result carries a user-facing message and, when the
 * problem belongs to one input, the key of that input so the screen can highlight it.
 */
public final class ValidationResult {
    public static final String FIELD_NAME = "name";
    public static final String FIELD_EMAIL = "email";
    public static final String FIELD_USC_ID = "uscId";
    public static final String FIELD_PASSWORD = "password";
    public static final String FIELD_CONFIRM_PASSWORD = "confirmPassword";
    public static final String FIELD_GROUP_NAME = "groupName";
    public static final String FIELD_DESCRIPTION = "description";
    public static final String FIELD_TITLE = "title";
    public static final String FIELD_AMOUNT = "amount";
    public static final String FIELD_PAYER = "payer";
    public static final String FIELD_PARTICIPANTS = "participants";
    public static final String FIELD_SPLIT = "split";
    public static final String FIELD_RECEIVER = "receiver";
    public static final String FIELD_NOTE = "note";

    private static final ValidationResult OK = new ValidationResult(true, null, null);

    private final boolean valid;
    private final String field;
    private final String errorMessage;

    private ValidationResult(boolean valid, String field, String errorMessage) {
        this.valid = valid;
        this.field = field;
        this.errorMessage = errorMessage;
    }

    public static ValidationResult ok() {
        return OK;
    }

    public static ValidationResult error(String errorMessage) {
        return new ValidationResult(false, null, errorMessage);
    }

    public static ValidationResult error(String field, String errorMessage) {
        return new ValidationResult(false, field, errorMessage);
    }

    public boolean isValid() {
        return valid;
    }

    /** Key of the offending input (one of the FIELD_ constants), or {@code null}. */
    public String getField() {
        return field;
    }

    /** User-facing explanation; {@code null} for a valid result. */
    public String getErrorMessage() {
        return errorMessage;
    }

    @Override
    public String toString() {
        return valid ? "valid" : "invalid(" + field + "): " + errorMessage;
    }
}
