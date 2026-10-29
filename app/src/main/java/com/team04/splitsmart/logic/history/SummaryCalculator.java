package com.team04.splitsmart.logic.history;

import com.team04.splitsmart.logic.validation.DateProvider;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Transaction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/** Builds the monthly expense summary (FR 5.7). */
public class SummaryCalculator {

    /**
     * Summarises the expenses whose automatically recorded creation date falls in
     * {@code yearMonth} ("YYYY-MM"). The total is the sum of the full expense amounts; settlements
     * are ignored.
     */
    public MonthlySummary calculate(List<? extends Transaction> transactions, String yearMonth) {
        int count = 0;
        long total = 0;
        Map<Category, Long> totals = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            totals.put(category, 0L);
        }
        if (transactions != null) {
            for (Transaction transaction : transactions) {
                if (!(transaction instanceof Expense)) {
                    continue;
                }
                if (!DateProvider.monthOf(transaction.getDate()).equals(yearMonth)) {
                    continue;
                }
                Expense expense = (Expense) transaction;
                count++;
                total += expense.getTotalCents();
                totals.put(expense.getCategory(),
                        totals.get(expense.getCategory()) + expense.getTotalCents());
            }
        }
        return new MonthlySummary(yearMonth, count, total, totals);
    }

    /** Distinct "YYYY-MM" months that contain at least one expense, newest first. */
    public List<String> monthsWithExpenses(List<? extends Transaction> transactions) {
        TreeSet<String> months = new TreeSet<>();
        if (transactions != null) {
            for (Transaction transaction : transactions) {
                if (transaction instanceof Expense) {
                    String month = DateProvider.monthOf(transaction.getDate());
                    if (!month.isEmpty()) {
                        months.add(month);
                    }
                }
            }
        }
        List<String> result = new ArrayList<>(months);
        Collections.reverse(result);
        return result;
    }
}
