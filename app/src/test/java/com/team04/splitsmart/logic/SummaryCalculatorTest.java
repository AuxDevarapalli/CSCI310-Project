package com.team04.splitsmart.logic;

import static com.team04.splitsmart.logic.TestData.ALICE;
import static com.team04.splitsmart.logic.TestData.BOB;
import static com.team04.splitsmart.logic.TestData.expense;
import static com.team04.splitsmart.logic.TestData.settlement;
import static com.team04.splitsmart.logic.TestData.share;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.team04.splitsmart.logic.history.MonthlySummary;
import com.team04.splitsmart.logic.history.SummaryCalculator;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Transaction;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

/** FR 5.7. */
public class SummaryCalculatorTest {
    private final SummaryCalculator calculator = new SummaryCalculator();

    private final List<Transaction> txs = Arrays.asList(
            expense("Groceries", ALICE, 3000, "2026-09-01", Category.FOOD,
                    share(ALICE, 1500), share(BOB, 1500)),
            expense("Pizza", BOB, 1999, "2026-09-30", Category.FOOD,
                    share(ALICE, 1000), share(BOB, 999)),
            expense("Rent", ALICE, 150000, "2026-09-15", Category.HOUSING,
                    share(ALICE, 75000), share(BOB, 75000)),
            expense("Concert", BOB, 8000, "2026-10-01", Category.ENTERTAINMENT,
                    share(ALICE, 4000), share(BOB, 4000)),
            settlement(BOB, ALICE, 500, "2026-09-20", "partial"));

    @Test
    public void countsAndTotalsOnlyThatMonthsExpenses() {
        MonthlySummary summary = calculator.calculate(txs, "2026-09");
        assertEquals(3, summary.getExpenseCount());
        assertEquals(154999, summary.getTotalCents());
        assertEquals(4999, summary.getCategoryTotal(Category.FOOD));
        assertEquals(150000, summary.getCategoryTotal(Category.HOUSING));
        assertEquals(0, summary.getCategoryTotal(Category.ENTERTAINMENT));
    }

    @Test
    public void categoryTotalsAddUpToTheTotal() {
        MonthlySummary summary = calculator.calculate(txs, "2026-09");
        long sum = 0;
        for (long value : summary.getCategoryTotals().values()) {
            sum += value;
        }
        assertEquals(summary.getTotalCents(), sum);
        assertEquals(Category.values().length, summary.getCategoryTotals().size());
    }

    @Test
    public void settlementsAreNotIncluded() {
        MonthlySummary summary = calculator.calculate(
                Arrays.<Transaction>asList(settlement(BOB, ALICE, 500, "2026-11-02", "")), "2026-11");
        assertTrue(summary.isEmpty());
        assertEquals(0, summary.getTotalCents());
    }

    @Test
    public void emptyMonthShowsZeros() {
        MonthlySummary summary = calculator.calculate(txs, "2025-01");
        assertEquals(0, summary.getExpenseCount());
        assertEquals(0, summary.getTotalCents());
        assertEquals(0, summary.getCategoryTotal(Category.OTHER));
    }

    @Test
    public void listsMonthsWithExpensesNewestFirst() {
        assertEquals(Arrays.asList("2026-10", "2026-09"), calculator.monthsWithExpenses(txs));
    }
}
