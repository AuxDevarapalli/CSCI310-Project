package com.team04.splitsmart.logic;

import static com.team04.splitsmart.logic.TestData.ALICE;
import static com.team04.splitsmart.logic.TestData.BOB;
import static com.team04.splitsmart.logic.TestData.CAROL;
import static com.team04.splitsmart.logic.TestData.expense;
import static com.team04.splitsmart.logic.TestData.settlement;
import static com.team04.splitsmart.logic.TestData.share;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.team04.splitsmart.logic.history.SpendingInsights;
import com.team04.splitsmart.logic.history.SummaryCalculator;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Transaction;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SpendingInsightsTest {

    @Test
    public void trendIncludesEmptyMonthsAndSpansTheYearBoundary() {
        List<Transaction> data = Arrays.asList(
                expense("Outside range", ALICE, 10000, "2026-07-31", Category.OTHER),
                expense("First day", ALICE, 700, "2026-08-01", Category.FOOD),
                expense("New year", BOB, 3200, "2027-01-01", Category.HOUSING),
                expense("After selected month", BOB, 900, "2027-02-01", Category.FOOD));

        SpendingInsights report = SpendingInsights.calculate(data, "2027-01", ALICE);

        assertEquals(Arrays.asList("2026-08", "2026-09", "2026-10", "2026-11", "2026-12",
                "2027-01"), new ArrayList<>(report.getTrend().keySet()));
        assertEquals(Arrays.asList(700L, 0L, 0L, 0L, 0L, 3200L),
                new ArrayList<>(report.getTrend().values()));
    }

    @Test
    public void trendAndCategoryTotalsAgreeWithRequiredSummary() {
        Expense food = expense("Dinner", ALICE, 3000, "2026-11-01", Category.FOOD);
        Expense bus = expense("Bus", BOB, 2000, "2026-11-30", Category.TRANSPORTATION);
        List<Transaction> data = Arrays.asList(food, bus,
                settlement(BOB, ALICE, 500, "2026-11-15", "Venmo"));

        SpendingInsights report = SpendingInsights.calculate(data, "2026-11", ALICE);
        long summaryTotal = new SummaryCalculator().calculate(data, "2026-11").getTotalCents();

        assertEquals(summaryTotal, report.getTrend().get("2026-11").longValue());
        assertEquals(Arrays.asList(food, bus), report.getExpenses(null));
        assertEquals(Collections.singletonList(food), report.getExpenses(Category.FOOD));
        assertEquals(Collections.singletonList(bus), report.getExpenses(Category.TRANSPORTATION));
        assertTrue(report.getExpenses(Category.ENTERTAINMENT).isEmpty());
    }

    @Test
    public void personalFiguresDistinguishPaymentsFromAssignedShares() {
        Expense paidByAlice = expense("Food", ALICE, 3000, "2026-11-05", Category.FOOD,
                share(ALICE, 1000), share(BOB, 2000));
        Expense paidByBob = expense("Taxi", BOB, 2000, "2026-11-08", Category.TRANSPORTATION,
                share(ALICE, 750), share(BOB, 1250));
        Expense others = expense("Concert", BOB, 4200, "2026-11-02", Category.ENTERTAINMENT,
                share(BOB, 2100), share(CAROL, 2100));
        Expense otherMonth = expense("Past", ALICE, 1000, "2026-10-12", Category.OTHER,
                share(ALICE, 500), share(BOB, 500));
        List<Transaction> data = Arrays.asList(paidByAlice, paidByBob, others, otherMonth,
                settlement(BOB, ALICE, 1000, "2026-11-10", ""));

        SpendingInsights alice = SpendingInsights.calculate(data, "2026-11", ALICE);
        SpendingInsights bob = SpendingInsights.calculate(data, "2026-11", BOB);
        SpendingInsights carol = SpendingInsights.calculate(data, "2026-11", CAROL);

        assertEquals(3000, alice.getPaidCents());
        assertEquals(1750, alice.getShareCents());
        assertEquals(6200, bob.getPaidCents());
        assertEquals(5350, bob.getShareCents());
        assertEquals(0, carol.getPaidCents());
        assertEquals(2100, carol.getShareCents());
    }

    @Test
    public void rankOrdersByAmountAndBreaksTiesByCreationTime() {
        Expense older = expense("A", ALICE, 3000, "2026-11-01", Category.OTHER);
        Expense newer = expense("B", ALICE, 3000, "2026-11-02", Category.OTHER);
        Expense small = expense("C", ALICE, 2999, "2026-11-03", Category.OTHER);

        SpendingInsights report = SpendingInsights.calculate(
                Arrays.asList(small, older, newer), "2026-11", ALICE);

        assertEquals(Arrays.asList(newer, older, small), report.getExpenses(null));
    }

    @Test
    public void movingMonthsRecomputesChangeAndDrillDown() {
        Expense october = expense("October", ALICE, 2500, "2026-10-30", Category.FOOD);
        Expense november = expense("November", ALICE, 4100, "2026-11-01", Category.FOOD);
        List<Transaction> data = Arrays.asList(october, november);

        SpendingInsights novemberReport = SpendingInsights.calculate(data, "2026-11", ALICE);
        SpendingInsights decemberReport = SpendingInsights.calculate(data, "2026-12", ALICE);

        assertEquals(1600, novemberReport.getChangeFromPreviousMonthCents());
        assertEquals(Collections.singletonList(november), novemberReport.getExpenses(Category.FOOD));
        assertEquals(-4100, decemberReport.getChangeFromPreviousMonthCents());
        assertTrue(decemberReport.getExpenses(null).isEmpty());
    }

    @Test
    public void selectingFirstBarKeepsWindowAndComparesAgainstMonthOutsideIt() {
        List<Transaction> data = Arrays.asList(
                expense("Before chart", ALICE, 1700, "2026-05-10", Category.FOOD),
                expense("First bar", ALICE, 2400, "2026-06-01", Category.FOOD),
                expense("Last bar", ALICE, 5000, "2026-11-01", Category.FOOD));
        SpendingInsights latest = SpendingInsights.calculate(data, "2026-11", ALICE);
        SpendingInsights selected = SpendingInsights.calculate(data, "2026-06", ALICE, "2026-11");

        assertEquals(latest.getTrend(), selected.getTrend());
        assertEquals("2026-06", selected.getMonth());
        assertEquals(700, selected.getChangeFromPreviousMonthCents());
        assertEquals("First bar", selected.getExpenses(null).get(0).getTitle());
    }

    @Test
    public void deletingLastExpenseClearsInsights() {
        Expense expense = expense("Last expense", ALICE, 2500, "2026-11-01", Category.FOOD,
                share(ALICE, 2000), share(BOB, 500));
        List<Transaction> data = new ArrayList<>();
        data.add(expense);
        data.add(settlement(BOB, ALICE, 100, "2026-11-02", ""));
        SpendingInsights before = SpendingInsights.calculate(data, "2026-11", ALICE);

        data.remove(expense);
        SpendingInsights after = SpendingInsights.calculate(data, "2026-11", ALICE);

        assertEquals(2500, before.getPaidCents());
        assertEquals(2000, before.getShareCents());
        assertEquals(0, after.getPaidCents());
        assertEquals(0, after.getShareCents());
        assertEquals(0, after.getChangeFromPreviousMonthCents());
        assertTrue(after.getExpenses(null).isEmpty());
        assertEquals(Collections.nCopies(6, 0L), new ArrayList<>(after.getTrend().values()));
    }
}
