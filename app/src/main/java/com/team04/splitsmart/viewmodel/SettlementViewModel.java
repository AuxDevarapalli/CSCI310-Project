package com.team04.splitsmart.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team04.splitsmart.data.AuthRepository;
import com.team04.splitsmart.data.DataCallback;
import com.team04.splitsmart.data.GroupRepository;
import com.team04.splitsmart.data.TransactionRepository;
import com.team04.splitsmart.data.UserRepository;
import com.team04.splitsmart.logic.balance.BalanceCalculator;
import com.team04.splitsmart.logic.validation.DateProvider;
import com.team04.splitsmart.logic.validation.InputValidator;
import com.team04.splitsmart.logic.validation.InvalidAmountException;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.User;

import java.util.ArrayList;
import java.util.List;

/**
 * Record or edit a payment made outside the app (FR 5.1 to FR 5.3, design Figure 6). Either side
 * of the payment can record it: the logged-in user either paid another member or was paid by
 * one. The amount may not exceed what the payer currently owes the receiver, which is
 * recalculated from fresh data right before saving.
 */
public class SettlementViewModel extends BaseViewModel {
    private final AuthRepository authRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final BalanceCalculator balanceCalculator;
    private final InputValidator validator;
    private final DateProvider dateProvider;

    private boolean initStarted;
    private String currentUid;
    private Group group;
    private List<User> memberList = new ArrayList<>();
    private List<Transaction> transactions = new ArrayList<>();
    private Settlement editing;
    /** True when the other member paid the logged-in user; false when the user paid them. */
    private boolean received;
    /** The member on the other side of the payment. */
    private String otherId;

    private final MutableLiveData<Boolean> ready = new MutableLiveData<>(false);
    private final MutableLiveData<String> loadError = new MutableLiveData<>();
    private final MutableLiveData<User> currentUser = new MutableLiveData<>();
    private final MutableLiveData<Boolean> receivedState = new MutableLiveData<>(false);
    private final MutableLiveData<List<MemberOption>> options = new MutableLiveData<>();
    private final MutableLiveData<String> other = new MutableLiveData<>();
    private final MutableLiveData<Long> owedCents = new MutableLiveData<>();
    private final MutableLiveData<Settlement> existingSettlement = new MutableLiveData<>();
    private final MutableLiveData<String> date = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> saved = new MutableLiveData<>();

    public SettlementViewModel(AuthRepository authRepository, GroupRepository groupRepository,
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

    public LiveData<Boolean> getReady() {
        return ready;
    }

    public LiveData<String> getLoadError() {
        return loadError;
    }

    /** The logged-in user, shown on their side of the payment. */
    public LiveData<User> getCurrentUser() {
        return currentUser;
    }

    /** True when the user is recording a payment they received rather than one they made. */
    public LiveData<Boolean> getReceived() {
        return receivedState;
    }

    public boolean isReceived() {
        return received;
    }

    /** Every other member with what is owed between them and the user in the chosen direction. */
    public LiveData<List<MemberOption>> getOptions() {
        return options;
    }

    /** The member on the other side of the payment, or null until one is chosen. */
    public LiveData<String> getOther() {
        return other;
    }

    /** What the payer owes the receiver for the selected member; 0 disables saving. */
    public LiveData<Long> getOwedCents() {
        return owedCents;
    }

    public LiveData<Settlement> getExistingSettlement() {
        return existingSettlement;
    }

    public LiveData<String> getDate() {
        return date;
    }

    public LiveData<Event<Boolean>> getSaved() {
        return saved;
    }

    public boolean isEditing() {
        return editing != null;
    }

    /**
     * @param settlementId        settlement to edit, or {@code null} to record a new one
     * @param preselectedMember   member on the other side (for example from the balance page)
     * @param preselectedReceived direction to start with, or {@code null} to pick one: paying
     *                            when the user owes someone, otherwise receiving when someone
     *                            owes the user
     */
    public void init(String groupId, String settlementId, String preselectedMember,
                     Boolean preselectedReceived) {
        if (initStarted) {
            return;
        }
        initStarted = true;
        currentUid = authRepository.getCurrentUserId();
        loading.setValue(true);
        groupRepository.getGroup(groupId, new DataCallback<Group>() {
            @Override
            public void onSuccess(Group loaded) {
                if (!loaded.isMember(currentUid)) {
                    fail("You are not a member of this group.");
                    return;
                }
                group = loaded;
                loadMembers(settlementId, preselectedMember, preselectedReceived);
            }

            @Override
            public void onFailure(Exception e) {
                fail(e.getMessage());
            }
        });
    }

    private void loadMembers(String settlementId, String preselectedMember,
                             Boolean preselectedReceived) {
        userRepository.getUsers(group.getMemberIds(), new DataCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> users) {
                memberList = new ArrayList<>(users);
                for (User user : users) {
                    if (user.getUid().equals(currentUid)) {
                        currentUser.setValue(user);
                    }
                }
                loadTransactions(settlementId, preselectedMember, preselectedReceived);
            }

            @Override
            public void onFailure(Exception e) {
                fail(e.getMessage());
            }
        });
    }

    private void loadTransactions(String settlementId, String preselectedMember,
                                  Boolean preselectedReceived) {
        transactionRepository.getTransactions(group.getId(),
                new DataCallback<List<Transaction>>() {
                    @Override
                    public void onSuccess(List<Transaction> loaded) {
                        transactions = new ArrayList<>(loaded);
                        if (settlementId != null) {
                            Transaction found = findById(settlementId);
                            if (!(found instanceof Settlement)) {
                                fail("This settlement no longer exists.");
                                return;
                            }
                            if (!found.isCreatedBy(currentUid)) {
                                fail("Only the person who recorded this settlement can edit it.");
                                return;
                            }
                            if (!group.getMemberIds().containsAll(found.getInvolvedUserIds())) {
                                fail("This settlement involves someone who has left the group, "
                                        + "so it can no longer be edited.");
                                return;
                            }
                            editing = (Settlement) found;
                            existingSettlement.setValue(editing);
                            date.setValue(editing.getDate());
                            received = currentUid.equals(editing.getReceiverId());
                            otherId = received ? editing.getPayerId() : editing.getReceiverId();
                        } else {
                            date.setValue(dateProvider.today());
                            received = pickInitialDirection(preselectedReceived);
                            otherId = pickInitialMember(preselectedMember);
                        }
                        loading.setValue(false);
                        receivedState.setValue(received);
                        publishOptions();
                        if (otherId != null) {
                            selectOther(otherId);
                        }
                        ready.setValue(true);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        fail(e.getMessage());
                    }
                });
    }

    private Transaction findById(String id) {
        for (Transaction transaction : transactions) {
            if (id.equals(transaction.getId())) {
                return transaction;
            }
        }
        return null;
    }

    /** Paying back comes first; receiving is the default only when the user owes nobody. */
    private boolean pickInitialDirection(Boolean preselected) {
        if (preselected != null) {
            return preselected;
        }
        if (anyoneOwed(false)) {
            return false;
        }
        return anyoneOwed(true);
    }

    private boolean anyoneOwed(boolean receivedDirection) {
        for (User member : memberList) {
            if (!member.getUid().equals(currentUid)
                    && owedFor(member.getUid(), receivedDirection) > 0) {
                return true;
            }
        }
        return false;
    }

    /** Uses the requested member if valid, otherwise the first one with something owed. */
    private String pickInitialMember(String preselected) {
        if (preselected != null && !preselected.equals(currentUid) && group.isMember(preselected)) {
            return preselected;
        }
        for (User member : memberList) {
            if (!member.getUid().equals(currentUid) && owedFor(member.getUid(), received) > 0) {
                return member.getUid();
            }
        }
        return null;
    }

    private void fail(String message) {
        loading.setValue(false);
        loadError.setValue(message == null ? "Could not load this form." : message);
    }

    /** Transactions minus the settlement being edited, so its old amount is not counted twice. */
    private List<Transaction> transactionsExcludingEditing() {
        if (editing == null) {
            return transactions;
        }
        List<Transaction> result = new ArrayList<>();
        for (Transaction transaction : transactions) {
            if (!editing.getId().equals(transaction.getId())) {
                result.add(transaction);
            }
        }
        return result;
    }

    /** What the payer owes the receiver when the other side of the payment is {@code uid}. */
    private long owedFor(String uid, boolean receivedDirection) {
        List<Transaction> counted = transactionsExcludingEditing();
        return receivedDirection
                ? balanceCalculator.amountOwed(uid, currentUid, counted)
                : balanceCalculator.amountOwed(currentUid, uid, counted);
    }

    private void publishOptions() {
        List<MemberOption> list = new ArrayList<>();
        for (User member : memberList) {
            if (!member.getUid().equals(currentUid)) {
                list.add(new MemberOption(member, owedFor(member.getUid(), received)));
            }
        }
        options.setValue(list);
    }

    /**
     * Switches between recording a payment the user made and one the user received. The chosen
     * member is kept; if none is chosen yet, the first one with something owed is picked.
     */
    public void setReceived(boolean value) {
        if (group == null || value == received) {
            return;
        }
        received = value;
        receivedState.setValue(value);
        publishOptions();
        String member = otherId != null ? otherId : pickInitialMember(null);
        if (member != null) {
            selectOther(member);
        } else {
            owedCents.setValue(0L);
        }
        clearError();
    }

    /** Chooses the other member and shows how much is owed between them (design Figure 6). */
    public void selectOther(String uid) {
        if (group == null || uid == null || uid.equals(currentUid) || !group.isMember(uid)) {
            return;
        }
        otherId = uid;
        other.setValue(uid);
        owedCents.setValue(owedFor(uid, received));
        clearError();
    }

    /** Validates and saves (FR 5.2), re-reading the balances first so the limit is current. */
    public void save(String amountText, String note) {
        if (group == null || isLoading()) {
            return;
        }
        clearError();
        if (otherId == null) {
            fieldError.setValue(ValidationResult.error(ValidationResult.FIELD_RECEIVER,
                    received ? "Choose the member who paid you" : "Choose the member you paid"));
            return;
        }
        long amountCents;
        try {
            amountCents = Money.parseDollars(amountText);
        } catch (InvalidAmountException e) {
            fieldError.setValue(ValidationResult.error(ValidationResult.FIELD_AMOUNT, e.getMessage()));
            return;
        }
        if (rejectIfInvalid(validator.validateNote(note))) {
            return;
        }
        if (editing != null && !editing.isCreatedBy(currentUid)) {
            showError("Only the person who recorded this settlement can edit it.");
            return;
        }
        String member = otherId;
        boolean receivedByUser = received;
        loading.setValue(true);
        transactionRepository.getTransactions(group.getId(), new DataCallback<List<Transaction>>() {
            @Override
            public void onSuccess(List<Transaction> fresh) {
                transactions = new ArrayList<>(fresh);
                long owed = owedFor(member, receivedByUser);
                owedCents.setValue(owed);
                publishOptions();
                ValidationResult result =
                        validator.validateSettlement(amountCents, owed, receivedByUser);
                if (!result.isValid()) {
                    loading.setValue(false);
                    fieldError.setValue(result);
                    return;
                }
                write(new Settlement(
                        editing == null ? null : editing.getId(),
                        group.getId(),
                        currentUid,
                        editing == null ? dateProvider.today() : editing.getDate(),
                        editing == null ? dateProvider.nowMillis() : editing.getCreatedAt(),
                        note,
                        receivedByUser ? member : currentUid,
                        receivedByUser ? currentUid : member,
                        amountCents));
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                showError(e);
            }
        });
    }

    private void write(Settlement settlement) {
        if (editing == null) {
            transactionRepository.addSettlement(settlement, dateProvider.nowMillis(),
                    new DataCallback<String>() {
                        @Override
                        public void onSuccess(String id) {
                            loading.setValue(false);
                            saved.setValue(new Event<>(true));
                        }

                        @Override
                        public void onFailure(Exception e) {
                            loading.setValue(false);
                            showError(e);
                        }
                    });
        } else {
            transactionRepository.updateSettlement(settlement, dateProvider.nowMillis(),
                    new DataCallback<Void>() {
                        @Override
                        public void onSuccess(Void value) {
                            loading.setValue(false);
                            saved.setValue(new Event<>(true));
                        }

                        @Override
                        public void onFailure(Exception e) {
                            loading.setValue(false);
                            showError(e);
                        }
                    });
        }
    }
}
