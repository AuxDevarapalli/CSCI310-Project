package com.team04.splitsmart.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team04.splitsmart.data.AuthRepository;
import com.team04.splitsmart.data.DataCallback;
import com.team04.splitsmart.data.GroupRepository;
import com.team04.splitsmart.data.TransactionRepository;
import com.team04.splitsmart.data.UserRepository;
import com.team04.splitsmart.logic.balance.BalanceCalculator;
import com.team04.splitsmart.logic.balance.BalanceSummary;
import com.team04.splitsmart.logic.balance.PairBalance;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * The group balance page (FR 4.1 to FR 4.5, design Figure 5). Balances are recomputed from
 * every expense and settlement each time the page loads, so they always match the history.
 */
public class BalanceViewModel extends BaseViewModel {
    private final AuthRepository authRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceCalculator calculator;

    private final MutableLiveData<BalanceSummary> summary = new MutableLiveData<>();
    private final MutableLiveData<Group> group = new MutableLiveData<>();
    private final MutableLiveData<String> loadError = new MutableLiveData<>();

    public BalanceViewModel(AuthRepository authRepository, GroupRepository groupRepository,
                            UserRepository userRepository,
                            TransactionRepository transactionRepository,
                            BalanceCalculator calculator) {
        this.authRepository = authRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.calculator = calculator;
    }

    public LiveData<BalanceSummary> getSummary() {
        return summary;
    }

    public LiveData<Group> getGroup() {
        return group;
    }

    /** Non-null when loading failed; the page shows it with a Retry button. */
    public LiveData<String> getLoadError() {
        return loadError;
    }

    public String getCurrentUserId() {
        return authRepository.getCurrentUserId();
    }

    public void loadBalances(String groupId) {
        loading.setValue(true);
        groupRepository.getGroup(groupId, new DataCallback<Group>() {
            @Override
            public void onSuccess(Group loaded) {
                group.setValue(loaded);
                loadTransactions(loaded);
            }

            @Override
            public void onFailure(Exception e) {
                fail(e);
            }
        });
    }

    private void loadTransactions(Group loaded) {
        transactionRepository.getTransactions(loaded.getId(),
                new DataCallback<List<Transaction>>() {
                    @Override
                    public void onSuccess(List<Transaction> transactions) {
                        loadMembers(loaded, transactions);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        fail(e);
                    }
                });
    }

    private void loadMembers(Group loaded, List<Transaction> transactions) {
        userRepository.getUsers(loaded.getMemberIds(), new DataCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> members) {
                BalanceSummary computed =
                        calculator.computeSummary(getCurrentUserId(), members, transactions);
                loading.setValue(false);
                loadError.setValue(null);
                summary.setValue(sorted(computed));
            }

            @Override
            public void onFailure(Exception e) {
                fail(e);
            }
        });
    }

    /** Open balances first (largest first), then members the user is settled up with. */
    private static BalanceSummary sorted(BalanceSummary summary) {
        List<PairBalance> pairs = new ArrayList<>(summary.getPairs());
        pairs.sort((a, b) -> {
            if (a.isSettled() != b.isSettled()) {
                return a.isSettled() ? 1 : -1;
            }
            int byAmount = Long.compare(b.getAbsoluteCents(), a.getAbsoluteCents());
            if (byAmount != 0) {
                return byAmount;
            }
            return a.getOtherUser().getName().compareToIgnoreCase(b.getOtherUser().getName());
        });
        return new BalanceSummary(summary.getTotalToPayCents(),
                summary.getTotalToReceiveCents(), pairs);
    }

    private void fail(Exception e) {
        loading.setValue(false);
        loadError.setValue(e.getMessage());
    }
}
