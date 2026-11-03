package com.team04.splitsmart.logic;

import static com.team04.splitsmart.logic.TestData.ALICE;
import static com.team04.splitsmart.logic.TestData.BOB;
import static com.team04.splitsmart.logic.TestData.CAROL;
import static com.team04.splitsmart.logic.TestData.group;
import static com.team04.splitsmart.logic.TestData.share;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.team04.splitsmart.logic.validation.InputValidator;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.model.Share;
import com.team04.splitsmart.model.SplitMethod;

import org.junit.Test;

import java.util.Arrays;

/** FR 1.1, FR 1.4, FR 2.2 to FR 2.6, FR 3.6 and FR 5.2. */
public class InputValidatorTest {
    private final InputValidator validator = new InputValidator();
    private final Group group = group(ALICE, BOB, CAROL);

    @Test
    public void registration_acceptsValidInput() {
        assertTrue(validator.validateRegistration("Tommy Trojan", "ttrojan@usc.edu",
                "1234567890", "fight0n", "fight0n").isValid());
        assertTrue(validator.validateRegistration("Tommy", " TTrojan@USC.EDU ",
                "1234567890", "secret").isValid());
    }

    @Test
    public void registration_requiresUscEmail() {
        assertField(validator.validateRegistration("T", "ttrojan@gmail.com", "1234567890", "secret"),
                ValidationResult.FIELD_EMAIL);
        assertField(validator.validateRegistration("T", "ttrojan@usc.edu.com", "1234567890", "secret"),
                ValidationResult.FIELD_EMAIL);
        assertField(validator.validateRegistration("T", "@usc.edu", "1234567890", "secret"),
                ValidationResult.FIELD_EMAIL);
        assertField(validator.validateRegistration("T", "a b@usc.edu", "1234567890", "secret"),
                ValidationResult.FIELD_EMAIL);
        assertField(validator.validateRegistration("T", "a@b@usc.edu", "1234567890", "secret"),
                ValidationResult.FIELD_EMAIL);
    }

    @Test
    public void registration_requiresTenDigitUscId() {
        assertField(validator.validateRegistration("T", "t@usc.edu", "123456789", "secret"),
                ValidationResult.FIELD_USC_ID);
        assertField(validator.validateRegistration("T", "t@usc.edu", "12345678901", "secret"),
                ValidationResult.FIELD_USC_ID);
        assertField(validator.validateRegistration("T", "t@usc.edu", "12345abcde", "secret"),
                ValidationResult.FIELD_USC_ID);
        assertField(validator.validateRegistration("T", "t@usc.edu", "", "secret"),
                ValidationResult.FIELD_USC_ID);
    }

    @Test
    public void registration_requiresNameAndPassword() {
        assertField(validator.validateRegistration("  ", "t@usc.edu", "1234567890", "secret"),
                ValidationResult.FIELD_NAME);
        assertField(validator.validateRegistration("T", "t@usc.edu", "1234567890", "12345"),
                ValidationResult.FIELD_PASSWORD);
        assertField(validator.validateRegistration("T", "t@usc.edu", "1234567890", "secret", "secreT"),
                ValidationResult.FIELD_CONFIRM_PASSWORD);
    }

    @Test
    public void normalizeEmail_isCaseInsensitive() {
        assertEquals("ttrojan@usc.edu", InputValidator.normalizeEmail("  TTrojan@USC.edu "));
    }

    @Test
    public void group_requiresName() {
        assertField(validator.validateGroup(" ", "desc"), ValidationResult.FIELD_GROUP_NAME);
        assertTrue(validator.validateGroup("Roommates", "").isValid());
        assertTrue(validator.validateGroup("Roommates", null).isValid());
    }

    @Test
    public void expense_validExpensePasses() {
        assertTrue(validator.validateExpense(expense(3000, ALICE,
                share(ALICE, 1000), share(BOB, 1000), share(CAROL, 1000)), group).isValid());
    }

    @Test
    public void expense_requiresTitleAndPositiveAmount() {
        Expense untitled = new Expense(null, "g", ALICE, "2026-09-17", 1, "", "  ", 1000,
                Category.OTHER, ALICE, SplitMethod.EQUAL,
                Arrays.asList(share(ALICE, 500), share(BOB, 500)));
        assertField(validator.validateExpense(untitled, group), ValidationResult.FIELD_TITLE);
        assertField(validator.validateExpense(expense(0, ALICE, share(ALICE, 0), share(BOB, 0)), group),
                ValidationResult.FIELD_AMOUNT);
    }

    @Test
    public void expense_requiresAnotherParticipantBesidesThePayer() {
        assertField(validator.validateExpense(expense(1000, ALICE, share(ALICE, 1000)), group),
                ValidationResult.FIELD_PARTICIPANTS);
    }

    @Test
    public void expense_requiresPayerAmongParticipants() {
        assertField(validator.validateExpense(expense(1000, ALICE, share(BOB, 500), share(CAROL, 500)),
                group), ValidationResult.FIELD_PARTICIPANTS);
    }

    @Test
    public void expense_requiresMembers() {
        assertField(validator.validateExpense(expense(1000, ALICE, share(ALICE, 500), share("mallory", 500)),
                group), ValidationResult.FIELD_PARTICIPANTS);
        assertField(validator.validateExpense(expense(1000, "mallory", share(ALICE, 500), share(BOB, 500)),
                group), ValidationResult.FIELD_PAYER);
    }

    @Test
    public void expense_requiresSharesToAddUp() {
        ValidationResult result = validator.validateExpense(
                expense(3000, ALICE, share(ALICE, 1400), share(BOB, 1400)), group);
        assertField(result, ValidationResult.FIELD_SPLIT);
        assertEquals("Amounts add up to $28.00, not $30.00", result.getErrorMessage());
    }

    @Test
    public void expense_percentageSplitMustTotalOneHundredPercent() {
        Expense expense = new Expense(null, "g", ALICE, "2026-09-17", 1, "", "Rent", 1000,
                Category.HOUSING, ALICE, SplitMethod.PERCENTAGE,
                Arrays.asList(new Share(ALICE, 500, 5000), new Share(BOB, 500, 4000)));
        assertField(validator.validateExpense(expense, group), ValidationResult.FIELD_SPLIT);
    }

    @Test
    public void settlement_mustBePositiveAndAtMostWhatIsOwed() {
        assertTrue(validator.validateSettlement(1100, 1100).isValid());
        assertTrue(validator.validateSettlement(1, 1100).isValid());
        assertField(validator.validateSettlement(0, 1100), ValidationResult.FIELD_AMOUNT);
        assertField(validator.validateSettlement(1101, 1100), ValidationResult.FIELD_AMOUNT);
        assertField(validator.validateSettlement(500, 0), ValidationResult.FIELD_RECEIVER);
    }

    @Test
    public void settlementRecordedByTheMemberWhoWasPaidHasTheSameLimits() {
        // $5.00 against $11.00 owed is fine and leaves $6.00 (demo checklist example).
        assertTrue(validator.validateSettlement(500, 1100, true).isValid());
        assertField(validator.validateSettlement(0, 1100, true), ValidationResult.FIELD_AMOUNT);
        ValidationResult tooMuch = validator.validateSettlement(1101, 1100, true);
        assertField(tooMuch, ValidationResult.FIELD_AMOUNT);
        assertEquals("Amount cannot be more than what they owe you ($11.00)",
                tooMuch.getErrorMessage());
        assertEquals("This member does not owe you any money",
                validator.validateSettlement(500, 0, true).getErrorMessage());
    }

    private static Expense expense(long total, String payer, Share... shares) {
        return new Expense(null, "g", payer, "2026-09-17", 1, "", "Dinner", total,
                Category.FOOD, payer, SplitMethod.EXACT, Arrays.asList(shares));
    }

    private static void assertField(ValidationResult result, String field) {
        assertFalse("expected invalid", result.isValid());
        assertEquals(result.getErrorMessage(), field, result.getField());
    }
}
