package com.team04.splitsmart.viewmodel;

import androidx.annotation.NonNull;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

/**
 * Builds every ViewModel with its dependencies from the {@link AppContainer}, so ViewModels
 * never create repositories themselves and can be tested with substitutes.
 */
public class ViewModelFactory implements ViewModelProvider.Factory {
    private final AppContainer container;

    public ViewModelFactory(AppContainer container) {
        this.container = container;
    }

    @NonNull
    @Override
    @SuppressWarnings("unchecked")
    public <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
        AppContainer c = container;
        if (modelClass == AuthViewModel.class) {
            return (T) new AuthViewModel(c.getAuthRepository(), c.getValidator(),
                    c.getDateProvider());
        }
        if (modelClass == ProfileViewModel.class) {
            return (T) new ProfileViewModel(c.getAuthRepository(), c.getUserRepository(),
                    c.getValidator());
        }
        if (modelClass == GroupListViewModel.class) {
            return (T) new GroupListViewModel(c.getAuthRepository(), c.getGroupRepository(),
                    c.getTransactionRepository(), c.getBalanceCalculator());
        }
        if (modelClass == GroupViewModel.class) {
            return (T) new GroupViewModel(c.getAuthRepository(), c.getGroupRepository(),
                    c.getUserRepository(), c.getTransactionRepository(),
                    c.getBalanceCalculator(), c.getValidator(), c.getDateProvider());
        }
        if (modelClass == ExpenseViewModel.class) {
            return (T) new ExpenseViewModel(c.getAuthRepository(), c.getGroupRepository(),
                    c.getUserRepository(), c.getTransactionRepository(), c.getSplitCalculator(),
                    c.getValidator(), c.getDateProvider());
        }
        if (modelClass == SettlementViewModel.class) {
            return (T) new SettlementViewModel(c.getAuthRepository(), c.getGroupRepository(),
                    c.getUserRepository(), c.getTransactionRepository(),
                    c.getBalanceCalculator(), c.getValidator(), c.getDateProvider());
        }
        if (modelClass == TransactionDetailViewModel.class) {
            return (T) new TransactionDetailViewModel(c.getAuthRepository(),
                    c.getGroupRepository(), c.getUserRepository(), c.getTransactionRepository());
        }
        if (modelClass == BalanceViewModel.class) {
            return (T) new BalanceViewModel(c.getAuthRepository(), c.getGroupRepository(),
                    c.getUserRepository(), c.getTransactionRepository(),
                    c.getBalanceCalculator());
        }
        if (modelClass == HistoryViewModel.class) {
            return (T) new HistoryViewModel(c.getAuthRepository(), c.getGroupRepository(),
                    c.getUserRepository(), c.getTransactionRepository(), c.getHistoryFilter());
        }
        if (modelClass == SummaryViewModel.class) {
            return (T) new SummaryViewModel(c.getTransactionRepository(),
                    c.getSummaryCalculator(), c.getDateProvider());
        }
        throw new IllegalArgumentException("Unknown ViewModel class: " + modelClass.getName());
    }
}
