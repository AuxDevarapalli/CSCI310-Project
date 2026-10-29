package com.team04.splitsmart.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.team04.splitsmart.R;
import com.team04.splitsmart.viewmodel.ProfileViewModel;

/**
 * View and update your name, see your read-only USC email and USC ID, and log out (FR 1.3).
 */
public class ProfileActivity extends BaseActivity {
    private static final String KEY_PREFILLED = "prefilled";

    private ProfileViewModel viewModel;
    private TextInputLayout nameLayout;
    private TextInputEditText nameInput;
    private MaterialButton saveButton;
    private boolean prefilled;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSignedIn()) {
            return;
        }
        setContentWithInsets(R.layout.activity_profile);
        viewModel = obtainViewModel(ProfileViewModel.class);
        prefilled = savedInstanceState != null && savedInstanceState.getBoolean(KEY_PREFILLED);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        nameLayout = findViewById(R.id.profile_name_layout);
        nameInput = findViewById(R.id.profile_name);
        saveButton = findViewById(R.id.profile_save_button);
        TextView avatar = findViewById(R.id.profile_avatar);
        TextView displayName = findViewById(R.id.profile_display_name);
        TextView displayEmail = findViewById(R.id.profile_display_email);
        TextInputEditText emailField = findViewById(R.id.profile_email);
        TextInputEditText uscIdField = findViewById(R.id.profile_usc_id);
        View progress = findViewById(R.id.profile_progress);

        saveButton.setOnClickListener(v -> saveName());
        nameInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveName();
                return true;
            }
            return false;
        });
        findViewById(R.id.profile_logout_button).setOnClickListener(v -> new ConfirmDialog(this,
                getString(R.string.confirm_logout_title),
                getString(R.string.confirm_logout_message),
                getString(R.string.action_log_out),
                viewModel::logout).show());

        viewModel.getUser().observe(this, user -> {
            if (user == null) {
                return;
            }
            UiUtils.bindAvatar(avatar, user);
            displayName.setText(user.getName());
            displayEmail.setText(user.getUscEmail());
            emailField.setText(user.getUscEmail());
            uscIdField.setText(user.getUscId());
            if (!prefilled) {
                nameInput.setText(user.getName());
                prefilled = true;
            }
        });
        viewModel.getLoading().observe(this, loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            UiUtils.setVisible(progress, busy);
            saveButton.setEnabled(!busy);
        });
        viewModel.getFieldError().observe(this, result -> nameLayout.setError(
                result == null || result.isValid() ? null : result.getErrorMessage()));
        viewModel.getLoggedOut().observe(this, event -> {
            if (event.getContentIfNotHandled() != null) {
                Intent intent = new Intent(this, LoginActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                startActivity(intent);
                finish();
            }
        });
        observeMessages(viewModel);

        if (viewModel.getUser().getValue() == null) {
            viewModel.loadProfile();
        }
    }

    private void saveName() {
        UiUtils.hideKeyboard(nameInput);
        viewModel.updateName(UiUtils.textOf(nameInput));
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_PREFILLED, prefilled);
    }
}
