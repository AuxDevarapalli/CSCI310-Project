package com.team04.splitsmart.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team04.splitsmart.data.AuthRepository;
import com.team04.splitsmart.data.DataCallback;
import com.team04.splitsmart.data.TransactionRepository;
import com.team04.splitsmart.logic.history.MonthlySummary;
import com.team04.splitsmart.logic.history.SpendingInsights;
import com.team04.splitsmart.logic.history.SummaryCalculator;
import com.team04.splitsmart.logic.validation.DateProvider;
import com.team04.splitsmart.model.Transaction;

import java.util.ArrayList;
import java.util.List;

/**
 * Monthly expense summary for the selected month (FR 5.7): number of expenses, total group
 * spending and spending per category. Settlements are excluded.
 */
public class SummaryViewModel extends BaseViewModel {
    private final AuthRepository authRepository;
    private final TransactionRepository transactionRepository;
    private final SummaryCalculator calculator;

    private List<Transaction> transactions = new ArrayList<>();
    private String selectedMonth;
    private String trendEnd;

    private final MutableLiveData<SpendingInsights> insights = new MutableLiveData<>();
    private final MutableLiveData<MonthlySummary> summary = new MutableLiveData<>();
    private final MutableLiveData<String> month = new MutableLiveData<>();
    private final MutableLiveData<List<String>> monthsWithExpenses = new MutableLiveData<>();
    private final MutableLiveData<String> loadError = new MutableLiveData<>();

    public SummaryViewModel(AuthRepository authRepository,
                            TransactionRepository transactionRepository,
                            SummaryCalculator calculator, DateProvider dateProvider) {
        this.authRepository = authRepository;
        this.transactionRepository = transactionRepository;
        this.calculator = calculator;
        this.selectedMonth = dateProvider.currentMonth();
        trendEnd = selectedMonth;
        month.setValue(selectedMonth);
    }

    public LiveData<MonthlySummary> getSummary() {
        return summary;
    }

    public LiveData<SpendingInsights> getInsights() {
        return insights;
    }

    /** Selected month as "YYYY-MM". */
    public LiveData<String> getMonth() {
        return month;
    }

    /** Months that contain at least one expense, newest first, for the month picker. */
    public LiveData<List<String>> getMonthsWithExpenses() {
        return monthsWithExpenses;
    }

    public LiveData<String> getLoadError() {
        return loadError;
    }

    public void load(String groupId) {
        loading.setValue(true);
        transactionRepository.getTransactions(groupId, new DataCallback<List<Transaction>>() {
            @Override
            public void onSuccess(List<Transaction> loaded) {
                loading.setValue(false);
                loadError.setValue(null);
                transactions = new ArrayList<>(loaded);
                monthsWithExpenses.setValue(calculator.monthsWithExpenses(transactions));
                recompute();
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                loadError.setValue(e.getMessage());
            }
        });
    }

    /** Selects a "YYYY-MM" month. */
    public void loadMonth(String yearMonth) {
        if (yearMonth == null || yearMonth.isEmpty()) {
            return;
        }
        trendEnd = yearMonth;
        selectedMonth = yearMonth;
        recompute();
    }

    public void selectTrendMonth(String yearMonth) {
        SpendingInsights report = insights.getValue();
        if (report != null && report.getTrend().containsKey(yearMonth)) {
            selectedMonth = yearMonth;
            recompute();
        }
    }

    public void previousMonth() {
        loadMonth(DateProvider.shiftMonth(selectedMonth, -1));
    }

    public void nextMonth() {
        loadMonth(DateProvider.shiftMonth(selectedMonth, 1));
    }

    private void recompute() {
        month.setValue(selectedMonth);
        summary.setValue(calculator.calculate(transactions, selectedMonth));
        insights.setValue(SpendingInsights.calculate(transactions, selectedMonth,
                authRepository.getCurrentUserId(), trendEnd));
    }
}
