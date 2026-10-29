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
 * Record or edit a payment made outside the app (FR 5.1 to FR 5.3, design Figure 6). The
 * logged-in user is always the payer. The amount may not exceed what they currently owe the
 * receiver, which is recalculated from fresh data right before saving.
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
    private String receiverId;

    private final MutableLiveData<Boolean> ready = new MutableLiveData<>(false);
    private final MutableLiveData<String> loadError = new MutableLiveData<>();
    private final MutableLiveData<User> payer = new MutableLiveData<>();
    private final MutableLiveData<List<ReceiverOption>> receivers = new MutableLiveData<>();
    private final MutableLiveData<String> receiver = new MutableLiveData<>();
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

    /** The logged-in user, who is always the one who paid. */
    public LiveData<User> getPayer() {
        return payer;
    }

    /** Every other member with what the user owes them. */
    public LiveData<List<ReceiverOption>> getReceivers() {
        return receivers;
    }

    public LiveData<String> getReceiver() {
        return receiver;
    }

    /** What the user owes the selected receiver; 0 disables saving. */
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
     * @param settlementId       settlement to edit, or {@code null} to record a new one
     * @param preselectedReceiver member to pay (for example from the balance page), or null
     */
    public void init(String groupId, String settlementId, String preselectedReceiver) {
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
                loadMembers(settlementId, preselectedReceiver);
            }

            @Override
            public void onFailure(Exception e) {
                fail(e.getMessage());
            }
        });
    }

    private void loadMembers(String settlementId, String preselectedReceiver) {
        userRepository.getUsers(group.getMemberIds(), new DataCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> users) {
                memberList = new ArrayList<>(users);
                for (User user : users) {
                    if (user.getUid().equals(currentUid)) {
                        payer.setValue(user);
                    }
                }
                loadTransactions(settlementId, preselectedReceiver);
            }

            @Override
            public void onFailure(Exception e) {
                fail(e.getMessage());
            }
        });
    }

    private void loadTransactions(String settlementId, String preselectedReceiver) {
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
                            editing = (Settlement) found;
                            existingSettlement.setValue(editing);
                            date.setValue(editing.getDate());
                            receiverId = editing.getReceiverId();
                        } else {
                            date.setValue(dateProvider.today());
                            receiverId = pickInitialReceiver(preselectedReceiver);
                        }
                        loading.setValue(false);
                        publishReceivers();
                        if (receiverId != null) {
                            selectReceiver(receiverId);
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

    /** Uses the requested member if valid, otherwise the first member the user owes. */
    private String pickInitialReceiver(String preselected) {
        if (preselected != null && !preselected.equals(currentUid) && group.isMember(preselected)) {
            return preselected;
        }
        for (User member : memberList) {
            if (!member.getUid().equals(currentUid) && owedTo(member.getUid()) > 0) {
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

    private long owedTo(String uid) {
        return balanceCalculator.amountOwed(currentUid, uid, transactionsExcludingEditing());
    }

    private void publishReceivers() {
        List<ReceiverOption> options = new ArrayList<>();
        for (User member : memberList) {
            if (!member.getUid().equals(currentUid)) {
                options.add(new ReceiverOption(member, owedTo(member.getUid())));
            }
        }
        receivers.setValue(options);
    }

    /** Chooses who was paid and shows how much the user owes them (design Figure 6). */
    public void selectReceiver(String uid) {
        if (group == null || uid == null || uid.equals(currentUid) || !group.isMember(uid)) {
            return;
        }
        receiverId = uid;
        receiver.setValue(uid);
        owedCents.setValue(owedTo(uid));
        clearError();
    }

    /** Validates and saves (FR 5.2), re-reading the balances first so the limit is current. */
    public void save(String amountText, String note) {
        if (group == null || isLoading()) {
            return;
        }
        clearError();
        if (receiverId == null) {
            fieldError.setValue(ValidationResult.error(ValidationResult.FIELD_RECEIVER,
                    "Choose the member you paid"));
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
        String paidTo = receiverId;
        loading.setValue(true);
        transactionRepository.getTransactions(group.getId(), new DataCallback<List<Transaction>>() {
            @Override
            public void onSuccess(List<Transaction> fresh) {
                transactions = new ArrayList<>(fresh);
                long owed = owedTo(paidTo);
                owedCents.setValue(owed);
                publishReceivers();
                ValidationResult result = validator.validateSettlement(amountCents, owed);
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
                        note, currentUid, paidTo, amountCents));
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
