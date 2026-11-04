package com.team04.splitsmart.logic.history;

import com.team04.splitsmart.logic.validation.DateProvider;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Expense-only insights derived from the same snapshot as the monthly summary. */
public final class SpendingInsights {
    private static final int TREND_MONTHS = 6;
    private static final Comparator<Expense> BIGGEST_FIRST = Comparator
            .comparingLong(Expense::getTotalCents)
            .thenComparingLong(Expense::getCreatedAt)
            .reversed();

    private final String month;
    private final Map<String, Long> trend;
    private final List<Expense> expenses;
    private final long paidCents;
    private final long shareCents;
    private final long changeCents;

    private SpendingInsights(String month, Map<String, Long> trend, List<Expense> expenses,
                             long paidCents, long shareCents, long changeCents) {
        this.month = month;
        this.trend = Collections.unmodifiableMap(trend);
        this.expenses = Collections.unmodifiableList(expenses);
        this.paidCents = paidCents;
        this.shareCents = shareCents;
        this.changeCents = changeCents;
    }

    /** Six calendar months ending at the selected month, including months with no expenses. */
    public static SpendingInsights calculate(List<? extends Transaction> transactions,
                                             String selectedMonth, String uid) {
        return calculate(transactions, selectedMonth, uid, selectedMonth);
    }

    /** Keeps the chart window stable while browsing one of its months. */
    public static SpendingInsights calculate(List<? extends Transaction> transactions,
                                             String selectedMonth, String uid, String trendEnd) {
        Map<String, Long> trend = new LinkedHashMap<>();
        for (int offset = 1 - TREND_MONTHS; offset <= 0; offset++) {
            trend.put(DateProvider.shiftMonth(trendEnd, offset), 0L);
        }

        List<Expense> currentExpenses = new ArrayList<>();
        String previousMonth = DateProvider.shiftMonth(selectedMonth, -1);
        long previousTotal = 0;
        long selectedTotal = 0;
        long paid = 0;
        long share = 0;
        for (Transaction transaction : transactions) {
            if (!(transaction instanceof Expense)) {
                continue;
            }
            Expense expense = (Expense) transaction;
            String month = DateProvider.monthOf(expense.getDate());
            Long runningTotal = trend.get(month);
            if (runningTotal != null) {
                trend.put(month, runningTotal + expense.getTotalCents());
            }
            if (previousMonth.equals(month)) {
                previousTotal += expense.getTotalCents();
            }
            if (selectedMonth.equals(month)) {
                selectedTotal += expense.getTotalCents();
                currentExpenses.add(expense);
                if (uid != null && uid.equals(expense.getPayerId())) {
                    paid += expense.getTotalCents();
                }
                share += expense.getShareFor(uid);
            }
        }
        currentExpenses.sort(BIGGEST_FIRST);
        return new SpendingInsights(selectedMonth, trend, currentExpenses, paid, share,
                selectedTotal - previousTotal);
    }

    public String getMonth() {
        return month;
    }

    public Map<String, Long> getTrend() {
        return trend;
    }

    public long getChangeFromPreviousMonthCents() {
        return changeCents;
    }

    /** Group expense amounts the current user paid up front during this month. */
    public long getPaidCents() {
        return paidCents;
    }

    /** Current user's assigned expense shares, regardless of who paid. */
    public long getShareCents() {
        return shareCents;
    }

    /** Full expense records for the selected month, ranked by amount then creation time. */
    public List<Expense> getExpenses(Category category) {
        if (category == null) {
            return expenses;
        }
        List<Expense> matching = new ArrayList<>();
        for (Expense expense : expenses) {
            if (expense.getCategory() == category) {
                matching.add(expense);
            }
        }
        return matching;
    }
}
