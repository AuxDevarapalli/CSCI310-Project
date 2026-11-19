package com.team04.splitsmart.logic;

import static com.team04.splitsmart.logic.TestData.ALICE;
import static com.team04.splitsmart.logic.TestData.BOB;
import static com.team04.splitsmart.logic.TestData.CAROL;
import static com.team04.splitsmart.logic.TestData.DAVID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.team04.splitsmart.logic.split.SplitCalculator;
import com.team04.splitsmart.logic.validation.InvalidAmountException;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.Share;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Project 2.4 white-box tests. Author: Aux Devarapalli (adevarap@usc.edu).
 * Branch and boundary tests for the split engine behind the Add Expense form: the
 * percentage check, the leftover-cent loop, the missing-input guards of every split
 * method, and percentage input parsing (FR 3.2 to FR 3.6).
 */
public class AuxExpenseSplitTest {
    private final SplitCalculator calculator = new SplitCalculator();

    // AD-W01: Edward's D2 input (two Integer.MAX_VALUE percentages plus 10,002 basis points)
    // against checkPercentages, the percentage check the expense form's preview uses. An int
    // total wraps 4,294,977,296 basis points around to exactly 10,000 (100%).
    @Test
    public void percentageCheckRejectsTotalThatOverflowsAnInt() {
        LinkedHashMap<String, Integer> percents = new LinkedHashMap<>();
        percents.put(ALICE, Integer.MAX_VALUE);
        percents.put(BOB, Integer.MAX_VALUE);
        percents.put(CAROL, 10002);
        assertSplitError(calculator.checkPercentages(percents),
                "Percentages add up to 42949772.96%, not 100%");
    }

    // AD-W02: The three missing-input guards of checkPercentages: no map, an empty map, and
    // a participant whose percentage was never entered.
    @Test
    public void percentageCheckRequiresAValueForEveryParticipant() {
        String message = "Enter a percentage for every participant";
        assertSplitError(calculator.checkPercentages(null), message);
        assertSplitError(calculator.checkPercentages(new LinkedHashMap<>()), message);
        LinkedHashMap<String, Integer> missing = new LinkedHashMap<>();
        missing.put(ALICE, 5000);
        missing.put(BOB, null);
        assertSplitError(calculator.checkPercentages(missing), message);
    }

    // AD-W03: Two leftover cents are handed out first to last, and the loop skips a 0%
    // participant in the middle (FR 3.5): $1.01 at 33.33% / 0% / 33.33% / 33.34%.
    @Test
    public void percentSplitHandsOutTwoLeftoverCentsSkippingZeroPercent() {
        LinkedHashMap<String, Integer> percents = new LinkedHashMap<>();
        percents.put(ALICE, 3333);
        percents.put(BOB, 0);
        percents.put(CAROL, 3333);
        percents.put(DAVID, 3334);
        List<Share> shares = calculator.splitByPercent(101, percents);
        assertAmounts(shares, 34, 0, 34, 33);
        assertEquals(101, SplitCalculator.sum(shares));
        assertEquals(0, shares.get(1).getPercentBasisPoints());
        assertEquals(3334, shares.get(3).getPercentBasisPoints());
    }

    // AD-W04: The empty-input guard of every split method: no participants, no
    // percentages, no exact amounts, a missing exact amount, and no shares to add up.
    @Test
    public void splitMethodsHandleMissingInput() {
        assertTrue(calculator.splitEqual(1000, null).isEmpty());
        assertTrue(calculator.splitEqual(1000, Collections.emptyList()).isEmpty());
        assertTrue(calculator.splitByPercent(1000, null).isEmpty());
        assertTrue(calculator.splitByPercent(1000, new LinkedHashMap<>()).isEmpty());
        assertTrue(calculator.splitExact(null).isEmpty());
        LinkedHashMap<String, Long> amounts = new LinkedHashMap<>();
        amounts.put(ALICE, 1000L);
        amounts.put(BOB, null);
        assertAmounts(calculator.splitExact(amounts), 1000, 0);
        assertEquals(0, SplitCalculator.sum(null));
        assertSplitError(calculator.checkSum(1000, null), "Amounts add up to $0.00, not $10.00");
    }

    // AD-W05: Percentage input boundaries for a percentage split (FR 3.4): the smallest
    // positive value, a value with no whole part, exactly 100%, one hundredth above 100%,
    // and an entry that is blank once the % sign and spaces are removed.
    @Test
    public void percentageInputBoundaries() throws Exception {
        assertEquals(1, Money.parsePercent("0.01"));
        assertEquals(50, Money.parsePercent(".5"));
        assertEquals(10000, Money.parsePercent("100.00"));
        assertRejected("100.01", "Percentage cannot be more than 100%");
        assertRejected("  %  ", "Enter a percentage");
    }

    private static void assertSplitError(ValidationResult result, String message) {
        assertFalse(result.isValid());
        assertEquals(ValidationResult.FIELD_SPLIT, result.getField());
        assertEquals(message, result.getErrorMessage());
    }

    private static void assertAmounts(List<Share> shares, long... expected) {
        assertEquals(expected.length, shares.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("share " + i, expected[i], shares.get(i).getAmountCents());
        }
    }

    private static void assertRejected(String input, String message) {
        try {
            Money.parsePercent(input);
            fail("Expected '" + input + "' to be rejected");
        } catch (InvalidAmountException e) {
            assertEquals(message, e.getMessage());
        }
    }
}
