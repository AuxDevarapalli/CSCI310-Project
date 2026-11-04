package com.team04.splitsmart.logic.history;

import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.TransactionType;

/**
 * The search, filter and sort settings applied to a group's history (FR 5.6). A {@code null}
 * filter means "any". Use {@link #defaults()} for the initial state: no filters, newest first.
 */
public class HistoryQuery {
    private String keyword = "";
    private TransactionType typeFilter;
    private Category categoryFilter;
    private String memberFilter;
    private String monthFilter;
    private SortOrder sortOrder = SortOrder.NEWEST_FIRST;

    /** No keyword, no filters, newest first (FR 5.4). */
    public static HistoryQuery defaults() {
        return new HistoryQuery();
    }

    public HistoryQuery copy() {
        HistoryQuery copy = new HistoryQuery();
        copy.keyword = keyword;
        copy.typeFilter = typeFilter;
        copy.categoryFilter = categoryFilter;
        copy.memberFilter = memberFilter;
        copy.monthFilter = monthFilter;
        copy.sortOrder = sortOrder;
        return copy;
    }

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword == null ? "" : keyword.trim();
    }

    /** EXPENSE, SETTLEMENT, or {@code null} for both. */
    public TransactionType getTypeFilter() {
        return typeFilter;
    }

    public void setTypeFilter(TransactionType typeFilter) {
        this.typeFilter = typeFilter;
    }

    /** Expense category to keep, or {@code null}. A category filter only matches expenses. */
    public Category getCategoryFilter() {
        return categoryFilter;
    }

    public void setCategoryFilter(Category categoryFilter) {
        this.categoryFilter = categoryFilter;
    }

    /** User id that must be involved in the transaction, or {@code null}. */
    public String getMemberFilter() {
        return memberFilter;
    }

    public void setMemberFilter(String memberFilter) {
        this.memberFilter = memberFilter;
    }

    /** Calendar month (YYYY-MM), or null for all dates. */
    public String getMonthFilter() {
        return monthFilter;
    }

    public void setMonthFilter(String monthFilter) {
        this.monthFilter = monthFilter;
    }

    public SortOrder getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(SortOrder sortOrder) {
        this.sortOrder = sortOrder == null ? SortOrder.NEWEST_FIRST : sortOrder;
    }

    /** True when a keyword or any filter narrows the history (sorting does not count). */
    public boolean isFiltered() {
        return !keyword.isEmpty() || typeFilter != null || categoryFilter != null
                || memberFilter != null || monthFilter != null;
    }
}
