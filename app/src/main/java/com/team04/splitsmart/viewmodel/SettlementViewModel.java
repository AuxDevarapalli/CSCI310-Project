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
 * logged-in user can record a payment they made or received. The debt is recalculated
 * from fresh data right before saving.
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
    private String counterpartyId;

    private final MutableLiveData<Boolean> ready = new MutableLiveData<>(false);
    private final MutableLiveData<String> loadError = new MutableLiveData<>();
    private final MutableLiveData<Boolean> incoming = new MutableLiveData<>(false);
    private final MutableLiveData<List<ReceiverOption>> receivers = new MutableLiveData<>();
    private final MutableLiveData<String> counterparty = new MutableLiveData<>();
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

    /** Whether the selected member paid the signed-in user. */
    public LiveData<Boolean> getIncoming() {
        return incoming;
    }

    /** Every other member with the signed pair balance. */
    public LiveData<List<ReceiverOption>> getReceivers() {
        return receivers;
    }

    public LiveData<String> getCounterparty() {
        return counterparty;
    }

    /** The amount owed in the selected payment direction. */
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
     * @param preselectedMember member to settle with (for example from the balance page), or null
     */
    public void init(String groupId, String settlementId, String preselectedMember) {
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
                loadMembers(settlementId, preselectedMember);
            }

            @Override
            public void onFailure(Exception e) {
                fail(e.getMessage());
            }
        });
    }

    private void loadMembers(String settlementId, String preselectedMember) {
        userRepository.getUsers(group.getMemberIds(), new DataCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> users) {
                memberList = new ArrayList<>(users);
                loadTransactions(settlementId, preselectedMember);
            }

            @Override
            public void onFailure(Exception e) {
                fail(e.getMessage());
            }
        });
    }

    private void loadTransactions(String settlementId, String preselectedMember) {
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
                            counterpartyId = currentUid.equals(editing.getPayerId())
                                    ? editing.getReceiverId() : editing.getPayerId();
                        } else {
                            date.setValue(dateProvider.today());
                            counterpartyId = pickInitialMember(preselectedMember);
                        }
                        loading.setValue(false);
                        publishReceivers();
                        if (counterpartyId != null) {
                            selectMember(counterpartyId);
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

    /** Uses the requested member if valid, otherwise the first member with an open balance. */
    private String pickInitialMember(String preselected) {
        if (preselected != null && !preselected.equals(currentUid) && group.isMember(preselected)) {
            return preselected;
        }
        for (User member : memberList) {
            if (!member.getUid().equals(currentUid) && pairBalance(member.getUid()) != 0) {
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

    private long pairBalance(String uid) {
        return balanceCalculator.netBetween(currentUid, uid, transactionsExcludingEditing());
    }

    private void publishReceivers() {
        List<ReceiverOption> options = new ArrayList<>();
        for (User member : memberList) {
            if (!member.getUid().equals(currentUid)) {
                options.add(new ReceiverOption(member, pairBalance(member.getUid())));
            }
        }
        receivers.setValue(options);
    }

    /** Chooses the other member and shows the debt in the selected direction. */
    public void selectMember(String uid) {
        if (group == null || uid == null || uid.equals(currentUid) || !group.isMember(uid)) {
            return;
        }
        counterpartyId = uid;
        long balance = pairBalance(uid);
        boolean received = editing == null ? balance > 0 : currentUid.equals(editing.getReceiverId());
        incoming.setValue(received);
        counterparty.setValue(uid);
        owedCents.setValue(Math.max(0, received ? balance : -balance));
        clearError();
    }

    /** Validates and saves (FR 5.2), re-reading the balances first so the limit is current. */
    public void save(String amountText, String note) {
        if (group == null || isLoading()) {
            return;
        }
        clearError();
        if (counterpartyId == null) {
            fieldError.setValue(ValidationResult.error(ValidationResult.FIELD_RECEIVER,
                    "Choose the other member"));
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
        boolean received = Boolean.TRUE.equals(incoming.getValue());
        String paidBy = received ? counterpartyId : currentUid;
        String paidTo = received ? currentUid : counterpartyId;
        loading.setValue(true);
        transactionRepository.getTransactions(group.getId(), new DataCallback<List<Transaction>>() {
            @Override
            public void onSuccess(List<Transaction> fresh) {
                transactions = new ArrayList<>(fresh);
                long owed = balanceCalculator.amountOwed(paidBy, paidTo, transactionsExcludingEditing());
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
                        note, paidBy, paidTo, amountCents));
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
