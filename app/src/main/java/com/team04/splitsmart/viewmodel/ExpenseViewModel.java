package com.team04.splitsmart.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team04.splitsmart.data.AuthRepository;
import com.team04.splitsmart.data.DataCallback;
import com.team04.splitsmart.data.GroupRepository;
import com.team04.splitsmart.data.TransactionRepository;
import com.team04.splitsmart.data.UserRepository;
import com.team04.splitsmart.logic.split.SplitCalculator;
import com.team04.splitsmart.logic.validation.DateProvider;
import com.team04.splitsmart.logic.validation.InputValidator;
import com.team04.splitsmart.logic.validation.InvalidAmountException;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.model.Share;
import com.team04.splitsmart.model.SplitMethod;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Create or edit an expense with a live split preview (FR 2.1 to FR 2.9, FR 3.1 to FR 3.7,
 * design Figure 4). Every change to the total, payer, participants, split method or a share
 * input recomputes the preview, and the expense can only be saved once the shares add up
 * exactly to the total.
 */
public class ExpenseViewModel extends BaseViewModel {
    private final AuthRepository authRepository;
    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final SplitCalculator splitCalculator;
    private final InputValidator validator;
    private final DateProvider dateProvider;

    // Loaded context
    private boolean initStarted;
    private String currentUid;
    private Group group;
    private List<User> memberList = new ArrayList<>();
    private Expense editing;

    // Form state
    private Long totalCents;
    private String totalError;
    private String payerId;
    private final Set<String> participantIds = new HashSet<>();
    private SplitMethod splitMethod = SplitMethod.EQUAL;
    private final Map<String, String> exactInputs = new HashMap<>();
    private final Map<String, String> percentInputs = new HashMap<>();
    private List<Share> currentShares = Collections.emptyList();
    private SplitStatus currentStatus = new SplitStatus("", false);

    // Outputs
    private final MutableLiveData<Boolean> ready = new MutableLiveData<>(false);
    private final MutableLiveData<String> loadError = new MutableLiveData<>();
    private final MutableLiveData<List<User>> members = new MutableLiveData<>();
    private final MutableLiveData<Expense> existingExpense = new MutableLiveData<>();
    private final MutableLiveData<String> payer = new MutableLiveData<>();
    private final MutableLiveData<SplitMethod> method = new MutableLiveData<>(SplitMethod.EQUAL);
    private final MutableLiveData<List<ParticipantRow>> rows = new MutableLiveData<>();
    private final MutableLiveData<SplitStatus> splitStatus = new MutableLiveData<>();
    private final MutableLiveData<String> amountError = new MutableLiveData<>();
    private final MutableLiveData<String> date = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> saved = new MutableLiveData<>();

    public ExpenseViewModel(AuthRepository authRepository, GroupRepository groupRepository,
                            UserRepository userRepository,
                            TransactionRepository transactionRepository,
                            SplitCalculator splitCalculator, InputValidator validator,
                            DateProvider dateProvider) {
        this.authRepository = authRepository;
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.splitCalculator = splitCalculator;
        this.validator = validator;
        this.dateProvider = dateProvider;
    }

    // ---- Outputs ----

    /** True once the group, members and (when editing) the expense are loaded. */
    public LiveData<Boolean> getReady() {
        return ready;
    }

    public LiveData<String> getLoadError() {
        return loadError;
    }

    public LiveData<List<User>> getMembers() {
        return members;
    }

    /** The expense being edited, published once so the form can fill in its fields. */
    public LiveData<Expense> getExistingExpense() {
        return existingExpense;
    }

    public LiveData<String> getPayer() {
        return payer;
    }

    public LiveData<SplitMethod> getSplitMethod() {
        return method;
    }

    public LiveData<List<ParticipantRow>> getRows() {
        return rows;
    }

    public LiveData<SplitStatus> getSplitStatus() {
        return splitStatus;
    }

    public LiveData<String> getAmountError() {
        return amountError;
    }

    /** The automatically recorded YYYY-MM-DD date shown read-only on the form (FR 2.4). */
    public LiveData<String> getDate() {
        return date;
    }

    public LiveData<Event<Boolean>> getSaved() {
        return saved;
    }

    public boolean isEditing() {
        return editing != null;
    }

    public String getCurrentUserId() {
        return authRepository.getCurrentUserId();
    }

    // ---- Loading ----

    /**
     * Loads everything the form needs. Pass {@code expenseId == null} to create a new expense:
     * the category starts as Other and the payer (the current user) is preselected as a
     * participant.
     */
    public void init(String groupId, String expenseId) {
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
                loadMembers(expenseId);
            }

            @Override
            public void onFailure(Exception e) {
                fail(e.getMessage());
            }
        });
    }

    private void loadMembers(String expenseId) {
        userRepository.getUsers(group.getMemberIds(), new DataCallback<List<User>>() {
            @Override
            public void onSuccess(List<User> users) {
                memberList = new ArrayList<>(users);
                members.setValue(memberList);
                if (expenseId == null) {
                    setUpNewExpense();
                } else {
                    loadExpense(expenseId);
                }
            }

            @Override
            public void onFailure(Exception e) {
                fail(e.getMessage());
            }
        });
    }

    private void loadExpense(String expenseId) {
        transactionRepository.getTransaction(group.getId(), expenseId,
                new DataCallback<Transaction>() {
                    @Override
                    public void onSuccess(Transaction transaction) {
                        if (!(transaction instanceof Expense)) {
                            fail("This transaction is not an expense.");
                            return;
                        }
                        if (!transaction.isCreatedBy(currentUid)) {
                            fail("Only the person who created this expense can edit it.");
                            return;
                        }
                        setUpExistingExpense((Expense) transaction);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        fail(e.getMessage());
                    }
                });
    }

    private void setUpNewExpense() {
        payerId = group.isMember(currentUid) ? currentUid : group.getOwnerId();
        participantIds.add(payerId);
        date.setValue(dateProvider.today());
        finishSetUp();
    }

    private void setUpExistingExpense(Expense expense) {
        editing = expense;
        totalCents = expense.getTotalCents();
        payerId = expense.getPayerId();
        splitMethod = expense.getSplitMethod();
        for (Share share : expense.getShares()) {
            participantIds.add(share.getParticipantId());
            exactInputs.put(share.getParticipantId(), Money.formatPlain(share.getAmountCents()));
            if (splitMethod == SplitMethod.PERCENTAGE) {
                percentInputs.put(share.getParticipantId(),
                        Money.formatPercentPlain(share.getPercentBasisPoints()));
            }
        }
        participantIds.add(payerId);
        date.setValue(expense.getDate());
        existingExpense.setValue(expense);
        finishSetUp();
    }

    private void finishSetUp() {
        loading.setValue(false);
        payer.setValue(payerId);
        method.setValue(splitMethod);
        recomputePreview();
        ready.setValue(true);
    }

    private void fail(String message) {
        loading.setValue(false);
        loadError.setValue(message == null ? "Could not load this form." : message);
    }

    // ---- Inputs (design Figure 4 loop) ----

    public void setTotal(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isEmpty()) {
            totalCents = null;
            totalError = null;
        } else {
            try {
                totalCents = Money.parseDollars(trimmed);
                totalError = null;
            } catch (InvalidAmountException e) {
                totalCents = null;
                totalError = e.getMessage();
            }
        }
        amountError.setValue(totalError);
        recomputePreview();
    }

    /** The payer is always kept as a participant (FR 2.6). */
    public void setPayer(String uid) {
        if (group == null || !group.isMember(uid) || uid.equals(payerId)) {
            return;
        }
        payerId = uid;
        participantIds.add(uid);
        payer.setValue(uid);
        recomputePreview();
    }

    /** Adds or removes a participant. The payer cannot be deselected (FR 2.6). */
    public void toggleParticipant(String uid, boolean checked) {
        if (uid == null || uid.equals(payerId)) {
            recomputePreview();
            return;
        }
        if (checked) {
            participantIds.add(uid);
        } else {
            participantIds.remove(uid);
        }
        recomputePreview();
    }

    /** Convenience: include every group member. */
    public void selectAllParticipants() {
        for (User member : memberList) {
            participantIds.add(member.getUid());
        }
        recomputePreview();
    }

    /** FR 3.1: exactly one split method per expense. */
    public void setSplitMethod(SplitMethod newMethod) {
        if (newMethod == null || newMethod == splitMethod) {
            return;
        }
        splitMethod = newMethod;
        method.setValue(newMethod);
        recomputePreview();
    }

    /** Text typed for one participant: dollars in exact mode, percent in percentage mode. */
    public void setSplitInput(String uid, String text) {
        if (splitMethod == SplitMethod.EXACT) {
            exactInputs.put(uid, text == null ? "" : text);
        } else if (splitMethod == SplitMethod.PERCENTAGE) {
            percentInputs.put(uid, text == null ? "" : text);
        } else {
            return;
        }
        recomputePreview();
    }

    /** Extra: fills every exact amount or percentage with an even split as a starting point. */
    public void fillEvenly() {
        List<String> ids = orderedParticipants();
        if (ids.isEmpty()) {
            return;
        }
        if (splitMethod == SplitMethod.EXACT) {
            if (totalCents == null) {
                showError("Enter the total amount first.");
                return;
            }
            for (Share share : splitCalculator.splitEqual(totalCents, ids)) {
                exactInputs.put(share.getParticipantId(), Money.formatPlain(share.getAmountCents()));
            }
        } else if (splitMethod == SplitMethod.PERCENTAGE) {
            for (Share share : splitCalculator.splitEqual(Money.FULL_PERCENT_BASIS_POINTS, ids)) {
                percentInputs.put(share.getParticipantId(),
                        Money.formatPercentPlain((int) share.getAmountCents()));
            }
        }
        recomputePreview();
    }

    // ---- Preview (FR 3.2 to FR 3.6) ----

    /** Participants in group-member order, which defines "first to last" for rounding. */
    private List<String> orderedParticipants() {
        List<String> ids = new ArrayList<>();
        for (User member : memberList) {
            if (participantIds.contains(member.getUid())) {
                ids.add(member.getUid());
            }
        }
        return ids;
    }

    /** Recomputes every participant's share and the status line. */
    public void recomputePreview() {
        if (group == null) {
            return;
        }
        List<String> ids = orderedParticipants();
        Map<String, String> inputErrors = new HashMap<>();
        List<Share> shares = Collections.emptyList();
        String message;
        boolean valid = false;

        if (totalCents == null) {
            message = totalError != null
                    ? "Enter a valid total to see each share"
                    : "Enter the total amount to see each person's share";
        } else if (splitMethod == SplitMethod.EQUAL) {
            shares = splitCalculator.splitEqual(totalCents, ids);
            valid = true;
            message = describeEqualSplit(shares);
        } else if (splitMethod == SplitMethod.EXACT) {
            LinkedHashMap<String, Long> amounts = new LinkedHashMap<>();
            boolean missing = false;
            for (String uid : ids) {
                String text = exactInputs.get(uid);
                if (text == null || text.trim().isEmpty()) {
                    missing = true;
                    amounts.put(uid, 0L);
                    continue;
                }
                try {
                    amounts.put(uid, Money.parseDollars(text));
                } catch (InvalidAmountException e) {
                    inputErrors.put(uid, e.getMessage());
                    amounts.put(uid, 0L);
                }
            }
            shares = splitCalculator.splitExact(amounts);
            long sum = SplitCalculator.sum(shares);
            if (!inputErrors.isEmpty()) {
                message = "Fix the highlighted amounts";
            } else if (missing) {
                message = "Enter an amount for every participant (use 0 if they owe nothing)";
            } else {
                ValidationResult check = splitCalculator.checkSum(totalCents, shares);
                valid = check.isValid();
                message = valid
                        ? "Amounts add up to " + Money.format(totalCents)
                        : check.getErrorMessage() + " (" + describeDifference(totalCents - sum) + ")";
            }
        } else {
            LinkedHashMap<String, Integer> percents = new LinkedHashMap<>();
            boolean missing = false;
            for (String uid : ids) {
                String text = percentInputs.get(uid);
                if (text == null || text.trim().isEmpty()) {
                    missing = true;
                    percents.put(uid, 0);
                    continue;
                }
                try {
                    percents.put(uid, Money.parsePercent(text));
                } catch (InvalidAmountException e) {
                    inputErrors.put(uid, e.getMessage());
                    percents.put(uid, 0);
                }
            }
            shares = splitCalculator.splitByPercent(totalCents, percents);
            if (!inputErrors.isEmpty()) {
                message = "Fix the highlighted percentages";
            } else if (missing) {
                message = "Enter a percentage for every participant (use 0 if they owe nothing)";
            } else {
                ValidationResult check = splitCalculator.checkPercentages(percents);
                if (check.isValid()) {
                    check = splitCalculator.checkSum(totalCents, shares);
                }
                valid = check.isValid();
                message = valid ? "Percentages add up to 100%" : check.getErrorMessage();
            }
        }
        if (ids.size() < 2) {
            valid = false;
            message = "Select at least one other participant besides the payer";
        }

        currentShares = shares;
        currentStatus = new SplitStatus(message, valid);
        splitStatus.setValue(currentStatus);
        rows.setValue(buildRows(shares, inputErrors));
    }

    private List<ParticipantRow> buildRows(List<Share> shares, Map<String, String> inputErrors) {
        Map<String, Long> preview = new HashMap<>();
        for (Share share : shares) {
            preview.put(share.getParticipantId(), share.getAmountCents());
        }
        List<ParticipantRow> result = new ArrayList<>();
        for (User member : memberList) {
            String uid = member.getUid();
            boolean checked = participantIds.contains(uid);
            String input;
            if (splitMethod == SplitMethod.EXACT) {
                input = exactInputs.get(uid);
            } else if (splitMethod == SplitMethod.PERCENTAGE) {
                input = percentInputs.get(uid);
            } else {
                input = "";
            }
            result.add(new ParticipantRow(member, uid.equals(payerId), uid.equals(currentUid),
                    checked, input, checked ? preview.get(uid) : null, inputErrors.get(uid)));
        }
        return result;
    }

    private static String describeEqualSplit(List<Share> shares) {
        if (shares.isEmpty()) {
            return "";
        }
        long max = Long.MIN_VALUE;
        long min = Long.MAX_VALUE;
        for (Share share : shares) {
            max = Math.max(max, share.getAmountCents());
            min = Math.min(min, share.getAmountCents());
        }
        if (max == min) {
            return "Split equally: " + Money.format(max) + " each";
        }
        return "Split equally: " + Money.format(max) + " or " + Money.format(min)
                + " each (extra cents go to the first people listed)";
    }

    private static String describeDifference(long remainingCents) {
        return remainingCents > 0
                ? Money.format(remainingCents) + " left to assign"
                : Money.format(-remainingCents) + " too much";
    }

    // ---- Save ----

    /** Validates and saves the expense (design Figure 4); the date is set automatically. */
    public void save(String title, Category category, String note) {
        if (group == null || isLoading()) {
            return;
        }
        clearError();
        if (title == null || title.trim().isEmpty()) {
            fieldError.setValue(ValidationResult.error(ValidationResult.FIELD_TITLE, "Enter a title"));
            return;
        }
        if (totalCents == null) {
            fieldError.setValue(ValidationResult.error(ValidationResult.FIELD_AMOUNT,
                    totalError != null ? totalError : "Enter the total amount"));
            return;
        }
        recomputePreview();
        if (orderedParticipants().size() < 2) {
            fieldError.setValue(ValidationResult.error(ValidationResult.FIELD_PARTICIPANTS,
                    "Select at least one other participant besides the payer"));
            return;
        }
        if (!currentStatus.isValid()) {
            fieldError.setValue(ValidationResult.error(ValidationResult.FIELD_SPLIT,
                    currentStatus.getMessage()));
            return;
        }
        if (editing != null && !editing.isCreatedBy(currentUid)) {
            showError("Only the person who created this expense can edit it.");
            return;
        }
        Expense expense = new Expense(
                editing == null ? null : editing.getId(),
                group.getId(),
                editing == null ? currentUid : editing.getCreatorId(),
                editing == null ? dateProvider.today() : editing.getDate(),
                editing == null ? dateProvider.nowMillis() : editing.getCreatedAt(),
                note, title, totalCents, category == null ? Category.DEFAULT : category,
                payerId, splitMethod, currentShares);
        if (rejectIfInvalid(validator.validateExpense(expense, group))) {
            return;
        }
        loading.setValue(true);
        if (editing == null) {
            transactionRepository.addExpense(expense, dateProvider.nowMillis(),
                    new DataCallback<String>() {
                        @Override
                        public void onSuccess(String id) {
                            onSaved();
                        }

                        @Override
                        public void onFailure(Exception e) {
                            loading.setValue(false);
                            showError(e);
                        }
                    });
        } else {
            transactionRepository.updateExpense(expense, dateProvider.nowMillis(),
                    new DataCallback<Void>() {
                        @Override
                        public void onSuccess(Void value) {
                            onSaved();
                        }

                        @Override
                        public void onFailure(Exception e) {
                            loading.setValue(false);
                            showError(e);
                        }
                    });
        }
    }

    private void onSaved() {
        loading.setValue(false);
        saved.setValue(new Event<>(true));
    }
}
