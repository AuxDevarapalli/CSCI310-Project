package com.team04.splitsmart.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.Chip;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.User;
import com.team04.splitsmart.viewmodel.MemberOption;
import com.team04.splitsmart.viewmodel.SettlementViewModel;

import java.util.List;

/**
 * Record or edit a settlement: a payment made outside the app between the user and another
 * member (FR 5.1 to FR 5.3, design Figure 6). Either side can record it: "I paid" when the user
 * paid someone back, "I was paid" when someone paid the user. The amount cannot exceed what the
 * payer owes the receiver.
 */
public class SettlementFormActivity extends BaseActivity {
    private static final String EXTRA_GROUP_ID = "groupId";
    private static final String EXTRA_SETTLEMENT_ID = "settlementId";
    private static final String EXTRA_RECEIVER_ID = "receiverId";
    private static final String EXTRA_PAYER_ID = "payerId";
    private static final String KEY_PREFILLED = "prefilled";

    private SettlementViewModel viewModel;
    private MaterialButtonToggleGroup directionGroup;
    private TextView introText;
    private TextInputLayout payerLayout;
    private TextInputLayout receiverLayout;
    private TextInputLayout amountLayout;
    private TextInputLayout noteLayout;
    private TextInputEditText payerInput;
    private TextInputEditText receiverInput;
    private TextInputEditText amountInput;
    private TextInputEditText noteInput;
    private TextView owedText;
    private Chip fullAmountChip;
    private MaterialButton saveButton;
    private boolean prefilled;
    private boolean busy;
    /** True while the direction buttons are updated from the ViewModel, so they do not echo. */
    private boolean renderingDirection;

    /**
     * Opens the form for a payment the user made.
     *
     * @param receiverId member to preselect (for example from the balance page), or null to let
     *                   the form choose the direction and member
     */
    public static Intent newCreateIntent(Context context, String groupId, String receiverId) {
        return new Intent(context, SettlementFormActivity.class)
                .putExtra(EXTRA_GROUP_ID, groupId)
                .putExtra(EXTRA_RECEIVER_ID, receiverId);
    }

    /** Opens the form for a payment {@code payerId} made to the user. */
    public static Intent newReceivedIntent(Context context, String groupId, String payerId) {
        return new Intent(context, SettlementFormActivity.class)
                .putExtra(EXTRA_GROUP_ID, groupId)
                .putExtra(EXTRA_PAYER_ID, payerId);
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
        String payerId = getIntent().getStringExtra(EXTRA_PAYER_ID);
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
        directionGroup = findViewById(R.id.settlement_direction_group);
        introText = findViewById(R.id.settlement_intro);
        payerLayout = findViewById(R.id.settlement_payer_layout);
        receiverLayout = findViewById(R.id.settlement_receiver_layout);
        amountLayout = findViewById(R.id.settlement_amount_layout);
        noteLayout = findViewById(R.id.settlement_note_layout);
        payerInput = findViewById(R.id.settlement_payer);
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

        // Only the side that is not the logged-in user is enabled, so only it opens the picker.
        View.OnClickListener pickMember = v -> showMemberPicker();
        payerInput.setOnClickListener(pickMember);
        receiverInput.setOnClickListener(pickMember);
        payerLayout.setEndIconOnClickListener(pickMember);
        receiverLayout.setEndIconOnClickListener(pickMember);
        directionGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked && !renderingDirection) {
                viewModel.setReceived(checkedId == R.id.settlement_direction_received);
            }
        });
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
        viewModel.getReceived().observe(this, this::renderDirection);
        viewModel.getCurrentUser().observe(this, user -> renderNames());
        viewModel.getOptions().observe(this, options -> renderNames());
        viewModel.getOther().observe(this, uid -> renderNames());
        viewModel.getOwedCents().observe(this, this::renderOwed);
        viewModel.getExistingSettlement().observe(this, this::prefill);
        viewModel.getDate().observe(this,
                date -> dateText.setText(getString(R.string.date_recorded_automatically, date)));
        viewModel.getFieldError().observe(this, this::showFieldError);
        viewModel.getSaved().observe(this, event -> {
            if (event.getContentIfNotHandled() != null) {
                setResult(RESULT_OK);
                finish();
            }
        });
        observeMessages(viewModel);

        if (payerId != null) {
            viewModel.init(groupId, settlementId, payerId, Boolean.TRUE);
        } else {
            viewModel.init(groupId, settlementId, receiverId,
                    receiverId == null ? null : Boolean.FALSE);
        }
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

    /** Swaps which side is the logged-in user and which side opens the member picker. */
    private void renderDirection(Boolean value) {
        boolean received = Boolean.TRUE.equals(value);
        renderingDirection = true;
        int button = received ? R.id.settlement_direction_received : R.id.settlement_direction_paid;
        if (directionGroup.getCheckedButtonId() != button) {
            directionGroup.check(button);
        }
        renderingDirection = false;
        introText.setText(received ? R.string.settlement_intro_received : R.string.settlement_intro);
        fullAmountChip.setText(received
                ? R.string.action_received_full_amount : R.string.action_pay_full_amount);
        bindSide(payerLayout, received);
        bindSide(receiverLayout, !received);
        payerLayout.setError(null);
        receiverLayout.setError(null);
        renderNames();
        renderOwed(viewModel.getOwedCents().getValue());
    }

    private static void bindSide(TextInputLayout layout, boolean picker) {
        layout.setEnabled(picker);
        layout.setEndIconVisible(picker);
    }

    /** "Alice (you)" on the user's side, the chosen member on the other side. */
    private void renderNames() {
        User me = viewModel.getCurrentUser().getValue();
        String meText = me == null ? "" : getString(R.string.member_you, me.getName());
        String otherText = memberName(viewModel.getOther().getValue());
        boolean received = viewModel.isReceived();
        payerInput.setText(received ? otherText : meText);
        receiverInput.setText(received ? meText : otherText);
    }

    /** Explains how much is owed; saving is only possible when something is owed (FR 5.2). */
    private void renderOwed(Long owedCents) {
        boolean received = viewModel.isReceived();
        String otherId = viewModel.getOther().getValue();
        boolean canSave;
        if (otherId == null) {
            if (hasAnyDebt()) {
                owedText.setText(received
                        ? R.string.settlement_choose_payer : R.string.settlement_choose_receiver);
            } else {
                owedText.setText(received
                        ? R.string.settlement_owed_by_nobody : R.string.settlement_owe_nobody);
            }
            amountLayout.setHelperText(null);
            canSave = false;
        } else {
            long owed = owedCents == null ? 0 : owedCents;
            String name = memberName(otherId);
            if (owed > 0) {
                owedText.setText(getString(received
                        ? R.string.settlement_owes_you : R.string.settlement_you_owe,
                        name, Money.format(owed)));
                amountLayout.setHelperText(getString(R.string.settlement_up_to, Money.format(owed)));
            } else {
                owedText.setText(getString(received
                        ? R.string.settlement_owes_you_nothing : R.string.settlement_owe_nothing,
                        name));
                amountLayout.setHelperText(null);
            }
            canSave = owed > 0;
        }
        saveButton.setEnabled(canSave && !busy);
        fullAmountChip.setEnabled(canSave);
    }

    private boolean hasAnyDebt() {
        List<MemberOption> options = viewModel.getOptions().getValue();
        if (options != null) {
            for (MemberOption option : options) {
                if (option.getOwedCents() > 0) {
                    return true;
                }
            }
        }
        return false;
    }

    private String memberName(String uid) {
        List<MemberOption> options = viewModel.getOptions().getValue();
        if (uid != null && options != null) {
            for (MemberOption option : options) {
                if (option.getUser().getUid().equals(uid)) {
                    return option.getUser().getName();
                }
            }
        }
        return "";
    }

    private void showMemberPicker() {
        List<MemberOption> options = viewModel.getOptions().getValue();
        if (options == null || options.isEmpty()) {
            showMessage(getString(R.string.settlement_no_other_members));
            return;
        }
        boolean received = viewModel.isReceived();
        String current = viewModel.getOther().getValue();
        String[] labels = new String[options.size()];
        int checked = -1;
        for (int i = 0; i < options.size(); i++) {
            MemberOption option = options.get(i);
            String name = option.getUser().getName();
            if (option.getOwedCents() > 0) {
                labels[i] = getString(received
                        ? R.string.settlement_option_owes_you : R.string.settlement_option_owed,
                        name, Money.format(option.getOwedCents()));
            } else {
                labels[i] = getString(R.string.settlement_option_nothing, name);
            }
            if (option.getUser().getUid().equals(current)) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(received ? R.string.title_who_paid_you : R.string.title_who_did_you_pay)
                .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                    viewModel.selectOther(options.get(which).getUser().getUid());
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void showFieldError(ValidationResult result) {
        payerLayout.setError(null);
        receiverLayout.setError(null);
        amountLayout.setError(null);
        noteLayout.setError(null);
        if (result == null || result.isValid()) {
            return;
        }
        String field = result.getField();
        if (ValidationResult.FIELD_RECEIVER.equals(field)) {
            // FIELD_RECEIVER marks the member picker, whichever side it is on.
            (viewModel.isReceived() ? payerLayout : receiverLayout)
                    .setError(result.getErrorMessage());
        } else if (ValidationResult.FIELD_NOTE.equals(field)) {
            noteLayout.setError(result.getErrorMessage());
        } else {
            amountLayout.setError(result.getErrorMessage());
        }
    }
}
