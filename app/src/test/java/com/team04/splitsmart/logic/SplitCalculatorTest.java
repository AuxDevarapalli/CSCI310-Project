package com.team04.splitsmart.logic;

import static com.team04.splitsmart.logic.TestData.ALICE;
import static com.team04.splitsmart.logic.TestData.BOB;
import static com.team04.splitsmart.logic.TestData.CAROL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.team04.splitsmart.logic.split.SplitCalculator;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.Share;

import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/** FR 3.1 to FR 3.6. */
public class SplitCalculatorTest {
    private final SplitCalculator calculator = new SplitCalculator();

    @Test
    public void equalSplit_tenDollarsThreeWays_givesExtraCentToFirst() {
        // Customer example: $10.00 among three users -> $3.34, $3.33, $3.33.
        List<Share> shares = calculator.splitEqual(1000, Arrays.asList(ALICE, BOB, CAROL));
        assertAmounts(shares, 334, 333, 333);
        assertEquals(1000, SplitCalculator.sum(shares));
    }

    @Test
    public void equalSplit_tenOhOneThreeWays_matchesFr35() {
        // FR 3.5: $10.01 split three ways is $3.34, $3.34 and $3.33.
        assertAmounts(calculator.splitEqual(1001, Arrays.asList(ALICE, BOB, CAROL)), 334, 334, 333);
    }

    @Test
    public void equalSplit_evenAmount_hasNoRemainder() {
        // Customer example: Alice pays $30.00 for Alice, Bob and Carol -> $10.00 each.
        assertAmounts(calculator.splitEqual(3000, Arrays.asList(ALICE, BOB, CAROL)), 1000, 1000, 1000);
    }

    @Test
    public void equalSplit_totalLessThanParticipants_allocatesSingleCentsFirst() {
        // $0.02 split 5 ways -> 2 people get $0.01, 3 people get $0.00
        List<Share> shares = calculator.splitEqual(2, Arrays.asList("a", "b", "c", "d", "e"));
        assertAmounts(shares, 1, 1, 0, 0, 0);
        assertEquals(2, SplitCalculator.sum(shares));
    }

    @Test
    public void equalSplit_singleParticipant_getsFullAmount() {
        List<Share> shares = calculator.splitEqual(500, java.util.Collections.singletonList(ALICE));
        assertAmounts(shares, 500);
        assertEquals(500, SplitCalculator.sum(shares));
    }

    @Test
    public void equalSplit_sharesNeverDifferByMoreThanOneCent() {
        for (long total = 1; total <= 2000; total += 7) {
            for (int people = 2; people <= 7; people++) {
                List<String> ids = Arrays.asList("a", "b", "c", "d", "e", "f", "g").subList(0, people);
                List<Share> shares = calculator.splitEqual(total, ids);
                long min = Long.MAX_VALUE;
                long max = Long.MIN_VALUE;
                for (Share share : shares) {
                    min = Math.min(min, share.getAmountCents());
                    max = Math.max(max, share.getAmountCents());
                }
                assertTrue(max - min <= 1);
                assertEquals(total, SplitCalculator.sum(shares));
            }
        }
    }

    @Test
    public void percentSplit_fiftyFiftyOfTenOhOne_matchesFr35() {
        // FR 3.5: $10.01 split 50/50 is $5.01 and $5.00.
        LinkedHashMap<String, Integer> percents = new LinkedHashMap<>();
        percents.put(ALICE, 5000);
        percents.put(BOB, 5000);
        List<Share> shares = calculator.splitByPercent(1001, percents);
        assertAmounts(shares, 501, 500);
        assertEquals(5000, shares.get(0).getPercentBasisPoints());
    }

    @Test
    public void percentSplit_leftoverCentSkipsZeroPercentParticipants() {
        LinkedHashMap<String, Integer> percents = new LinkedHashMap<>();
        percents.put(ALICE, 0);
        percents.put(BOB, 3333);
        percents.put(CAROL, 6667);
        List<Share> shares = calculator.splitByPercent(100, percents);
        assertAmounts(shares, 0, 34, 66);
        assertEquals(100, SplitCalculator.sum(shares));
    }

    @Test
    public void percentSplit_alwaysAddsUpWhenPercentagesAreValid() {
        LinkedHashMap<String, Integer> percents = new LinkedHashMap<>();
        percents.put(ALICE, 3333);
        percents.put(BOB, 3333);
        percents.put(CAROL, 3334);
        for (long total = 1; total <= 5000; total += 13) {
            assertEquals(total, SplitCalculator.sum(calculator.splitByPercent(total, percents)));
        }
    }

    @Test
    public void percentSplit_invalidPercentagesAreNotPatchedUp() {
        LinkedHashMap<String, Integer> percents = new LinkedHashMap<>();
        percents.put(ALICE, 5000);
        percents.put(BOB, 4000);
        List<Share> shares = calculator.splitByPercent(1000, percents);
        assertAmounts(shares, 500, 400);
        assertFalse(calculator.checkSum(1000, shares).isValid());
    }

    @Test
    public void checkPercentages_requiresExactlyOneHundred() {
        LinkedHashMap<String, Integer> percents = new LinkedHashMap<>();
        percents.put(ALICE, 5000);
        percents.put(BOB, 4000);
        ValidationResult result = calculator.checkPercentages(percents);
        assertFalse(result.isValid());
        assertEquals("Percentages add up to 90%, not 100%", result.getErrorMessage());

        percents.put(BOB, 5000);
        assertTrue(calculator.checkPercentages(percents).isValid());
    }

    @Test
    public void checkPercentages_rejectsNegativeValues() {
        LinkedHashMap<String, Integer> percents = new LinkedHashMap<>();
        percents.put(ALICE, 11000);
        percents.put(BOB, -1000);
        assertFalse(calculator.checkPercentages(percents).isValid());
    }

    @Test
    public void exactSplit_usesTypedAmounts() {
        LinkedHashMap<String, Long> amounts = new LinkedHashMap<>();
        amounts.put(ALICE, 1200L);
        amounts.put(BOB, 1800L);
        List<Share> shares = calculator.splitExact(amounts);
        assertAmounts(shares, 1200, 1800);
        assertTrue(calculator.checkSum(3000, shares).isValid());
    }

    @Test
    public void checkSum_explainsTheDifference() {
        // 2.1 use case: "Amounts sum up to $28.00, not $30.00".
        LinkedHashMap<String, Long> amounts = new LinkedHashMap<>();
        amounts.put(ALICE, 1400L);
        amounts.put(BOB, 1400L);
        ValidationResult result = calculator.checkSum(3000, calculator.splitExact(amounts));
        assertFalse(result.isValid());
        assertEquals("Amounts add up to $28.00, not $30.00", result.getErrorMessage());
        assertEquals(ValidationResult.FIELD_SPLIT, result.getField());
    }

    @Test(expected = IllegalArgumentException.class)
    public void negativeTotalsAreRejected() {
        calculator.splitEqual(-1, Arrays.asList(ALICE, BOB));
    }

    private static void assertAmounts(List<Share> shares, long... expected) {
        assertEquals(expected.length, shares.size());
        for (int i = 0; i < expected.length; i++) {
            assertEquals("share " + i, expected[i], shares.get(i).getAmountCents());
        }
    }
}
