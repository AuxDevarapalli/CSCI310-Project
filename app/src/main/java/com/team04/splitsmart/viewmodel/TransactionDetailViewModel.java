package com.team04.splitsmart.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team04.splitsmart.data.AuthRepository;
import com.team04.splitsmart.data.DataCallback;
import com.team04.splitsmart.data.GroupRepository;
import com.team04.splitsmart.data.TransactionRepository;
import com.team04.splitsmart.data.UserRepository;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.User;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Complete information about one expense or settlement (FR 5.5). Any member can view it; only
 * its creator can edit or delete it (FR 2.7, FR 5.3).
 */
public class TransactionDetailViewModel extends BaseViewModel {
    private final AuthRepository authRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;

    private final MutableLiveData<Transaction> transaction = new MutableLiveData<>();
    private final MutableLiveData<Map<String, User>> users = new MutableLiveData<>();
    private final MutableLiveData<String> loadError = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> deleted = new MutableLiveData<>();

    public TransactionDetailViewModel(AuthRepository authRepository,
                                      GroupRepository groupRepository,
                                      UserRepository userRepository,
                                      TransactionRepository transactionRepository) {
        this.authRepository = authRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
    }

    public LiveData<Transaction> getTransaction() {
        return transaction;
    }

    /** Group members by uid, for showing names. */
    public LiveData<Map<String, User>> getUsers() {
        return users;
    }

    public LiveData<String> getLoadError() {
        return loadError;
    }

    public LiveData<Event<Boolean>> getDeleted() {
        return deleted;
    }

    public String getCurrentUserId() {
        return authRepository.getCurrentUserId();
    }

    /** Only the creator may edit or delete (FR 2.7, FR 5.3). */
    public boolean canEdit() {
        Transaction current = transaction.getValue();
        return current != null && current.isCreatedBy(getCurrentUserId());
    }

    /** Loads (or reloads, after an edit) the transaction and the group's members. */
    public void load(String groupId, String transactionId) {
        loading.setValue(true);
        groupRepository.getGroup(groupId, new DataCallback<Group>() {
            @Override
            public void onSuccess(Group group) {
                if (!group.isMember(getCurrentUserId())) {
                    fail("You are not a member of this group.");
                    return;
                }
                loadUsers(group, transactionId);
            }

            @Override
            public void onFailure(Exception e) {
                fail(e.getMessage());
            }
        });
    }

    private void loadUsers(Group group, String transactionId) {
        userRepository.getUsers(group.getMemberIds(), new DataCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> members) {
                Map<String, User> byId = new HashMap<>();
                for (User member : members) {
                    byId.put(member.getUid(), member);
                }
                users.setValue(byId);
                loadTransaction(group.getId(), transactionId);
            }

            @Override
            public void onFailure(Exception e) {
                fail(e.getMessage());
            }
        });
    }

    private void loadTransaction(String groupId, String transactionId) {
        transactionRepository.getTransaction(groupId, transactionId,
                new DataCallback<Transaction>() {
                    @Override
                    public void onSuccess(Transaction loaded) {
                        loading.setValue(false);
                        loadError.setValue(null);
                        transaction.setValue(loaded);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        fail(e.getMessage());
                    }
                });
    }

    private void fail(String message) {
        loading.setValue(false);
        loadError.setValue(message == null ? "Could not load this transaction." : message);
    }

    /** Deletes after the screen has asked for confirmation (FR 2.8, FR 5.3). */
    public void deleteTransaction() {
        Transaction current = transaction.getValue();
        if (current == null || isLoading()) {
            return;
        }
        if (!canEdit()) {
            showError("Only the person who created this "
                    + (current instanceof Expense ? "expense" : "settlement")
                    + " can delete it.");
            return;
        }
        loading.setValue(true);
        transactionRepository.deleteTransaction(current.getGroupId(), current.getId(),
                new DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        loading.setValue(false);
                        deleted.setValue(new Event<>(true));
                    }

                    @Override
                    public void onFailure(Exception e) {
                        loading.setValue(false);
                        showError(e);
                    }
                });
    }
}
