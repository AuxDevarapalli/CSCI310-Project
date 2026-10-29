package com.team04.splitsmart.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.team04.splitsmart.data.AuthRepository;
import com.team04.splitsmart.data.DataCallback;
import com.team04.splitsmart.data.UserRepository;
import com.team04.splitsmart.logic.validation.InputValidator;
import com.team04.splitsmart.model.User;

/** View and update the user's name, and log out (FR 1.3). */
public class ProfileViewModel extends BaseViewModel {
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final InputValidator validator;
    private final MutableLiveData<User> user = new MutableLiveData<>();
    private final MutableLiveData<Event<Boolean>> loggedOut = new MutableLiveData<>();

    public ProfileViewModel(AuthRepository authRepository, UserRepository userRepository,
                            InputValidator validator) {
        this.authRepository = authRepository;
        this.userRepository = userRepository;
        this.validator = validator;
    }

    public LiveData<User> getUser() {
        return user;
    }

    public LiveData<Event<Boolean>> getLoggedOut() {
        return loggedOut;
    }

    public void loadProfile() {
        loading.setValue(true);
        authRepository.getCurrentUser(new DataCallback<User>() {
            @Override
            public void onSuccess(User value) {
                loading.setValue(false);
                user.setValue(value);
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                showError(e);
            }
        });
    }

    /** Only the name can change; the USC email and USC ID are read-only (FR 1.3). */
    public void updateName(String name) {
        User current = user.getValue();
        if (current == null || isLoading()) {
            return;
        }
        if (rejectIfInvalid(validator.validateName(name))) {
            return;
        }
        clearError();
        String trimmed = name.trim();
        if (trimmed.equals(current.getName())) {
            showInfo("Your name is already " + trimmed);
            return;
        }
        loading.setValue(true);
        userRepository.updateName(current, trimmed, new DataCallback<User>() {
            @Override
            public void onSuccess(User updated) {
                loading.setValue(false);
                user.setValue(updated);
                showInfo("Name updated");
            }

            @Override
            public void onFailure(Exception e) {
                loading.setValue(false);
                showError(e);
            }
        });
    }

    public void logout() {
        authRepository.logout();
        loggedOut.setValue(new Event<>(true));
    }
}
