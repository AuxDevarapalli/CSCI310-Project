package com.team04.splitsmart.logic;

import static com.team04.splitsmart.logic.TestData.ALICE;
import static com.team04.splitsmart.logic.TestData.BOB;
import static com.team04.splitsmart.logic.TestData.CAROL;
import static com.team04.splitsmart.logic.TestData.expense;
import static com.team04.splitsmart.logic.TestData.settlement;
import static com.team04.splitsmart.logic.TestData.share;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.team04.splitsmart.logic.history.HistoryFilter;
import com.team04.splitsmart.logic.history.HistoryQuery;
import com.team04.splitsmart.logic.history.MonthlySummary;
import com.team04.splitsmart.logic.history.SortOrder;
import com.team04.splitsmart.logic.history.SummaryCalculator;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.TransactionType;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Project 2.4 white-box tests. Author: Zachary Chung (zchung@usc.edu).
 * Branch and boundary tests for the history and monthly summary logic behind the History and
 * Summary tabs: the missing-input guards of HistoryFilter and SummaryCalculator, keyword
 * normalization and the "is anything filtered" check in HistoryQuery, the calendar-month
 * boundary of the month filter, and the category defaults of MonthlySummary (FR 5.4, FR 5.6,
 * FR 5.7).
 */
public class ZacharyHistorySummaryTest {
    private final HistoryFilter filter = new HistoryFilter();
    private final SummaryCalculator calculator = new SummaryCalculator();

    // ZC-W01: HistoryFilter.apply takes its two early-exit branches. A missing transaction
    // list returns an empty, non-null list instead of throwing, and a missing query falls
    // back to HistoryQuery.defaults(), which shows everything newest first (FR 5.4).
    @Test
    public void applyHandlesMissingListAndMissingQuery() {
        List<Transaction> nothing = filter.apply(null, HistoryQuery.defaults());
        assertNotNull(nothing);
        assertTrue(nothing.isEmpty());

        Expense groceries = expense("Groceries", ALICE, 2000, "2026-11-01", Category.FOOD,
                share(ALICE, 1000), share(BOB, 1000));
        Settlement venmo = settlement(BOB, ALICE, 500, "2026-11-02", "Venmo");
        Expense gas = expense("Gas", BOB, 800, "2026-11-03", Category.TRANSPORTATION,
                share(BOB, 400), share(ALICE, 400));

        List<Transaction> result = filter.apply(Arrays.asList(venmo, groceries, gas), null);
        assertEquals(Arrays.<Transaction>asList(gas, venmo, groceries), result);
    }

    // ZC-W02: HistoryQuery decides whether the History tab shows "N transactions" or
    // "Showing X of N" and whether Clear filters is visible. A blank or null keyword is
    // stored as "" and does not count as a filter, a padded keyword is trimmed before it is
    // matched, sorting alone is not a filter, a null sort order falls back to newest first,
    // and each of the five filter operands makes isFiltered() true on its own (FR 5.6).
    @Test
    public void keywordIsTrimmedAndSortingAloneIsNotAFilter() {
        HistoryQuery query = HistoryQuery.defaults();
        query.setKeyword("   ");
        assertEquals("", query.getKeyword());
        assertFalse(query.isFiltered());
        query.setKeyword(null);
        assertEquals("", query.getKeyword());

        query.setSortOrder(SortOrder.OLDEST_FIRST);
        assertFalse(query.isFiltered());
        query.setSortOrder(null);
        assertEquals(SortOrder.NEWEST_FIRST, query.getSortOrder());

        Settlement venmo = settlement(BOB, ALICE, 500, "2026-11-02", "Venmo for groceries");
        Expense groceries = expense("Groceries", ALICE, 2000, "2026-11-01", Category.FOOD,
                share(ALICE, 1000), share(BOB, 1000));
        query.setKeyword("  venmo  ");
        assertEquals("venmo", query.getKeyword());
        assertTrue(query.isFiltered());
        assertEquals(Collections.<Transaction>singletonList(venmo),
                filter.apply(Arrays.asList(groceries, venmo), query));

        HistoryQuery byType = HistoryQuery.defaults();
        byType.setTypeFilter(TransactionType.SETTLEMENT);
        assertTrue(byType.isFiltered());
        HistoryQuery byCategory = HistoryQuery.defaults();
        byCategory.setCategoryFilter(Category.FOOD);
        assertTrue(byCategory.isFiltered());
        HistoryQuery byMember = HistoryQuery.defaults();
        byMember.setMemberFilter(CAROL);
        assertTrue(byMember.isFiltered());
        HistoryQuery byMonth = HistoryQuery.defaults();
        byMonth.setMonthFilter("2026-11");
        assertTrue(byMonth.isFiltered());
    }

    // ZC-W03: Month filter at the calendar-month boundary. September 30 and October 1 fall on
    // either side of "2026-10", and October 31 is the last day inside it. A transaction with
    // no recorded date has month "" and is dropped by the month filter, and availableMonths
    // takes its empty-month branch and leaves it out of the month list (FR 5.6).
    @Test
    public void monthFilterUsesTheCalendarMonthBoundary() {
        Expense lastDayOfSeptember = expense("Rent", ALICE, 90000, "2026-09-30",
                Category.HOUSING, share(ALICE, 45000), share(BOB, 45000));
        Expense firstDayOfOctober = expense("Groceries", BOB, 2000, "2026-10-01",
                Category.FOOD, share(BOB, 1000), share(ALICE, 1000));
        Settlement lastDayOfOctober = settlement(ALICE, BOB, 1000, "2026-10-31", "Zelle");
        Expense undated = expense("Undated", ALICE, 1000, "", Category.OTHER,
                share(ALICE, 500), share(BOB, 500));
        List<Transaction> all = Arrays.asList(
                lastDayOfSeptember, firstDayOfOctober, lastDayOfOctober, undated);

        HistoryQuery october = HistoryQuery.defaults();
        october.setMonthFilter("2026-10");
        assertEquals(Arrays.<Transaction>asList(lastDayOfOctober, firstDayOfOctober),
                filter.apply(all, october));

        assertEquals(Arrays.asList("2026-10", "2026-09"), filter.availableMonths(all));
    }

    // ZC-W04: SummaryCalculator's skip branches. A missing list gives an empty summary with
    // all five categories at $0.00 instead of throwing. monthsWithExpenses skips a missing
    // list, a settlement (so a settlement-only month is not offered, unlike the history
    // month list), and an expense with no recorded date (FR 5.7).
    @Test
    public void summaryIgnoresMissingListSettlementsAndUndatedExpenses() {
        MonthlySummary none = calculator.calculate(null, "2026-11");
        assertEquals(0, none.getExpenseCount());
        assertEquals(0, none.getTotalCents());
        assertTrue(none.isEmpty());
        assertEquals(Category.values().length, none.getCategoryTotals().size());
        for (Category category : Category.values()) {
            assertEquals(0, none.getCategoryTotal(category));
        }

        assertTrue(calculator.monthsWithExpenses(null).isEmpty());

        Expense october = expense("Pizza", ALICE, 3000, "2026-10-12", Category.FOOD,
                share(ALICE, 1500), share(BOB, 1500));
        Settlement novemberOnly = settlement(BOB, ALICE, 1500, "2026-11-02", "Venmo");
        Expense undated = expense("Undated", ALICE, 1000, "", Category.OTHER,
                share(ALICE, 500), share(BOB, 500));
        List<Transaction> all = Arrays.asList(october, novemberOnly, undated);

        assertEquals(Collections.singletonList("2026-10"), calculator.monthsWithExpenses(all));
        assertEquals(Arrays.asList("2026-11", "2026-10"), filter.availableMonths(all));
    }

    // ZC-W05: MonthlySummary's constructor fills every category. A null map, a category
    // missing from the map, and a category mapped to null all become 0 cents in the map
    // itself (not only through the null-safe getter), the five categories stay in declared
    // order, and the returned map cannot be modified by the screen that displays it (FR 5.7).
    @Test
    public void monthlySummaryFillsEveryCategoryWithZeroWhenMissing() {
        MonthlySummary fromNull = new MonthlySummary("2026-11", 0, 0, null);
        for (Category category : Category.values()) {
            assertEquals(Long.valueOf(0), fromNull.getCategoryTotals().get(category));
        }

        Map<Category, Long> partial = new EnumMap<>(Category.class);
        partial.put(Category.FOOD, 3000L);
        partial.put(Category.HOUSING, null);
        MonthlySummary summary = new MonthlySummary("2026-11", 1, 3000, partial);
        assertFalse(summary.isEmpty());
        assertEquals(Long.valueOf(3000), summary.getCategoryTotals().get(Category.FOOD));
        assertEquals(Long.valueOf(0), summary.getCategoryTotals().get(Category.HOUSING));
        assertEquals(Long.valueOf(0), summary.getCategoryTotals().get(Category.ENTERTAINMENT));
        assertEquals(Arrays.asList(Category.values()),
                new ArrayList<>(summary.getCategoryTotals().keySet()));

        try {
            summary.getCategoryTotals().put(Category.OTHER, 1L);
            fail("Category totals should be read-only");
        } catch (UnsupportedOperationException expected) {
            assertEquals(0, summary.getCategoryTotal(Category.OTHER));
        }
    }
}
