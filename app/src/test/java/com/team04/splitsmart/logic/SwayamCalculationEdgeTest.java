package com.team04.splitsmart.logic;

import static com.team04.splitsmart.logic.TestData.ALICE;
import static com.team04.splitsmart.logic.TestData.BOB;
import static com.team04.splitsmart.logic.TestData.expense;
import static com.team04.splitsmart.logic.TestData.share;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.team04.splitsmart.logic.balance.BalanceCalculator;
import com.team04.splitsmart.logic.history.HistoryFilter;
import com.team04.splitsmart.logic.split.SplitCalculator;
import com.team04.splitsmart.logic.validation.InputValidator;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Share;
import com.team04.splitsmart.model.SplitMethod;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.logic.history.SortOrder;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Project 2.4 white-box tests. Author: Swayam Parekh (swayampa@usc.edu).
 * Boundary and branch tests for money parsing, percentage splitting, pairwise
 * balance guards, group-field validation and history ordering that the existing
 * unit suite does not exercise (FR 1.4, FR 1.7, FR 3.4, FR 4.2, FR 5.6).
 */
public class SwayamCalculationEdgeTest {
    private final SplitCalculator splitCalculator = new SplitCalculator();
    private final BalanceCalculator balanceCalculator = new BalanceCalculator();
    private final InputValidator validator = new InputValidator();

    // SW-W01: Leading zeros are stripped before the too-many-digits guard, so a
    // long but small amount like 0000000000042.50 is still accepted.
    @Test
    public void parseDollarsStripsLeadingZerosBeforeLengthGuard() throws Exception {
        assertEquals(4250, Money.parseDollars("0000000000042.50"));
        assertEquals(1, Money.parseDollars("000000000000.01"));
    }

    // SW-W02: A missing (null) percentage input is treated as 0% by the split
    // math instead of crashing, and is skipped by the leftover-cent handout.
    @Test
    public void percentSplitTreatsNullPercentageAsZero() {
        LinkedHashMap<String, Integer> percents = new LinkedHashMap<>();
        percents.put(ALICE, null);
        percents.put(BOB, Money.FULL_PERCENT_BASIS_POINTS);
        List<Share> shares = splitCalculator.splitByPercent(1001, percents);
        assertEquals(0, shares.get(0).getAmountCents());
        assertEquals(0, shares.get(0).getPercentBasisPoints());
        assertEquals(1001, shares.get(1).getAmountCents());
    }

    // SW-W03: Comparing a user with themselves is always zero, even when real
    // expenses exist; the uid.equals(otherUid) guard runs before any transaction.
    @Test
    public void netBetweenSameUserIsAlwaysZero() {
        List<Transaction> txs = Collections.singletonList(
                expense(ALICE, share(ALICE, 500), share(BOB, 500)));
        assertEquals(0, balanceCalculator.netBetween(ALICE, ALICE, txs));
        assertEquals(0, balanceCalculator.amountOwed(ALICE, ALICE, txs));
    }

    // SW-W04: Group name and description length boundaries (FR 1.4 / FR 1.7):
    // 50 and 200 characters pass, 51 and 201 fail with the field-specific error.
    @Test
    public void groupNameAndDescriptionLengthBoundaries() {
        String name50 = repeat('a', 50);
        String name51 = repeat('a', 51);
        String description200 = repeat('d', 200);
        String description201 = repeat('d', 201);

        assertTrue(validator.validateGroup(name50, description200).isValid());

        ValidationResult longName = validator.validateGroup(name51, "");
        assertFalse(longName.isValid());
        assertEquals(ValidationResult.FIELD_GROUP_NAME, longName.getField());
        assertEquals("Group name must be at most 50 characters", longName.getErrorMessage());

        ValidationResult longDescription = validator.validateGroup("Roommates", description201);
        assertFalse(longDescription.isValid());
        assertEquals(ValidationResult.FIELD_DESCRIPTION, longDescription.getField());
        assertEquals("Description must be at most 200 characters",
                longDescription.getErrorMessage());
    }

    // SW-W05: History ordering is stable for transactions created in the same
    // millisecond: ties break by date, then by id, and a null id sorts first.
    @Test
    public void historySortBreaksCreatedAtTiesByDateThenId() {
        long sameTime = 5_000L;
        Transaction b2 = expenseWithId("b2", "2026-09-17", sameTime);
        Transaction noId = expenseWithId(null, "2026-09-17", sameTime);
        Transaction a1 = expenseWithId("a1", "2026-09-17", sameTime);
        Transaction older = expenseWithId("c3", "2026-09-16", sameTime);
        List<Transaction> txs = new ArrayList<>(Arrays.asList(b2, noId, a1, older));

        new HistoryFilter().sort(txs, SortOrder.OLDEST_FIRST);
        assertEquals(Arrays.asList(older, noId, a1, b2), txs);

        new HistoryFilter().sort(txs, SortOrder.NEWEST_FIRST);
        assertEquals(Arrays.asList(b2, a1, noId, older), txs);
    }

    private static Expense expenseWithId(String id, String date, long createdAt) {
        return new Expense(id, TestData.GROUP_ID, ALICE, date, createdAt, "", "Expense",
                1000, Category.OTHER, ALICE, SplitMethod.EXACT,
                Arrays.asList(share(ALICE, 500), share(BOB, 500)));
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        Arrays.fill(chars, c);
        return new String(chars);
    }
}
