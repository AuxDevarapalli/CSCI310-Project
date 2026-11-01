package com.team04.splitsmart.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team04.splitsmart.data.AuthRepository;
import com.team04.splitsmart.data.DataCallback;
import com.team04.splitsmart.data.GroupRepository;
import com.team04.splitsmart.data.Subscription;
import com.team04.splitsmart.data.TransactionRepository;
import com.team04.splitsmart.logic.balance.BalanceCalculator;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.model.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * The home screen: every group the user belongs to (FR 1.4, FR 1.6). As an extra, each row
 * also shows what the user owes or is owed in that group, plus overall totals.
 */
public class GroupListViewModel extends BaseViewModel {
    private final AuthRepository authRepository;
    private final GroupRepository groupRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceCalculator balanceCalculator;

    private final MutableLiveData<List<GroupListItem>> groups = new MutableLiveData<>();
    private final MutableLiveData<String> loadError = new MutableLiveData<>();
    /** Increases on every reload so answers to an older reload are ignored. */
    private int generation;
    private Subscription groupsSubscription;

    public GroupListViewModel(AuthRepository authRepository, GroupRepository groupRepository,
                              TransactionRepository transactionRepository,
                              BalanceCalculator balanceCalculator) {
        this.authRepository = authRepository;
        this.groupRepository = groupRepository;
        this.transactionRepository = transactionRepository;
        this.balanceCalculator = balanceCalculator;
    }

    public LiveData<List<GroupListItem>> getGroups() {
        return groups;
    }

    /** Non-null while the list could not be loaded; the screen offers a retry. */
    public LiveData<String> getLoadError() {
        return loadError;
    }

    public String getCurrentUserId() {
        return authRepository.getCurrentUserId();
    }

    /**
     * Reloads the list whenever the user is added to or removed from a group, or a group is
     * renamed or deleted, on any phone.
     */
    public void startLiveUpdates() {
        String uid = authRepository.getCurrentUserId();
        if (groupsSubscription == null && uid != null) {
            groupsSubscription = groupRepository.observeGroupsForUser(uid, this::loadGroups);
        }
    }

    @Override
    protected void onCleared() {
        if (groupsSubscription != null) {
            groupsSubscription.remove();
            groupsSubscription = null;
        }
    }

    public void loadGroups() {
        String uid = authRepository.getCurrentUserId();
        if (uid == null) {
            return;
        }
        int requestGeneration = ++generation;
        loading.setValue(true);
        groupRepository.getGroupsForUser(uid, new DataCallback<List<Group>>() {
            @Override
            public void onSuccess(List<Group> result) {
                if (requestGeneration != generation) {
                    return;
                }
                loading.setValue(false);
                loadError.setValue(null);
                List<GroupListItem> items = new ArrayList<>();
                List<GroupListItem> previous = groups.getValue();
                for (Group group : result) {
                    items.add(previousBalanceFor(group, previous));
                }
                groups.setValue(items);
                for (Group group : result) {
                    loadBalance(uid, group, requestGeneration);
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (requestGeneration != generation) {
                    return;
                }
                loading.setValue(false);
                loadError.setValue(e.getMessage());
            }
        });
    }

    /** Keeps the last known totals on screen while fresh ones load, to avoid flicker. */
    private static GroupListItem previousBalanceFor(Group group, List<GroupListItem> previous) {
        if (previous != null) {
            for (GroupListItem item : previous) {
                if (item.getGroup().getId().equals(group.getId()) && item.isBalanceLoaded()) {
                    return new GroupListItem(group, true, item.getTotalToPayCents(),
                            item.getTotalToReceiveCents());
                }
            }
        }
        return new GroupListItem(group);
    }

    private void loadBalance(String uid, Group group, int requestGeneration) {
        transactionRepository.getTransactions(group.getId(),
                new DataCallback<List<Transaction>>() {
                    @Override
                    public void onSuccess(List<Transaction> transactions) {
                        if (requestGeneration != generation) {
                            return;
                        }
                        long[] totals = balanceCalculator.computeTotals(uid,
                                group.getMemberIds(), transactions);
                        replaceItem(new GroupListItem(group, true, totals[0], totals[1]));
                    }

                    @Override
                    public void onFailure(Exception e) {
                        // The group itself is still shown; only its balance line is missing.
                    }
                });
    }

    private void replaceItem(GroupListItem updated) {
        List<GroupListItem> current = groups.getValue();
        if (current == null) {
            return;
        }
        List<GroupListItem> copy = new ArrayList<>(current);
        for (int i = 0; i < copy.size(); i++) {
            if (copy.get(i).getGroup().getId().equals(updated.getGroup().getId())) {
                copy.set(i, updated);
                groups.setValue(copy);
                return;
            }
        }
    }

    /** Sum of what the user owes across all loaded groups. */
    public static long totalToPay(List<GroupListItem> items) {
        long total = 0;
        if (items != null) {
            for (GroupListItem item : items) {
                total += item.getTotalToPayCents();
            }
        }
        return total;
    }

    /** Sum of what the user is owed across all loaded groups. */
    public static long totalToReceive(List<GroupListItem> items) {
        long total = 0;
        if (items != null) {
            for (GroupListItem item : items) {
                total += item.getTotalToReceiveCents();
            }
        }
        return total;
    }
}
