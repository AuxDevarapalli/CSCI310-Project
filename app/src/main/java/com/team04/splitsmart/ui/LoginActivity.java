package com.team04.splitsmart.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.team04.splitsmart.R;
import com.team04.splitsmart.SplitSmartApp;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.viewmodel.AuthViewModel;

/**
 * Launcher screen: logs a registered user in (FR 1.3). A returning user who never logged out
 * goes straight to their groups.
 */
public class LoginActivity extends BaseActivity {
    private AuthViewModel viewModel;
    private TextInputLayout emailLayout;
    private TextInputLayout passwordLayout;
    private TextInputEditText emailInput;
    private TextInputEditText passwordInput;
    private TextView errorText;
    private MaterialButton loginButton;
    private View progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!SplitSmartApp.from(this).isFirebaseConfigured()) {
            setContentWithInsets(R.layout.activity_setup_required);
            return;
        }
        viewModel = obtainViewModel(AuthViewModel.class);
        if (viewModel.isLoggedIn()) {
            openGroups();
            return;
        }
        setContentWithInsets(R.layout.activity_login);

        emailLayout = findViewById(R.id.login_email_layout);
        passwordLayout = findViewById(R.id.login_password_layout);
        emailInput = findViewById(R.id.login_email);
        passwordInput = findViewById(R.id.login_password);
        errorText = findViewById(R.id.login_error);
        loginButton = findViewById(R.id.login_button);
        progress = findViewById(R.id.login_progress);

        loginButton.setOnClickListener(v -> onLoginClick());
        passwordInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onLoginClick();
                return true;
            }
            return false;
        });
        findViewById(R.id.login_register_link).setOnClickListener(
                v -> startActivity(new Intent(this, RegisterActivity.class)));

        viewModel.getLoading().observe(this, loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            UiUtils.setVisible(progress, busy);
            loginButton.setEnabled(!busy);
        });
        viewModel.getFieldError().observe(this, this::showFieldError);
        viewModel.getError().observe(this, event -> {
            String message = event.getContentIfNotHandled();
            if (message != null) {
                errorText.setText(message);
                UiUtils.setVisible(errorText, true);
            }
        });
        viewModel.getCurrentUser().observe(this, user -> {
            if (user != null) {
                openGroups();
            }
        });
    }

    private void onLoginClick() {
        UiUtils.hideKeyboard(loginButton);
        UiUtils.setVisible(errorText, false);
        viewModel.login(UiUtils.textOf(emailInput), UiUtils.textOf(passwordInput));
    }

    private void showFieldError(ValidationResult result) {
        emailLayout.setError(null);
        passwordLayout.setError(null);
        if (result == null || result.isValid()) {
            return;
        }
        if (ValidationResult.FIELD_PASSWORD.equals(result.getField())) {
            passwordLayout.setError(result.getErrorMessage());
        } else {
            emailLayout.setError(result.getErrorMessage());
        }
    }

    private void openGroups() {
        Intent intent = new Intent(this, GroupListActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }
}
