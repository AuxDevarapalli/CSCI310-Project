package com.team04.splitsmart.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team04.splitsmart.data.AuthRepository;
import com.team04.splitsmart.data.DataCallback;
import com.team04.splitsmart.logic.validation.DateProvider;
import com.team04.splitsmart.logic.validation.InputValidator;
import com.team04.splitsmart.model.User;

/** Registration and login (FR 1.1 to FR 1.3, design Figure 3). */
public class AuthViewModel extends BaseViewModel {
    private final AuthRepository authRepository;
    private final InputValidator validator;
    private final DateProvider dateProvider;
    private final MutableLiveData<User> currentUser = new MutableLiveData<>();

    public AuthViewModel(AuthRepository authRepository, InputValidator validator,
                         DateProvider dateProvider) {
        this.authRepository = authRepository;
        this.validator = validator;
        this.dateProvider = dateProvider;
    }

    /** Set once registration or login succeeds; the screen then opens the group list. */
    public LiveData<User> getCurrentUser() {
        return currentUser;
    }

    /** Firebase remembers the session, so a returning user skips the login screen. */
    public boolean isLoggedIn() {
        return authRepository.isLoggedIn();
    }

    public void register(String name, String email, String uscId, String password,
                         String confirmPassword) {
        if (isLoading()) {
            return;
        }
        if (rejectIfInvalid(validator.validateRegistration(name, email, uscId, password,
                confirmPassword))) {
            return;
        }
        clearError();
        loading.setValue(true);
        authRepository.register(name.trim(), InputValidator.normalizeEmail(email), uscId.trim(),
                password, dateProvider.nowMillis(), new DataCallback<User>() {
                    @Override
                    public void onSuccess(User user) {
                        loading.setValue(false);
                        currentUser.setValue(user);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        loading.setValue(false);
                        showError(e);
                    }
                });
    }

    public void login(String email, String password) {
        if (isLoading()) {
            return;
        }
        if (rejectIfInvalid(validator.validateLogin(email, password))) {
            return;
        }
        clearError();
        loading.setValue(true);
        authRepository.login(InputValidator.normalizeEmail(email), password,
                new DataCallback<User>() {
                    @Override
                    public void onSuccess(User user) {
                        loading.setValue(false);
                        currentUser.setValue(user);
                    }

                    @Override
                    public void onFailure(Exception e) {
                        loading.setValue(false);
                        showError(e);
                    }
                });
    }
}
