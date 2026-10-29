package com.team04.splitsmart.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.viewmodel.GroupViewModel;

/** Create a group (FR 1.4) or, for the owner, edit its name and description (FR 1.7). */
public class GroupFormActivity extends BaseActivity {
    private static final String EXTRA_GROUP_ID = "groupId";
    private static final String KEY_PREFILLED = "prefilled";

    private GroupViewModel viewModel;
    private TextInputLayout nameLayout;
    private TextInputLayout descriptionLayout;
    private TextInputEditText nameInput;
    private TextInputEditText descriptionInput;
    private String groupId;
    private boolean prefilled;

    public static Intent newCreateIntent(Context context) {
        return new Intent(context, GroupFormActivity.class);
    }

    public static Intent newEditIntent(Context context, String groupId) {
        return new Intent(context, GroupFormActivity.class).putExtra(EXTRA_GROUP_ID, groupId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSignedIn()) {
            return;
        }
        setContentWithInsets(R.layout.activity_group_form);
        viewModel = obtainViewModel(GroupViewModel.class);
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        boolean editing = groupId != null;
        prefilled = savedInstanceState != null && savedInstanceState.getBoolean(KEY_PREFILLED);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(editing ? R.string.title_edit_group : R.string.title_new_group);
        toolbar.setNavigationOnClickListener(v -> finish());
        nameLayout = findViewById(R.id.group_name_layout);
        descriptionLayout = findViewById(R.id.group_description_layout);
        nameInput = findViewById(R.id.group_name);
        descriptionInput = findViewById(R.id.group_description);
        MaterialButton saveButton = findViewById(R.id.group_save_button);
        View progress = findViewById(R.id.group_progress);
        UiUtils.setVisible(findViewById(R.id.group_form_note), !editing);
        saveButton.setText(editing ? R.string.action_save_changes : R.string.action_create_group);

        saveButton.setOnClickListener(v -> {
            UiUtils.hideKeyboard(saveButton);
            String name = UiUtils.textOf(nameInput);
            String description = UiUtils.textOf(descriptionInput);
            if (editing) {
                viewModel.updateGroup(name, description);
            } else {
                viewModel.createGroup(name, description);
            }
        });

        if (editing) {
            viewModel.getGroup().observe(this, group -> {
                if (group == null) {
                    return;
                }
                if (!viewModel.isOwner()) {
                    Toast.makeText(this, R.string.error_owner_only_edit, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                if (!prefilled) {
                    nameInput.setText(group.getName());
                    descriptionInput.setText(group.getDescription());
                    prefilled = true;
                }
            });
            viewModel.getLoadError().observe(this, error -> {
                if (error != null) {
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show();
                    finish();
                }
            });
            viewModel.getGroupGone().observe(this, event -> {
                String message = event.getContentIfNotHandled();
                if (message != null) {
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    finish();
                }
            });
            if (viewModel.getGroup().getValue() == null) {
                viewModel.load(groupId);
            }
        }

        viewModel.getLoading().observe(this, loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            UiUtils.setVisible(progress, busy);
            saveButton.setEnabled(!busy);
        });
        viewModel.getFieldError().observe(this, this::showFieldError);
        viewModel.getGroupSaved().observe(this, event -> {
            String savedId = event.getContentIfNotHandled();
            if (savedId == null) {
                return;
            }
            if (editing) {
                Toast.makeText(this, R.string.message_group_updated, Toast.LENGTH_SHORT).show();
            } else {
                startActivity(GroupDetailActivity.newIntent(this, savedId,
                        GroupDetailActivity.TAB_MEMBERS, true));
            }
            finish();
        });
        observeMessages(viewModel);
    }

    private void showFieldError(ValidationResult result) {
        nameLayout.setError(null);
        descriptionLayout.setError(null);
        if (result == null || result.isValid()) {
            return;
        }
        if (ValidationResult.FIELD_DESCRIPTION.equals(result.getField())) {
            descriptionLayout.setError(result.getErrorMessage());
        } else {
            nameLayout.setError(result.getErrorMessage());
        }
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_PREFILLED, prefilled);
    }
}
