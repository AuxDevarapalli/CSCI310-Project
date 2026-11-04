package com.team04.splitsmart.logic;

import static com.team04.splitsmart.logic.TestData.ALICE;
import static com.team04.splitsmart.logic.TestData.BOB;
import static com.team04.splitsmart.logic.TestData.CAROL;
import static com.team04.splitsmart.logic.TestData.DAVID;
import static com.team04.splitsmart.logic.TestData.expense;
import static com.team04.splitsmart.logic.TestData.settlement;
import static com.team04.splitsmart.logic.TestData.share;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.team04.splitsmart.logic.history.HistoryFilter;
import com.team04.splitsmart.logic.history.HistoryQuery;
import com.team04.splitsmart.logic.history.SortOrder;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.TransactionType;

import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** FR 5.4 and FR 5.6. */
public class HistoryFilterTest {
    private final HistoryFilter filter = new HistoryFilter();
    private Expense groceries;
    private Expense uber;
    private Settlement payBack;
    private Expense movie;
    private List<Transaction> all;

    @Before
    public void setUp() {
        // Created in this order, so createdAt increases.
        groceries = expense("Trader Joe's groceries", ALICE, 3000, "2026-09-01", Category.FOOD,
                share(ALICE, 1000), share(BOB, 1000), share(CAROL, 1000));
        uber = expense("Uber to LAX", BOB, 4000, "2026-09-05", Category.TRANSPORTATION,
                share(BOB, 2000), share(CAROL, 2000));
        payBack = settlement(BOB, ALICE, 1000, "2026-09-06", "Venmo for groceries");
        movie = expense("Movie night", DAVID, 2400, "2026-10-02", Category.ENTERTAINMENT,
                share(DAVID, 1200), share(ALICE, 1200));
        all = Arrays.asList(uber, groceries, movie, payBack);
    }

    @Test
    public void defaultQueryShowsEverythingNewestFirst() {
        List<Transaction> result = filter.apply(all, HistoryQuery.defaults());
        assertEquals(Arrays.asList(movie, payBack, uber, groceries), result);
    }

    @Test
    public void canSortOldestFirst() {
        HistoryQuery query = HistoryQuery.defaults();
        query.setSortOrder(SortOrder.OLDEST_FIRST);
        assertEquals(Arrays.asList(groceries, uber, payBack, movie), filter.apply(all, query));
    }

    @Test
    public void keywordMatchesTitlesIgnoringCase() {
        HistoryQuery query = HistoryQuery.defaults();
        query.setKeyword("uber");
        assertEquals(Arrays.<Transaction>asList(uber), filter.apply(all, query));
    }

    @Test
    public void keywordMatchesNotesOfAnyTransaction() {
        HistoryQuery query = HistoryQuery.defaults();
        query.setKeyword("VENMO");
        assertEquals(Arrays.<Transaction>asList(payBack), filter.apply(all, query));

        Expense withNote = TestData.expenseWithNote("Dinner", "paid with venmo card", 9_000_000);
        List<Transaction> both = filter.apply(Arrays.asList(payBack, withNote), query);
        assertEquals(2, both.size());
    }

    @Test
    public void matchCaseOnlyFindsTheExactCapitalization() {
        HistoryQuery query = HistoryQuery.defaults();
        query.setMatchCase(true);
        query.setKeyword("Uber");
        assertEquals(Arrays.<Transaction>asList(uber), filter.apply(all, query));
        query.setKeyword("uber");
        assertTrue(filter.apply(all, query).isEmpty());
        assertTrue(query.copy().isMatchCase());
    }

    @Test
    public void matchCaseAppliesToExpenseAndSettlementNotes() {
        Expense withNote = TestData.expenseWithNote("Dinner", "paid with Venmo card", 9_000_000);
        List<Transaction> notes = Arrays.asList(payBack, withNote);
        HistoryQuery query = HistoryQuery.defaults();
        query.setMatchCase(true);
        query.setKeyword("Venmo");
        assertEquals(2, filter.apply(notes, query).size());
        query.setKeyword("venmo");
        assertTrue(filter.apply(notes, query).isEmpty());
    }

    @Test
    public void canFilterByType() {
        HistoryQuery query = HistoryQuery.defaults();
        query.setTypeFilter(TransactionType.SETTLEMENT);
        assertEquals(Arrays.<Transaction>asList(payBack), filter.apply(all, query));

        query.setTypeFilter(TransactionType.EXPENSE);
        assertEquals(3, filter.apply(all, query).size());
    }

    @Test
    public void categoryFilterOnlyKeepsExpensesOfThatCategory() {
        HistoryQuery query = HistoryQuery.defaults();
        query.setCategoryFilter(Category.FOOD);
        assertEquals(Arrays.<Transaction>asList(groceries), filter.apply(all, query));
    }

    @Test
    public void memberFilterKeepsTransactionsInvolvingTheMember() {
        HistoryQuery query = HistoryQuery.defaults();
        query.setMemberFilter(CAROL);
        assertEquals(Arrays.<Transaction>asList(uber, groceries), filter.apply(all, query));

        query.setMemberFilter(ALICE);
        assertEquals(Arrays.<Transaction>asList(movie, payBack, groceries), filter.apply(all, query));
    }

    @Test
    public void filtersCombine() {
        HistoryQuery query = HistoryQuery.defaults();
        query.setMemberFilter(ALICE);
        query.setTypeFilter(TransactionType.EXPENSE);
        query.setKeyword("night");
        assertEquals(Arrays.<Transaction>asList(movie), filter.apply(all, query));
        assertTrue(query.isFiltered());
    }

    @Test
    public void monthFilterIncludesBothTransactionTypesAndCombinesWithOtherFilters() {
        HistoryQuery query = HistoryQuery.defaults();
        query.setMonthFilter("2026-09");
        assertEquals(Arrays.asList(payBack, uber, groceries), filter.apply(all, query));
        assertTrue(query.isFiltered());

        query.setMemberFilter(ALICE);
        query.setSortOrder(SortOrder.OLDEST_FIRST);
        assertEquals(Arrays.asList(groceries, payBack), filter.apply(all, query));

        query.setTypeFilter(TransactionType.SETTLEMENT);
        assertEquals(Arrays.asList(payBack), filter.apply(all, query));
    }

    @Test
    public void copyingAndClearingMonthFilterPreservesOriginal() {
        HistoryQuery original = HistoryQuery.defaults();
        original.setMonthFilter("2026-10");
        HistoryQuery copy = original.copy();
        assertEquals(Arrays.asList(movie), filter.apply(all, copy));

        copy.setMonthFilter(null);

        assertFalse(copy.isFiltered());
        assertEquals(4, filter.apply(all, copy).size());
        assertEquals(Arrays.asList(movie), filter.apply(all, original));
    }

    @Test
    public void availableMonthsAreDistinctAndIncludeSettlementOnlyMonths() {
        Settlement december = settlement(BOB, ALICE, 100, "2026-12-01", "");
        assertEquals(Arrays.asList("2026-12", "2026-10", "2026-09"),
                filter.availableMonths(Arrays.asList(december, groceries, uber, movie, payBack)));
        assertTrue(filter.availableMonths(Collections.emptyList()).isEmpty());
    }

    @Test
    public void noMatchesGivesEmptyList() {
        HistoryQuery query = HistoryQuery.defaults();
        query.setKeyword("nothing matches this");
        assertTrue(filter.apply(all, query).isEmpty());
        assertFalse(HistoryQuery.defaults().isFiltered());
    }
}
