package com.team04.splitsmart.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team04.splitsmart.data.AuthRepository;
import com.team04.splitsmart.data.DataCallback;
import com.team04.splitsmart.data.GroupRepository;
import com.team04.splitsmart.data.RepositoryException;
import com.team04.splitsmart.data.Subscription;
import com.team04.splitsmart.data.TransactionRepository;
import com.team04.splitsmart.data.UserRepository;
import com.team04.splitsmart.logic.balance.BalanceCalculator;
import com.team04.splitsmart.logic.validation.DateProvider;
import com.team04.splitsmart.logic.validation.InputValidator;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates, shows, edits and deletes a group and manages its members (FR 1.4 to FR 1.7), including
 * the optional extensions: the owner removes a member or hands ownership to another member, and
 * a member leaves the group. Nobody can leave or be removed while they still owe or are owed
 * money in the group, so no debt ever disappears.
 * Owner-only actions are re-checked here before every write, in addition to being hidden in
 * the UI and enforced by the Firestore rules.
 */
public class GroupViewModel extends BaseViewModel {
    private final AuthRepository authRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceCalculator balanceCalculator;
    private final InputValidator validator;
    private final DateProvider dateProvider;

    private final MutableLiveData<Group> group = new MutableLiveData<>();
    private final MutableLiveData<List<User>> members = new MutableLiveData<>();
    private final MutableLiveData<String> loadError = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> groupGone = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> groupSaved = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> groupDeleted = new MutableLiveData<>();
    private final MutableLiveData<Event<String>> memberAdded = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> leftGroup = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> remoteChange = new MutableLiveData<>();
    private final List<Subscription> subscriptions = new ArrayList<>();

    public GroupViewModel(AuthRepository authRepository, GroupRepository groupRepository,
                          UserRepository userRepository,
                          TransactionRepository transactionRepository,
                          BalanceCalculator balanceCalculator, InputValidator validator,
                          DateProvider dateProvider) {
        this.authRepository = authRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.balanceCalculator = balanceCalculator;
        this.validator = validator;
        this.dateProvider = dateProvider;
    }

    public LiveData<Group> getGroup() {
        return group;
    }

    /** Members in the order they joined; the owner first. */
    public LiveData<List<User>> getMembers() {
        return members;
    }

    /** Non-null when the group could not be loaded for a temporary reason (e.g. offline). */
    public LiveData<String> getLoadError() {
        return loadError;
    }

    /**
     * Fires when the group was deleted (for example by its owner on another phone) or the user
     * is not a member; the screen should close.
     */
    public LiveData<Event<String>> getGroupGone() {
        return groupGone;
    }

    /** Fires with the group id after a create or edit succeeds. */
    public LiveData<Event<String>> getGroupSaved() {
        return groupSaved;
    }

    public LiveData<Event<Boolean>> getGroupDeleted() {
        return groupDeleted;
    }

    /** Fires with the new member's name after they are added. */
    public LiveData<Event<String>> getMemberAdded() {
        return memberAdded;
    }

    /** Fires after the current user left the group; the screen should close. */
    public LiveData<Event<Boolean>> getLeftGroup() {
        return leftGroup;
    }

    /**
     * Fires when another member changed the group or any of its expenses and settlements; the
     * screen reloads every tab, so changes appear live without pulling to refresh.
     */
    public LiveData<Event<Boolean>> getRemoteChange() {
        return remoteChange;
    }

    /** Starts listening for changes made on other phones (once per ViewModel). */
    public void startLiveUpdates(String groupId) {
        if (!subscriptions.isEmpty()) {
            return;
        }
        Runnable changed = () -> remoteChange.setValue(new Event<>(true));
        subscriptions.add(groupRepository.observeGroup(groupId, changed));
        subscriptions.add(transactionRepository.observeTransactions(groupId, changed));
    }

    @Override
    protected void onCleared() {
        for (Subscription subscription : subscriptions) {
            subscription.remove();
        }
        subscriptions.clear();
    }

    public String getCurrentUserId() {
        return authRepository.getCurrentUserId();
    }

    /** True when the logged-in user owns the loaded group (FR 1.7). */
    public boolean isOwner() {
        Group current = group.getValue();
        return current != null && current.isOwner(getCurrentUserId());
    }

    /** Loads the group and its member list (FR 1.6). */
    public void load(String groupId) {
        loading.setValue(true);
        groupRepository.getGroup(groupId, new DataCallback<Group>() {
            @Override
            public void onSuccess(Group loaded) {
                if (!loaded.isMember(getCurrentUserId())) {
                    loading.setValue(false);
                    groupGone.setValue(new Event<>("You are not a member of this group."));
                    return;
                }
                loadError.setValue(null);
                group.setValue(loaded);
                loadMembers(loaded);
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                if (RepositoryException.isNotFound(e)) {
                    groupGone.setValue(new Event<>(e.getMessage()));
                } else {
                    loadError.setValue(e.getMessage());
                }
            }
        });
    }

    private void loadMembers(Group loaded) {
        userRepository.getUsers(loaded.getMemberIds(), new DataCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> users) {
                loading.setValue(false);
                members.setValue(users);
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                showError(e);
            }
        });
    }

    /** FR 1.4: the creator becomes the owner and first member. */
    public void createGroup(String name, String description) {
        if (isLoading()) {
            return;
        }
        if (rejectIfInvalid(validator.validateGroup(name, description))) {
            return;
        }
        String uid = getCurrentUserId();
        if (uid == null) {
            showError("Please log in again.");
            return;
        }
        clearError();
        loading.setValue(true);
        groupRepository.createGroup(name.trim(), trimOrEmpty(description), uid,
                dateProvider.nowMillis(), new DataCallback<Group>() {
                    @Override
                    public void onSuccess(Group created) {
                        loading.setValue(false);
                        group.setValue(created);
                        groupSaved.setValue(new Event<>(created.getId()));
                    }

                    @Override
                    public void onFailure(Exception e) {
                        loading.setValue(false);
                        showError(e);
                    }
                });
    }

    /** FR 1.7: only the owner may change the name and description. */
    public void updateGroup(String name, String description) {
        Group current = group.getValue();
        if (current == null || isLoading()) {
            return;
        }
        if (!isOwner()) {
            showError("Only the group owner can edit this group.");
            return;
        }
        if (rejectIfInvalid(validator.validateGroup(name, description))) {
            return;
        }
        clearError();
        String newName = name.trim();
        String newDescription = trimOrEmpty(description);
        loading.setValue(true);
        groupRepository.updateGroup(current.getId(), newName, newDescription,
                new DataCallback<Void>() {
                    @Override
                    public void onSuccess(Void value) {
                        loading.setValue(false);
                        group.setValue(current.withDetails(newName, newDescription));
                        groupSaved.setValue(new Event<>(current.getId()));
                    }

                    @Override
                    public void onFailure(Exception e) {
                        loading.setValue(false);
                        showError(e);
                    }
                });
    }

    /**
     * FR 1.5: the owner adds a registered user by USC email. Unregistered emails and existing
     * members are rejected with a message.
     */
    public void addMember(String email) {
        Group current = group.getValue();
        if (current == null || isLoading()) {
            return;
        }
        if (!isOwner()) {
            showError("Only the group owner can add members.");
            return;
        }
        ValidationResult emailCheck = validator.validateUscEmail(email);
        if (rejectIfInvalid(emailCheck)) {
            return;
        }
        clearError();
        String normalized = InputValidator.normalizeEmail(email);
        loading.setValue(true);
        userRepository.findByEmail(normalized, new DataCallback<User>() {
            @Override
            public void onSuccess(User found) {
                if (found == null) {
                    loading.setValue(false);
                    fieldError.setValue(ValidationResult.error(ValidationResult.FIELD_EMAIL,
                            "No SplitSmart account uses " + normalized));
                    return;
                }
                if (current.isMember(found.getUid())) {
                    loading.setValue(false);
                    fieldError.setValue(ValidationResult.error(ValidationResult.FIELD_EMAIL,
                            found.getName() + " is already a member of this group"));
                    return;
                }
                saveMember(current, found);
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                showError(e);
            }
        });
    }

    private void saveMember(Group current, User newMember) {
        groupRepository.addMember(current.getId(), newMember.getUid(), new DataCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                loading.setValue(false);
                group.setValue(current.withMember(newMember.getUid()));
                List<User> updated = members.getValue() == null
                        ? new ArrayList<>() : new ArrayList<>(members.getValue());
                updated.add(newMember);
                members.setValue(updated);
                memberAdded.setValue(new Event<>(newMember.getName()));
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                showError(e);
            }
        });
    }

    /** FR 1.7: owner-only; also deletes every expense and settlement in the group. */
    public void deleteGroup() {
        Group current = group.getValue();
        if (current == null || isLoading()) {
            return;
        }
        if (!isOwner()) {
            showError("Only the group owner can delete this group.");
            return;
        }
        loading.setValue(true);
        groupRepository.deleteGroup(current.getId(), new DataCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                loading.setValue(false);
                groupDeleted.setValue(new Event<>(true));
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                showError(e);
            }
        });
    }

    /** Owner-only: removes another member who is settled up with everyone in the group. */
    public void removeMember(String uid) {
        Group current = group.getValue();
        if (current == null || isLoading()) {
            return;
        }
        if (!isOwner()) {
            showError("Only the group owner can remove members.");
            return;
        }
        if (current.isOwner(uid) || !current.isMember(uid)) {
            showError("The owner cannot be removed. Transfer ownership first.");
            return;
        }
        String name = nameOf(uid);
        removeIfSettled(current, uid, name + " still owes or is owed ", () -> {
            group.setValue(current.withoutMember(uid));
            members.setValue(withoutUser(members.getValue(), uid));
            showInfo(name + " was removed from the group");
        });
    }

    /** Lets a member who is not the owner leave, once they are settled up with everyone. */
    public void leaveGroup() {
        Group current = group.getValue();
        String uid = getCurrentUserId();
        if (current == null || uid == null || isLoading()) {
            return;
        }
        if (current.isOwner(uid)) {
            showError("Transfer ownership to another member before leaving the group.");
            return;
        }
        removeIfSettled(current, uid, "You still owe or are owed ",
                () -> leftGroup.setValue(new Event<>(true)));
    }

    /** Owner-only: makes another member the owner. The old owner stays a member. */
    public void transferOwnership(String newOwnerId) {
        Group current = group.getValue();
        if (current == null || isLoading()) {
            return;
        }
        if (!isOwner()) {
            showError("Only the group owner can transfer ownership.");
            return;
        }
        if (!current.isMember(newOwnerId) || current.isOwner(newOwnerId)) {
            showError("Choose another member of this group.");
            return;
        }
        String name = nameOf(newOwnerId);
        loading.setValue(true);
        groupRepository.transferOwnership(current.getId(), newOwnerId, new DataCallback<Void>() {
            @Override
            public void onSuccess(Void value) {
                loading.setValue(false);
                group.setValue(current.withOwner(newOwnerId));
                members.setValue(members.getValue());
                showInfo(name + " is now the owner of this group");
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                showError(e);
            }
        });
    }

    /**
     * Reads the latest transactions and removes {@code uid} only if their balance with every
     * other member is zero; otherwise shows {@code unsettledPrefix} followed by the amounts.
     */
    private void removeIfSettled(Group current, String uid, String unsettledPrefix,
                                 Runnable onRemoved) {
        clearError();
        loading.setValue(true);
        transactionRepository.getTransactions(current.getId(),
                new DataCallback<List<Transaction>>() {
                    @Override
                    public void onSuccess(List<Transaction> transactions) {
                        long[] totals = balanceCalculator.computeTotals(uid,
                                current.getMemberIds(), transactions);
                        if (totals[0] != 0 || totals[1] != 0) {
                            loading.setValue(false);
                            showError(unsettledPrefix + describeUnsettled(totals)
                                    + " in this group. Settle up first.");
                            return;
                        }
                        groupRepository.removeMember(current.getId(), uid,
                                new DataCallback<Void>() {
                                    @Override
                                    public void onSuccess(Void value) {
                                        loading.setValue(false);
                                        onRemoved.run();
                                    }

                                    @Override
                                    public void onFailure(Exception e) {
                                        loading.setValue(false);
                                        showError(e);
                                    }
                                });
                    }

                    @Override
                    public void onFailure(Exception e) {
                        loading.setValue(false);
                        showError(e);
                    }
                });
    }

    private static String describeUnsettled(long[] totals) {
        if (totals[0] != 0 && totals[1] != 0) {
            return "money (owes " + Money.format(totals[0]) + ", is owed "
                    + Money.format(totals[1]) + ")";
        }
        return Money.format(totals[0] != 0 ? totals[0] : totals[1]);
    }

    private String nameOf(String uid) {
        List<User> current = members.getValue();
        if (current != null) {
            for (User user : current) {
                if (user.getUid().equals(uid)) {
                    return user.getName();
                }
            }
        }
        return "This member";
    }

    private static List<User> withoutUser(List<User> users, String uid) {
        List<User> result = new ArrayList<>();
        if (users != null) {
            for (User user : users) {
                if (!user.getUid().equals(uid)) {
                    result.add(user);
                }
            }
        }
        return result;
    }

    private static String trimOrEmpty(String text) {
        return text == null ? "" : text.trim();
    }
}
