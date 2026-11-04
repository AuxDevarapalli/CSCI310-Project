package com.team04.splitsmart.ui;

import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.PickVisualMediaRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.viewmodel.GroupViewModel;

import java.io.IOException;

/** Create a group (FR 1.4) or, for the owner, edit its name and description (FR 1.7). */
public class GroupFormActivity extends BaseActivity {
    private static final String EXTRA_GROUP_ID = "groupId";
    private static final String KEY_PREFILLED = "prefilled";

    private final ActivityResultLauncher<PickVisualMediaRequest> photoPicker =
            registerForActivityResult(new ActivityResultContracts.PickVisualMedia(),
                    this::preparePhoto);
    private GroupViewModel viewModel;
    private ImageView photoPreview;
    private View photoPlaceholder;
    private MaterialButton choosePhoto;
    private MaterialButton removePhoto;
    private MaterialButton saveButton;
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
        saveButton = findViewById(R.id.group_save_button);
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
            findViewById(R.id.group_photo_editor).setVisibility(View.VISIBLE);
            photoPreview = findViewById(R.id.group_photo_preview);
            photoPlaceholder = findViewById(R.id.group_photo_placeholder);
            choosePhoto = findViewById(R.id.group_choose_photo);
            removePhoto = findViewById(R.id.group_remove_photo);
            choosePhoto.setOnClickListener(v -> photoPicker.launch(
                    new PickVisualMediaRequest.Builder()
                            .setMediaType(ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                            .build()));
            removePhoto.setOnClickListener(v -> viewModel.finishPhotoPreparation(new byte[0], null));
            viewModel.getPhotoDraft().observe(this, draft -> renderPhoto());
            viewModel.getPreparingPhoto().observe(this, preparing -> renderControls());
            viewModel.getGroup().observe(this, group -> {
                if (group == null) {
                    return;
                }
                if (!viewModel.isOwner()) {
                    Toast.makeText(this, R.string.error_owner_only_edit, Toast.LENGTH_LONG).show();
                    finish();
                    return;
                }
                renderPhoto();
                renderControls();
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
            renderControls();
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

    private void preparePhoto(Uri uri) {
        if (uri == null) {
            return;
        }
        GroupViewModel model = viewModel;
        ContentResolver resolver = getApplicationContext().getContentResolver();
        Handler main = new Handler(Looper.getMainLooper());
        String error = getString(R.string.group_photo_error);
        model.startPhotoPreparation();
        new Thread(() -> {
            try {
                byte[] photo = GroupPhoto.read(resolver, uri);
                main.post(() -> model.finishPhotoPreparation(photo, null));
            } catch (IOException | RuntimeException e) {
                main.post(() -> model.finishPhotoPreparation(null, error));
            }
        }, "group-photo").start();
    }

    private void renderPhoto() {
        Group group = viewModel.getGroup().getValue();
        byte[] draft = viewModel.getPhotoDraft().getValue();
        byte[] photo = draft != null ? draft : group == null ? null : group.getPhoto();
        GroupPhoto.bind(photoPreview, photo);
        boolean hasPhoto = photoPreview.getVisibility() == View.VISIBLE;
        UiUtils.setVisible(photoPlaceholder, !hasPhoto);
        UiUtils.setVisible(removePhoto, hasPhoto);
        choosePhoto.setText(hasPhoto ? R.string.group_change_photo : R.string.group_choose_photo);
    }

    private void renderControls() {
        boolean preparing = Boolean.TRUE.equals(viewModel.getPreparingPhoto().getValue());
        boolean enabled = !Boolean.TRUE.equals(viewModel.getLoading().getValue()) && !preparing;
        if (choosePhoto != null) {
            enabled &= viewModel.getGroup().getValue() != null && viewModel.isOwner();
            choosePhoto.setEnabled(enabled);
            removePhoto.setEnabled(enabled);
        }
        saveButton.setEnabled(enabled);
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
