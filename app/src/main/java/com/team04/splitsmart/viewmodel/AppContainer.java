package com.team04.splitsmart.viewmodel;

import com.team04.splitsmart.data.AuthRepository;
import com.team04.splitsmart.data.GroupRepository;
import com.team04.splitsmart.data.TransactionRepository;
import com.team04.splitsmart.data.UserRepository;
import com.team04.splitsmart.logic.balance.BalanceCalculator;
import com.team04.splitsmart.logic.history.HistoryFilter;
import com.team04.splitsmart.logic.history.SummaryCalculator;
import com.team04.splitsmart.logic.split.SplitCalculator;
import com.team04.splitsmart.logic.validation.DateProvider;
import com.team04.splitsmart.logic.validation.InputValidator;

/**
 * Creates and holds the single instance of every repository and engine (manual dependency
 * injection). {@link ViewModelFactory} hands these to the ViewModels it creates.
 */
public class AppContainer {
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final TransactionRepository transactionRepository;
    private final DateProvider dateProvider;
    private final InputValidator validator = new InputValidator();
    private final SplitCalculator splitCalculator = new SplitCalculator();
    private final BalanceCalculator balanceCalculator = new BalanceCalculator();
    private final HistoryFilter historyFilter = new HistoryFilter();
    private final SummaryCalculator summaryCalculator = new SummaryCalculator();

    public AppContainer(AuthRepository authRepository, UserRepository userRepository,
                        GroupRepository groupRepository,
                        TransactionRepository transactionRepository, DateProvider dateProvider) {
        this.authRepository = authRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.transactionRepository = transactionRepository;
        this.dateProvider = dateProvider;
    }

    /** Production wiring: Firebase-backed repositories and the device clock. */
    public static AppContainer createDefault() {
        return new AppContainer(AuthRepository.create(), UserRepository.create(),
                GroupRepository.create(), TransactionRepository.create(), new DateProvider());
    }

    public AuthRepository getAuthRepository() {
        return authRepository;
    }

    public UserRepository getUserRepository() {
        return userRepository;
    }

    public GroupRepository getGroupRepository() {
        return groupRepository;
    }

    public TransactionRepository getTransactionRepository() {
        return transactionRepository;
    }

    public DateProvider getDateProvider() {
        return dateProvider;
    }

    public InputValidator getValidator() {
        return validator;
    }

    public SplitCalculator getSplitCalculator() {
        return splitCalculator;
    }

    public BalanceCalculator getBalanceCalculator() {
        return balanceCalculator;
    }

    public HistoryFilter getHistoryFilter() {
        return historyFilter;
    }

    public SummaryCalculator getSummaryCalculator() {
        return summaryCalculator;
    }
}
