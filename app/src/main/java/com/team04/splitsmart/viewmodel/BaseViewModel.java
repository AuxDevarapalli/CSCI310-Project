package com.team04.splitsmart.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.team04.splitsmart.logic.validation.ValidationResult;

/**
 * Shared state for every screen: a loading flag, one-time error/info messages, and the current
 * input error so a form can highlight the offending field.
 */
public abstract class BaseViewModel extends ViewModel {
    protected final MutableLiveData<Boolean> loading = new MutableLiveData<>(false);
    protected final MutableLiveData<Event<String>> error = new MutableLiveData<>();
    protected final MutableLiveData<Event<String>> info = new MutableLiveData<>();
    protected final MutableLiveData<ValidationResult> fieldError = new MutableLiveData<>();

    /** True while a repository call started by this screen is running. */
    public LiveData<Boolean> getLoading() {
        return loading;
    }

    /** One-time error messages (shown in a snackbar or banner). */
    public LiveData<Event<String>> getError() {
        return error;
    }

    /** One-time confirmations such as "Name updated". */
    public LiveData<Event<String>> getInfo() {
        return info;
    }

    /** The current validation problem, or {@code null} when the inputs are fine. */
    public LiveData<ValidationResult> getFieldError() {
        return fieldError;
    }

    public void clearError() {
        fieldError.setValue(null);
    }

    protected boolean isLoading() {
        return Boolean.TRUE.equals(loading.getValue());
    }

    protected void showError(String message) {
        error.setValue(new Event<>(message));
    }

    protected void showError(Exception e) {
        String message = e == null ? null : e.getMessage();
        showError(message == null || message.isEmpty()
                ? "Something went wrong. Please try again." : message);
    }

    protected void showInfo(String message) {
        info.setValue(new Event<>(message));
    }

    /** Publishes a failed validation; returns {@code true} if the result was invalid. */
    protected boolean rejectIfInvalid(ValidationResult result) {
        if (result.isValid()) {
            return false;
        }
        fieldError.setValue(result);
        return true;
    }
}
