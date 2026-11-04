package com.team04.splitsmart.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team04.splitsmart.data.AuthRepository;
import com.team04.splitsmart.data.DataCallback;
import com.team04.splitsmart.data.GroupRepository;
import com.team04.splitsmart.data.TransactionRepository;
import com.team04.splitsmart.data.UserRepository;
import com.team04.splitsmart.logic.history.HistoryCsv;
import com.team04.splitsmart.logic.history.HistoryFilter;
import com.team04.splitsmart.logic.history.HistoryQuery;
import com.team04.splitsmart.logic.history.SortOrder;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.TransactionType;
import com.team04.splitsmart.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The group history with search, filters and sorting (FR 5.4 to FR 5.6, design Figure 7).
 * Transactions are loaded once per refresh; changing the keyword, a filter or the sort order
 * re-filters the list in memory without another database call.
 */
public class HistoryViewModel extends BaseViewModel {
    private final AuthRepository authRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final HistoryFilter filter;

    private List<Transaction> all = new ArrayList<>();
    private String pendingExport;
    private HistoryQuery query = HistoryQuery.defaults();

    private final MutableLiveData<List<Transaction>> visible = new MutableLiveData<>();
    private final MutableLiveData<Integer> totalCount = new MutableLiveData<>(0);
    private final MutableLiveData<HistoryQuery> currentQuery = new MutableLiveData<>(query.copy());
    private final MutableLiveData<List<User>> members = new MutableLiveData<>();
    private final MutableLiveData<Map<String, User>> users = new MutableLiveData<>();
    private final MutableLiveData<String> loadError = new MutableLiveData<>();

    public HistoryViewModel(AuthRepository authRepository, GroupRepository groupRepository,
                            UserRepository userRepository,
                            TransactionRepository transactionRepository, HistoryFilter filter) {
        this.authRepository = authRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.filter = filter;
    }

    /** The transactions matching the current query, in the requested order. */
    public LiveData<List<Transaction>> getVisible() {
        return visible;
    }

    /** Number of transactions in the group before filtering. */
    public LiveData<Integer> getTotalCount() {
        return totalCount;
    }

    /** A copy of the active query, so the screen can show which filters are on. */
    public LiveData<HistoryQuery> getQuery() {
        return currentQuery;
    }

    public LiveData<List<User>> getMembers() {
        return members;
    }

    public LiveData<Map<String, User>> getUsers() {
        return users;
    }

    public LiveData<String> getLoadError() {
        return loadError;
    }

    public String getCurrentUserId() {
        return authRepository.getCurrentUserId();
    }

    public void load(String groupId) {
        loading.setValue(true);
        groupRepository.getGroup(groupId, new DataCallback<Group>() {
            @Override
            public void onSuccess(Group group) {
                loadMembers(group);
            }

            @Override
            public void onFailure(Exception e) {
                fail(e);
            }
        });
    }

    private void loadMembers(Group group) {
        userRepository.getUsers(group.getMemberIds(), new DataCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> loaded) {
                Map<String, User> byId = new HashMap<>();
                for (User user : loaded) {
                    byId.put(user.getUid(), user);
                }
                members.setValue(loaded);
                users.setValue(byId);
                loadTransactions(group);
            }

            @Override
            public void onFailure(Exception e) {
                fail(e);
            }
        });
    }

    private void loadTransactions(Group group) {
        transactionRepository.getTransactions(group.getId(),
                new DataCallback<List<Transaction>>() {
                    @Override
                    public void onSuccess(List<Transaction> transactions) {
                        loading.setValue(false);
                        loadError.setValue(null);
                        all = new ArrayList<>(transactions);
                        totalCount.setValue(all.size());
                        applyQuery(query);
                        loadFormerMembers(transactions);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        fail(e);
                    }
                });
    }

    /** Names of people who appear in old transactions but have since left the group. */
    private void loadFormerMembers(List<Transaction> transactions) {
        Map<String, User> known = users.getValue() == null
                ? new HashMap<>() : new HashMap<>(users.getValue());
        List<String> missing = FormerMembers.missingUserIds(transactions, known.keySet());
        if (missing.isEmpty()) {
            return;
        }
        userRepository.getUsers(missing, new DataCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> loaded) {
                for (User user : loaded) {
                    known.put(user.getUid(), user);
                }
                users.setValue(known);
                // Re-publish so rows are rebound with the newly known names.
                visible.setValue(filter.apply(all, query));
            }

            @Override
            public void onFailure(Exception e) {
                // Names fall back to "Unknown user"; the history itself is already shown.
            }
        });
    }

    private void fail(Exception e) {
        loading.setValue(false);
        loadError.setValue(e.getMessage());
    }

    /** Re-filters the loaded transactions; no database call is made. */
    public void applyQuery(HistoryQuery newQuery) {
        query = newQuery == null ? HistoryQuery.defaults() : newQuery.copy();
        currentQuery.setValue(query.copy());
        visible.setValue(filter.apply(all, query));
    }

    public void setKeyword(String keyword) {
        HistoryQuery next = query.copy();
        next.setKeyword(keyword);
        if (!next.getKeyword().equals(query.getKeyword())) {
            applyQuery(next);
        }
    }

    /** Expense, settlement, or {@code null} for both. Settlements have no category. */
    public void setTypeFilter(TransactionType type) {
        HistoryQuery next = query.copy();
        next.setTypeFilter(type);
        if (type == TransactionType.SETTLEMENT) {
            next.setCategoryFilter(null);
        }
        applyQuery(next);
    }

    /** Filters expenses by category; settlements are excluded while it is set. */
    public void setCategoryFilter(Category category) {
        HistoryQuery next = query.copy();
        next.setCategoryFilter(category);
        if (category != null && next.getTypeFilter() == TransactionType.SETTLEMENT) {
            next.setTypeFilter(null);
        }
        applyQuery(next);
    }

    public void setMemberFilter(String uid) {
        HistoryQuery next = query.copy();
        next.setMemberFilter(uid);
        applyQuery(next);
    }

    public List<String> getAvailableMonths() {
        return filter.availableMonths(all);
    }

    public void setMonthFilter(String month) {
        HistoryQuery next = query.copy();
        next.setMonthFilter(month);
        applyQuery(next);
    }

    public boolean canExport() {
        List<Transaction> matching = visible.getValue();
        return pendingExport == null && !isLoading() && loadError.getValue() == null
                && matching != null && !matching.isEmpty();
    }

    /** Freeze the displayed results while the Android file picker is open. */
    public boolean prepareExport(String unknownMember) {
        if (!canExport()) {
            return false;
        }
        Map<String, User> names = users.getValue();
        pendingExport = HistoryCsv.encode(visible.getValue(),
                names == null ? Collections.emptyMap() : names, unknownMember);
        return true;
    }

    public String takeExport() {
        String csv = pendingExport;
        pendingExport = null;
        return csv;
    }

    public void setSortOrder(SortOrder order) {
        HistoryQuery next = query.copy();
        next.setSortOrder(order);
        applyQuery(next);
    }

    public void toggleSortOrder() {
        setSortOrder(query.getSortOrder().toggled());
    }

    /** Removes the keyword and all filters but keeps the sort order. */
    public void clearFilters() {
        HistoryQuery next = HistoryQuery.defaults();
        next.setSortOrder(query.getSortOrder());
        applyQuery(next);
    }
}
