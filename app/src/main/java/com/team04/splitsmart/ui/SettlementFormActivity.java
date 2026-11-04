package com.team04.splitsmart.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.viewmodel.ReceiverOption;
import com.team04.splitsmart.viewmodel.SettlementViewModel;

import java.util.List;

/**
 * Record or edit a payment between the user and another member (FR 5.1 to FR 5.3).
 */
public class SettlementFormActivity extends BaseActivity {
    private static final String EXTRA_GROUP_ID = "groupId";
    private static final String EXTRA_SETTLEMENT_ID = "settlementId";
    private static final String EXTRA_RECEIVER_ID = "receiverId";
    private static final String KEY_PREFILLED = "prefilled";

    private SettlementViewModel viewModel;
    private TextInputLayout receiverLayout;
    private TextInputLayout amountLayout;
    private TextInputLayout noteLayout;
    private TextInputEditText receiverInput;
    private TextInputEditText amountInput;
    private TextInputEditText noteInput;
    private TextView owedText;
    private Chip fullAmountChip;
    private MaterialButton saveButton;
    private boolean prefilled;
    private boolean busy;

    /** @param receiverId member to preselect (for example from the balance page), or null */
    public static Intent newCreateIntent(Context context, String groupId, String receiverId) {
        return new Intent(context, SettlementFormActivity.class)
                .putExtra(EXTRA_GROUP_ID, groupId)
                .putExtra(EXTRA_RECEIVER_ID, receiverId);
    }

    public static Intent newEditIntent(Context context, String groupId, String settlementId) {
        return new Intent(context, SettlementFormActivity.class)
                .putExtra(EXTRA_GROUP_ID, groupId)
                .putExtra(EXTRA_SETTLEMENT_ID, settlementId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSignedIn()) {
            return;
        }
        String groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        String settlementId = getIntent().getStringExtra(EXTRA_SETTLEMENT_ID);
        String receiverId = getIntent().getStringExtra(EXTRA_RECEIVER_ID);
        if (groupId == null) {
            finish();
            return;
        }
        setContentWithInsets(R.layout.activity_settlement_form);
        viewModel = obtainViewModel(SettlementViewModel.class);
        prefilled = savedInstanceState != null && savedInstanceState.getBoolean(KEY_PREFILLED);
        boolean editing = settlementId != null;

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(editing ? R.string.title_edit_settlement : R.string.title_record_settlement);
        toolbar.setNavigationOnClickListener(v -> finish());
        receiverLayout = findViewById(R.id.settlement_receiver_layout);
        amountLayout = findViewById(R.id.settlement_amount_layout);
        noteLayout = findViewById(R.id.settlement_note_layout);
        receiverInput = findViewById(R.id.settlement_receiver);
        amountInput = findViewById(R.id.settlement_amount);
        noteInput = findViewById(R.id.settlement_note);
        owedText = findViewById(R.id.settlement_owed_text);
        fullAmountChip = findViewById(R.id.settlement_full_amount);
        saveButton = findViewById(R.id.settlement_save_button);
        TextView dateText = findViewById(R.id.settlement_date);
        TextView loadErrorText = findViewById(R.id.settlement_load_error);
        ScrollView scrollView = findViewById(R.id.settlement_scroll);
        View progress = findViewById(R.id.settlement_progress);
        saveButton.setText(editing ? R.string.action_save_changes : R.string.action_record_settlement);

        receiverInput.setOnClickListener(v -> showReceiverPicker());
        receiverLayout.setEndIconOnClickListener(v -> showReceiverPicker());
        fullAmountChip.setOnClickListener(v -> {
            Long owed = viewModel.getOwedCents().getValue();
            if (owed != null && owed > 0) {
                amountInput.setText(Money.formatPlain(owed));
                amountInput.setSelection(amountInput.length());
            }
        });
        saveButton.setOnClickListener(v -> {
            UiUtils.hideKeyboard(saveButton);
            viewModel.save(UiUtils.textOf(amountInput), UiUtils.textOf(noteInput));
        });

        viewModel.getReady().observe(this, ready -> {
            if (Boolean.TRUE.equals(ready)) {
                scrollView.setVisibility(View.VISIBLE);
                renderOwed(viewModel.getOwedCents().getValue());
            }
        });
        viewModel.getLoadError().observe(this, error -> {
            if (error != null) {
                loadErrorText.setText(error);
                UiUtils.setVisible(loadErrorText, true);
                scrollView.setVisibility(View.GONE);
            }
        });
        viewModel.getLoading().observe(this, loading -> {
            busy = Boolean.TRUE.equals(loading);
            UiUtils.setVisible(progress, busy);
            renderOwed(viewModel.getOwedCents().getValue());
        });
        viewModel.getCounterparty().observe(this, uid -> receiverInput.setText(receiverName(uid)));
        viewModel.getOwedCents().observe(this, this::renderOwed);
        viewModel.getExistingSettlement().observe(this, this::prefill);
        viewModel.getDate().observe(this,
                date -> dateText.setText(getString(R.string.date_recorded_automatically, date)));
        viewModel.getFieldError().observe(this, this::showFieldError);
        viewModel.getSaved().observe(this, event -> {
            if (event.getContentIfNotHandled() != null) {
                Toast.makeText(this, editing ? R.string.settlement_updated
                        : R.string.settlement_recorded, Toast.LENGTH_SHORT).show();
                setResult(RESULT_OK);
                finish();
            }
        });
        observeMessages(viewModel);

        viewModel.init(groupId, settlementId, receiverId);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_PREFILLED, prefilled);
    }

    private void prefill(Settlement settlement) {
        if (settlement == null || prefilled) {
            return;
        }
        prefilled = true;
        amountInput.setText(Money.formatPlain(settlement.getAmountCents()));
        noteInput.setText(settlement.getNote());
    }

    /** Explains how much is owed; saving is only possible when something is owed (FR 5.2). */
    private void renderOwed(Long owedCents) {
        String receiverId = viewModel.getCounterparty().getValue();
        boolean canSave;
        if (receiverId == null) {
            owedText.setText(hasAnyDebt()
                    ? getString(R.string.settlement_choose_receiver)
                    : getString(R.string.settlement_owe_nobody));
            amountLayout.setHelperText(null);
            canSave = false;
        } else {
            long owed = owedCents == null ? 0 : owedCents;
            String name = receiverName(receiverId);
            if (owed > 0) {
                owedText.setText(getString(Boolean.TRUE.equals(viewModel.getIncoming().getValue())
                        ? R.string.settlement_they_owe : R.string.settlement_you_owe,
                        name, Money.format(owed)));
                amountLayout.setHelperText(getString(R.string.settlement_up_to, Money.format(owed)));
            } else {
                owedText.setText(getString(R.string.settlement_owe_nothing, name));
                amountLayout.setHelperText(null);
            }
            canSave = owed > 0;
        }
        saveButton.setEnabled(canSave && !busy);
        fullAmountChip.setEnabled(canSave);
    }

    private boolean hasAnyDebt() {
        List<ReceiverOption> options = viewModel.getReceivers().getValue();
        if (options != null) {
            for (ReceiverOption option : options) {
                if (option.getNetCents() != 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private String receiverName(String uid) {
        List<ReceiverOption> options = viewModel.getReceivers().getValue();
        if (uid != null && options != null) {
            for (ReceiverOption option : options) {
                if (option.getUser().getUid().equals(uid)) {
                    return option.getUser().getName();
                }
            }
        }
        return "";
    }

    private void showReceiverPicker() {
        List<ReceiverOption> options = viewModel.getReceivers().getValue();
        if (busy) {
            return;
        }
        if (options == null || options.isEmpty()) {
            showMessage(getString(R.string.settlement_no_other_members));
            return;
        }
        String current = viewModel.getCounterparty().getValue();
        String[] labels = new String[options.size()];
        int checked = -1;
        for (int i = 0; i < options.size(); i++) {
            ReceiverOption option = options.get(i);
            labels[i] = option.getNetCents() != 0
                    ? getString(option.getNetCents() > 0
                            ? R.string.settlement_option_receivable : R.string.settlement_option_owed,
                    option.getUser().getName(), Money.format(Math.abs(option.getNetCents())))
                    : getString(R.string.settlement_option_nothing, option.getUser().getName());
            if (option.getUser().getUid().equals(current)) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_who_did_you_pay)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    viewModel.selectMember(options.get(which).getUser().getUid());
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showFieldError(ValidationResult result) {
        receiverLayout.setError(null);
        amountLayout.setError(null);
        noteLayout.setError(null);
        if (result == null || result.isValid()) {
            return;
        }
        String field = result.getField();
        if (ValidationResult.FIELD_RECEIVER.equals(field)) {
            receiverLayout.setError(result.getErrorMessage());
        } else if (ValidationResult.FIELD_NOTE.equals(field)) {
            noteLayout.setError(result.getErrorMessage());
        } else {
            amountLayout.setError(result.getErrorMessage());
        }
    }
}
