package com.team04.splitsmart.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.viewmodel.AuthViewModel;

/**
 * Account creation with a USC email and 10-digit USC ID (FR 1.1, FR 1.2, design Figure 3).
 */
public class RegisterActivity extends BaseActivity {
    private AuthViewModel viewModel;
    private TextInputLayout nameLayout;
    private TextInputLayout emailLayout;
    private TextInputLayout uscIdLayout;
    private TextInputLayout passwordLayout;
    private TextInputLayout confirmLayout;
    private TextInputEditText nameInput;
    private TextInputEditText emailInput;
    private TextInputEditText uscIdInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmInput;
    private TextView errorText;
    private MaterialButton registerButton;
    private View progress;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentWithInsets(R.layout.activity_register);
        viewModel = obtainViewModel(AuthViewModel.class);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        nameLayout = findViewById(R.id.register_name_layout);
        emailLayout = findViewById(R.id.register_email_layout);
        uscIdLayout = findViewById(R.id.register_usc_id_layout);
        passwordLayout = findViewById(R.id.register_password_layout);
        confirmLayout = findViewById(R.id.register_confirm_layout);
        nameInput = findViewById(R.id.register_name);
        emailInput = findViewById(R.id.register_email);
        uscIdInput = findViewById(R.id.register_usc_id);
        passwordInput = findViewById(R.id.register_password);
        confirmInput = findViewById(R.id.register_confirm);
        errorText = findViewById(R.id.register_error);
        registerButton = findViewById(R.id.register_button);
        progress = findViewById(R.id.register_progress);

        registerButton.setOnClickListener(v -> onRegisterClick());
        confirmInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                onRegisterClick();
                return true;
            }
            return false;
        });
        findViewById(R.id.register_login_link).setOnClickListener(v -> finish());

        viewModel.getLoading().observe(this, loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            UiUtils.setVisible(progress, busy);
            registerButton.setEnabled(!busy);
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
                Intent intent = new Intent(this, GroupListActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
    }

    private void onRegisterClick() {
        UiUtils.hideKeyboard(registerButton);
        UiUtils.setVisible(errorText, false);
        viewModel.register(UiUtils.textOf(nameInput), UiUtils.textOf(emailInput),
                UiUtils.textOf(uscIdInput), UiUtils.textOf(passwordInput),
                UiUtils.textOf(confirmInput));
    }

    private void showFieldError(ValidationResult result) {
        nameLayout.setError(null);
        emailLayout.setError(null);
        uscIdLayout.setError(null);
        passwordLayout.setError(null);
        confirmLayout.setError(null);
        if (result == null || result.isValid()) {
            return;
        }
        String field = result.getField();
        TextInputLayout target;
        if (ValidationResult.FIELD_EMAIL.equals(field)) {
            target = emailLayout;
        } else if (ValidationResult.FIELD_USC_ID.equals(field)) {
            target = uscIdLayout;
        } else if (ValidationResult.FIELD_PASSWORD.equals(field)) {
            target = passwordLayout;
        } else if (ValidationResult.FIELD_CONFIRM_PASSWORD.equals(field)) {
            target = confirmLayout;
        } else {
            target = nameLayout;
        }
        target.setError(result.getErrorMessage());
        if (target.getEditText() != null) {
            target.getEditText().requestFocus();
        }
    }
}
