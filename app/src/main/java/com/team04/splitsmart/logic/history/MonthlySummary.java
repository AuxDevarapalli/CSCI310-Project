package com.team04.splitsmart.logic.history;

import com.team04.splitsmart.model.Category;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * A group's spending for one month (FR 5.7): how many expenses were created, the total of
 * their full amounts, and the total per category. Every category is present (zero when
 * unused) and the category totals always add up to the total. Settlements are never included.
 */
public class MonthlySummary {
    private final String yearMonth;
    private final int expenseCount;
    private final long totalCents;
    private final Map<Category, Long> categoryTotals;

    public MonthlySummary(String yearMonth, int expenseCount, long totalCents,
                          Map<Category, Long> categoryTotals) {
        this.yearMonth = yearMonth;
        this.expenseCount = expenseCount;
        this.totalCents = totalCents;
        EnumMap<Category, Long> totals = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            Long value = categoryTotals == null ? null : categoryTotals.get(category);
            totals.put(category, value == null ? 0L : value);
        }
        this.categoryTotals = Collections.unmodifiableMap(totals);
    }

    /** The month as "YYYY-MM". */
    public String getYearMonth() {
        return yearMonth;
    }

    public int getExpenseCount() {
        return expenseCount;
    }

    public long getTotalCents() {
        return totalCents;
    }

    /** Totals for all five categories in their declared order. */
    public Map<Category, Long> getCategoryTotals() {
        return categoryTotals;
    }

    public long getCategoryTotal(Category category) {
        Long value = categoryTotals.get(category);
        return value == null ? 0 : value;
    }

    public boolean isEmpty() {
        return expenseCount == 0;
    }
}
