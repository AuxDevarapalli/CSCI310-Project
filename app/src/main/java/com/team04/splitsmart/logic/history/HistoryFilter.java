package com.team04.splitsmart.logic.history;

import com.team04.splitsmart.logic.validation.DateProvider;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Transaction;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

/**
 * Applies a {@link HistoryQuery} to transactions that are already loaded (FR 5.6). Filtering is
 * done in memory, so changing a filter never needs another database call.
 */
public class HistoryFilter {

    private static final Comparator<Transaction> OLDEST_FIRST = (a, b) -> {
        int byTime = Long.compare(a.getCreatedAt(), b.getCreatedAt());
        if (byTime != 0) return byTime;
        int byDate = a.getDate().compareTo(b.getDate());
        if (byDate != 0) return byDate;
        String idA = a.getId() == null ? "" : a.getId();
        String idB = b.getId() == null ? "" : b.getId();
        return idA.compareTo(idB);
    };

    /** Keeps the transactions matching every active filter, sorted as requested. */
    public List<Transaction> apply(List<? extends Transaction> all, HistoryQuery query) {
        List<Transaction> result = new ArrayList<>();
        if (all == null) {
            return result;
        }
        HistoryQuery q = query == null ? HistoryQuery.defaults() : query;
        String keyword = q.getKeyword();
        for (Transaction transaction : all) {
            if (matches(transaction, q, keyword)) {
                result.add(transaction);
            }
        }
        sort(result, q.getSortOrder());
        return result;
    }

    /** Distinct transaction months, including months that contain only settlements. */
    public List<String> availableMonths(List<? extends Transaction> transactions) {
        TreeSet<String> months = new TreeSet<>(Comparator.reverseOrder());
        for (Transaction transaction : transactions) {
            String month = DateProvider.monthOf(transaction.getDate());
            if (!month.isEmpty()) {
                months.add(month);
            }
        }
        return new ArrayList<>(months);
    }

    /** Sorts in place by creation time. */
    public void sort(List<Transaction> transactions, SortOrder order) {
        if (order == SortOrder.OLDEST_FIRST) {
            transactions.sort(OLDEST_FIRST);
        } else {
            transactions.sort(OLDEST_FIRST.reversed());
        }
    }

    private boolean matches(Transaction transaction, HistoryQuery query, String keyword) {
        if (query.getTypeFilter() != null && transaction.getType() != query.getTypeFilter()) {
            return false;
        }
        if (query.getCategoryFilter() != null) {
            if (!(transaction instanceof Expense)
                    || ((Expense) transaction).getCategory() != query.getCategoryFilter()) {
                return false;
            }
        }
        if (query.getMemberFilter() != null && !transaction.involves(query.getMemberFilter())) {
            return false;
        }
        if (query.getMonthFilter() != null
                && !query.getMonthFilter().equals(DateProvider.monthOf(transaction.getDate()))) {
            return false;
        }
        return keyword.isEmpty() || matchesKeyword(transaction, keyword);
    }

    /** Keyword search over expense titles and transaction notes, matching case. */
    private boolean matchesKeyword(Transaction transaction, String keyword) {
        if (transaction instanceof Expense
                && ((Expense) transaction).getTitle().contains(keyword)) {
            return true;
        }
        return transaction.getNote().contains(keyword);
    }
}
