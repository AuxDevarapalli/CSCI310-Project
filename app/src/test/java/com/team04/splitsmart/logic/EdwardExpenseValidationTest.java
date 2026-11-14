package com.team04.splitsmart.logic;

import static com.team04.splitsmart.logic.TestData.*;
import static org.junit.Assert.*;

import com.team04.splitsmart.logic.validation.InputValidator;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.*;
import java.util.Arrays;
import org.junit.Test;

/** Project 2.4 white-box tests. Author: Edward Zhao (ebzhao@usc.edu). */
public class EdwardExpenseValidationTest {
    private final InputValidator validator = new InputValidator();
    private final Group members = group(ALICE, BOB);

    // EZ-W01: Reach the title-length guard with otherwise valid data.
    @Test public void titleLengthBoundary() {
        assertTrue(validate("T".repeat(60), 1000, "", SplitMethod.EXACT,
                share(ALICE, 500), share(BOB, 500)).isValid());
        assertError(validate("T".repeat(61), 1000, "", SplitMethod.EXACT,
                share(ALICE, 500), share(BOB, 500)), ValidationResult.FIELD_TITLE,
                "Title must be at most 60 characters");
    }

    // EZ-W02: Reach the maximum-amount guard, including its inclusive boundary.
    @Test public void amountMaximumBoundary() {
        assertTrue(validate("Dinner", Money.MAX_CENTS, "", SplitMethod.EXACT,
                share(ALICE, Money.MAX_CENTS - 1), share(BOB, 1)).isValid());
        assertError(validate("Dinner", Money.MAX_CENTS + 1, "", SplitMethod.EXACT,
                share(ALICE, Money.MAX_CENTS), share(BOB, 1)), ValidationResult.FIELD_AMOUNT,
                "Amount cannot be more than " + Money.format(Money.MAX_CENTS));
    }

    // EZ-W03: The second Alice takes the duplicate-participant branch of seen.add.
    @Test public void duplicateParticipantRejectedEvenWhenTotalMatches() {
        assertError(validate("Dinner", 1000, "", SplitMethod.EXACT,
                share(ALICE, 250), share(ALICE, 250), share(BOB, 500)),
                ValidationResult.FIELD_PARTICIPANTS, "Each participant can only be selected once");
    }

    // EZ-W04: Membership succeeds before the negative-share guard is evaluated.
    @Test public void negativeShareRejectedEvenWhenTotalMatches() {
        assertError(validate("Dinner", 1000, "", SplitMethod.EXACT,
                share(ALICE, -1), share(BOB, 1001)), ValidationResult.FIELD_SPLIT,
                "Shares cannot be negative");
    }

    // EZ-W05: Reach the final note validation after every expense guard succeeds.
    @Test public void noteLengthBoundary() {
        assertTrue(validate("Dinner", 1000, "N".repeat(300), SplitMethod.EXACT,
                share(ALICE, 500), share(BOB, 500)).isValid());
        assertError(validate("Dinner", 1000, "N".repeat(301), SplitMethod.EXACT,
                share(ALICE, 500), share(BOB, 500)), ValidationResult.FIELD_NOTE,
                "Note must be at most 300 characters");
    }

    private ValidationResult validate(String title, long total, String note,
                                      SplitMethod method, Share... shares) {
        Expense expense = new Expense("ez-expense", GROUP_ID, ALICE, "2026-11-13", 1,
                note, title, total, Category.FOOD, ALICE, method, Arrays.asList(shares));
        return validator.validateExpense(expense, members);
    }

    private static void assertError(ValidationResult result, String field, String message) {
        assertFalse(result.isValid());
        assertEquals(field, result.getField());
        assertEquals(message, result.getErrorMessage());
    }
}
