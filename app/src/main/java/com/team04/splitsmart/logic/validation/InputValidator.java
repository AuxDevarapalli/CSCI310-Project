package com.team04.splitsmart.logic.validation;

import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.model.Share;
import com.team04.splitsmart.model.SplitMethod;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validates everything a user types before it reaches the database. Each method returns the
 * first problem found so the screen can show one clear message at a time.
 */
public class InputValidator {
    public static final String USC_EMAIL_DOMAIN = "@usc.edu";
    /** Firebase Authentication rejects passwords shorter than 6 characters. */
    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_NAME_LENGTH = 50;
    public static final int MAX_GROUP_NAME_LENGTH = 50;
    public static final int MAX_DESCRIPTION_LENGTH = 200;
    public static final int MAX_TITLE_LENGTH = 60;
    public static final int MAX_NOTE_LENGTH = 300;

    private static final Pattern EMAIL_LOCAL_PART = Pattern.compile("^[A-Za-z0-9._%+-]+$");
    private static final Pattern USC_ID = Pattern.compile("^\\d{10}$");

    /** Trims and lower-cases an email so lookups and uniqueness checks are case-insensitive. */
    public static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.US);
    }

    /** FR 1.1: name, USC email, 10-digit USC ID and password are all required. */
    public ValidationResult validateRegistration(String name, String email, String uscId,
                                                 String password) {
        ValidationResult result = validateName(name);
        if (!result.isValid()) return result;
        result = validateUscEmail(email);
        if (!result.isValid()) return result;
        result = validateUscId(uscId);
        if (!result.isValid()) return result;
        return validatePassword(password);
    }

    /** Registration check that also confirms the password was typed the same way twice. */
    public ValidationResult validateRegistration(String name, String email, String uscId,
                                                 String password, String confirmPassword) {
        ValidationResult result = validateRegistration(name, email, uscId, password);
        if (!result.isValid()) return result;
        if (confirmPassword == null || !confirmPassword.equals(password)) {
            return ValidationResult.error(ValidationResult.FIELD_CONFIRM_PASSWORD,
                    "Passwords do not match");
        }
        return ValidationResult.ok();
    }

    public ValidationResult validateLogin(String email, String password) {
        if (normalizeEmail(email).isEmpty()) {
            return ValidationResult.error(ValidationResult.FIELD_EMAIL, "Enter your USC email");
        }
        if (password == null || password.isEmpty()) {
            return ValidationResult.error(ValidationResult.FIELD_PASSWORD, "Enter your password");
        }
        return ValidationResult.ok();
    }

    /** FR 1.3: names are required and kept to a reasonable length. */
    public ValidationResult validateName(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return ValidationResult.error(ValidationResult.FIELD_NAME, "Enter your name");
        }
        if (trimmed.length() > MAX_NAME_LENGTH) {
            return ValidationResult.error(ValidationResult.FIELD_NAME,
                    "Name must be at most " + MAX_NAME_LENGTH + " characters");
        }
        return ValidationResult.ok();
    }

    /** FR 1.1: the email address must end with "@usc.edu". */
    public ValidationResult validateUscEmail(String email) {
        String normalized = normalizeEmail(email);
        if (normalized.isEmpty()) {
            return ValidationResult.error(ValidationResult.FIELD_EMAIL, "Enter your USC email");
        }
        if (!normalized.endsWith(USC_EMAIL_DOMAIN)) {
            return ValidationResult.error(ValidationResult.FIELD_EMAIL,
                    "Use a USC email address ending in " + USC_EMAIL_DOMAIN);
        }
        String localPart = normalized.substring(0, normalized.length() - USC_EMAIL_DOMAIN.length());
        if (localPart.isEmpty() || !EMAIL_LOCAL_PART.matcher(localPart).matches()) {
            return ValidationResult.error(ValidationResult.FIELD_EMAIL,
                    "Enter a valid USC email address");
        }
        return ValidationResult.ok();
    }

    /** FR 1.1: the USC ID contains exactly 10 numerical digits. */
    public ValidationResult validateUscId(String uscId) {
        String trimmed = uscId == null ? "" : uscId.trim();
        if (trimmed.isEmpty()) {
            return ValidationResult.error(ValidationResult.FIELD_USC_ID, "Enter your USC ID");
        }
        if (!USC_ID.matcher(trimmed).matches()) {
            return ValidationResult.error(ValidationResult.FIELD_USC_ID,
                    "USC ID must be exactly 10 digits");
        }
        return ValidationResult.ok();
    }

    public ValidationResult validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            return ValidationResult.error(ValidationResult.FIELD_PASSWORD, "Enter a password");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            return ValidationResult.error(ValidationResult.FIELD_PASSWORD,
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }
        return ValidationResult.ok();
    }

    /** FR 1.4 / FR 1.7: a group needs a name; the description is optional. */
    public ValidationResult validateGroup(String name, String description) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return ValidationResult.error(ValidationResult.FIELD_GROUP_NAME, "Enter a group name");
        }
        if (trimmed.length() > MAX_GROUP_NAME_LENGTH) {
            return ValidationResult.error(ValidationResult.FIELD_GROUP_NAME,
                    "Group name must be at most " + MAX_GROUP_NAME_LENGTH + " characters");
        }
        if (description != null && description.trim().length() > MAX_DESCRIPTION_LENGTH) {
            return ValidationResult.error(ValidationResult.FIELD_DESCRIPTION,
                    "Description must be at most " + MAX_DESCRIPTION_LENGTH + " characters");
        }
        return ValidationResult.ok();
    }

    public ValidationResult validateNote(String note) {
        if (note != null && note.trim().length() > MAX_NOTE_LENGTH) {
            return ValidationResult.error(ValidationResult.FIELD_NOTE,
                    "Note must be at most " + MAX_NOTE_LENGTH + " characters");
        }
        return ValidationResult.ok();
    }

    /**
     * Full check of an expense before it is saved: FR 2.2 (fields), FR 2.3 (amount), FR 2.6
     * (payer and participants are members, payer included, at least two participants) and
     * FR 3.6 (shares add up exactly to the total).
     */
    public ValidationResult validateExpense(Expense expense, Group group) {
        if (expense.getTitle().isEmpty()) {
            return ValidationResult.error(ValidationResult.FIELD_TITLE, "Enter a title");
        }
        if (expense.getTitle().length() > MAX_TITLE_LENGTH) {
            return ValidationResult.error(ValidationResult.FIELD_TITLE,
                    "Title must be at most " + MAX_TITLE_LENGTH + " characters");
        }
        if (expense.getTotalCents() <= 0) {
            return ValidationResult.error(ValidationResult.FIELD_AMOUNT,
                    "Amount must be greater than $0.00");
        }
        if (expense.getTotalCents() > Money.MAX_CENTS) {
            return ValidationResult.error(ValidationResult.FIELD_AMOUNT,
                    "Amount cannot be more than " + Money.format(Money.MAX_CENTS));
        }
        if (expense.getPayerId() == null || !group.isMember(expense.getPayerId())) {
            return ValidationResult.error(ValidationResult.FIELD_PAYER,
                    "The payer must be a member of this group");
        }
        Set<String> seen = new HashSet<>();
        for (Share share : expense.getShares()) {
            if (!group.isMember(share.getParticipantId())) {
                return ValidationResult.error(ValidationResult.FIELD_PARTICIPANTS,
                        "Every participant must be a member of this group");
            }
            if (!seen.add(share.getParticipantId())) {
                return ValidationResult.error(ValidationResult.FIELD_PARTICIPANTS,
                        "Each participant can only be selected once");
            }
            if (share.getAmountCents() < 0) {
                return ValidationResult.error(ValidationResult.FIELD_SPLIT,
                        "Shares cannot be negative");
            }
        }
        if (!seen.contains(expense.getPayerId())) {
            return ValidationResult.error(ValidationResult.FIELD_PARTICIPANTS,
                    "The payer must be one of the participants");
        }
        if (seen.size() < 2) {
            return ValidationResult.error(ValidationResult.FIELD_PARTICIPANTS,
                    "Select at least one other participant besides the payer");
        }
        if (expense.getSplitMethod() == SplitMethod.PERCENTAGE) {
            int totalBasisPoints = 0;
            for (Share share : expense.getShares()) {
                totalBasisPoints += share.getPercentBasisPoints();
            }
            if (totalBasisPoints != Money.FULL_PERCENT_BASIS_POINTS) {
                return ValidationResult.error(ValidationResult.FIELD_SPLIT,
                        "Percentages add up to " + Money.formatPercent(totalBasisPoints)
                                + ", not 100%");
            }
        }
        long sum = 0;
        for (Share share : expense.getShares()) {
            sum += share.getAmountCents();
        }
        if (sum != expense.getTotalCents()) {
            return ValidationResult.error(ValidationResult.FIELD_SPLIT,
                    "Amounts add up to " + Money.format(sum) + ", not "
                            + Money.format(expense.getTotalCents()));
        }
        return validateNote(expense.getNote());
    }

    /**
     * FR 5.2: a settlement must be greater than $0.00 and no more than the amount the payer
     * currently owes the receiver.
     */
    public ValidationResult validateSettlement(long amountCents, long owedCents) {
        if (owedCents <= 0) {
            return ValidationResult.error(ValidationResult.FIELD_RECEIVER,
                    "You do not owe this member any money");
        }
        if (amountCents <= 0) {
            return ValidationResult.error(ValidationResult.FIELD_AMOUNT,
                    "Amount must be greater than $0.00");
        }
        if (amountCents > owedCents) {
            return ValidationResult.error(ValidationResult.FIELD_AMOUNT,
                    "Amount cannot be more than what you owe (" + Money.format(owedCents) + ")");
        }
        return ValidationResult.ok();
    }
}
