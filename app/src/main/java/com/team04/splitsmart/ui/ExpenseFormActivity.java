package com.team04.splitsmart.ui;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.TextViewCompat;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.SplitMethod;
import com.team04.splitsmart.model.User;
import com.team04.splitsmart.viewmodel.ExpenseViewModel;
import com.team04.splitsmart.viewmodel.ParticipantRow;
import com.team04.splitsmart.viewmodel.SplitStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Create or edit an expense (FR 2.1 to FR 2.9) with a live split preview (FR 3.1 to FR 3.6,
 * design Figure 4). The creation date is filled in automatically and cannot be edited.
 */
public class ExpenseFormActivity extends BaseActivity {
    private static final String EXTRA_GROUP_ID = "groupId";
    private static final String EXTRA_EXPENSE_ID = "expenseId";
    private static final String KEY_PREFILLED = "prefilled";

    private ExpenseViewModel viewModel;
    private TextInputLayout titleLayout;
    private TextInputLayout amountLayout;
    private TextInputLayout payerLayout;
    private TextInputLayout noteLayout;
    private TextInputEditText titleInput;
    private TextInputEditText amountInput;
    private TextInputEditText payerInput;
    private TextInputEditText noteInput;
    private ChipGroup categoryGroup;
    private MaterialButtonToggleGroup splitGroup;
    private LinearLayout participantsContainer;
    private TextView splitStatusText;
    private MaterialButton fillEvenlyButton;
    private ScrollView scrollView;
    private final Map<String, ParticipantViews> participantViews = new LinkedHashMap<>();
    private boolean prefilled;
    /** True while views are updated from the ViewModel, so listeners do not echo back. */
    private boolean syncing;

    public static Intent newCreateIntent(Context context, String groupId) {
        return new Intent(context, ExpenseFormActivity.class).putExtra(EXTRA_GROUP_ID, groupId);
    }

    public static Intent newEditIntent(Context context, String groupId, String expenseId) {
        return newCreateIntent(context, groupId).putExtra(EXTRA_EXPENSE_ID, expenseId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSignedIn()) {
            return;
        }
        String groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        String expenseId = getIntent().getStringExtra(EXTRA_EXPENSE_ID);
        if (groupId == null) {
            finish();
            return;
        }
        setContentWithInsets(R.layout.activity_expense_form);
        viewModel = obtainViewModel(ExpenseViewModel.class);
        prefilled = savedInstanceState != null && savedInstanceState.getBoolean(KEY_PREFILLED);
        boolean editing = expenseId != null;

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setTitle(editing ? R.string.title_edit_expense : R.string.title_add_expense);
        toolbar.setNavigationOnClickListener(v -> finish());
        titleLayout = findViewById(R.id.expense_title_layout);
        amountLayout = findViewById(R.id.expense_amount_layout);
        payerLayout = findViewById(R.id.expense_payer_layout);
        noteLayout = findViewById(R.id.expense_note_layout);
        titleInput = findViewById(R.id.expense_title);
        amountInput = findViewById(R.id.expense_amount);
        payerInput = findViewById(R.id.expense_payer);
        noteInput = findViewById(R.id.expense_note);
        categoryGroup = findViewById(R.id.expense_category_group);
        splitGroup = findViewById(R.id.expense_split_group);
        participantsContainer = findViewById(R.id.expense_participants);
        splitStatusText = findViewById(R.id.expense_split_status);
        fillEvenlyButton = findViewById(R.id.expense_fill_evenly);
        scrollView = findViewById(R.id.expense_scroll);
        MaterialButton saveButton = findViewById(R.id.expense_save_button);
        TextView loadErrorText = findViewById(R.id.expense_load_error);
        TextView dateText = findViewById(R.id.expense_date);
        View progress = findViewById(R.id.expense_progress);
        saveButton.setText(editing ? R.string.action_save_changes : R.string.action_save_expense);

        amountInput.addTextChangedListener(new SimpleTextWatcher(text -> {
            if (!syncing) {
                viewModel.setTotal(text);
            }
        }));
        payerInput.setOnClickListener(v -> showPayerPicker());
        payerLayout.setEndIconOnClickListener(v -> showPayerPicker());
        splitGroup.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (isChecked && !syncing) {
                viewModel.setSplitMethod(methodFor(checkedId));
            }
        });
        findViewById(R.id.expense_select_all).setOnClickListener(
                v -> viewModel.selectAllParticipants());
        fillEvenlyButton.setOnClickListener(v -> viewModel.fillEvenly());
        saveButton.setOnClickListener(v -> {
            UiUtils.hideKeyboard(saveButton);
            viewModel.save(UiUtils.textOf(titleInput), selectedCategory(),
                    UiUtils.textOf(noteInput));
        });

        viewModel.getReady().observe(this, ready -> {
            if (Boolean.TRUE.equals(ready)) {
                scrollView.setVisibility(View.VISIBLE);
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
            boolean busy = Boolean.TRUE.equals(loading);
            UiUtils.setVisible(progress, busy);
            saveButton.setEnabled(!busy);
        });
        viewModel.getMembers().observe(this, this::buildParticipantRows);
        viewModel.getExistingExpense().observe(this, this::prefill);
        viewModel.getPayer().observe(this, this::renderPayer);
        viewModel.getSplitMethod().observe(this, this::renderSplitMethod);
        viewModel.getRows().observe(this, this::renderRows);
        viewModel.getSplitStatus().observe(this, this::renderStatus);
        viewModel.getAmountError().observe(this, amountLayout::setError);
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

        viewModel.init(groupId, expenseId);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_PREFILLED, prefilled);
    }

    // ---- Rendering ----

    /** Fills the form once with the expense being edited. */
    private void prefill(Expense expense) {
        if (expense == null || prefilled) {
            return;
        }
        prefilled = true;
        titleInput.setText(expense.getTitle());
        syncing = true;
        amountInput.setText(Money.formatPlain(expense.getTotalCents()));
        syncing = false;
        noteInput.setText(expense.getNote());
        categoryGroup.check(chipFor(expense.getCategory()));
    }

    private void renderPayer(String payerId) {
        List<User> members = viewModel.getMembers().getValue();
        if (members == null || payerId == null) {
            return;
        }
        for (User member : members) {
            if (member.getUid().equals(payerId)) {
                payerInput.setText(displayName(member));
            }
        }
    }

    private void renderSplitMethod(SplitMethod method) {
        syncing = true;
        int buttonId = buttonFor(method);
        if (splitGroup.getCheckedButtonId() != buttonId) {
            splitGroup.check(buttonId);
        }
        syncing = false;
        UiUtils.setVisible(fillEvenlyButton, method != SplitMethod.EQUAL);
    }

    private void buildParticipantRows(List<User> members) {
        if (members == null) {
            return;
        }
        participantsContainer.removeAllViews();
        participantViews.clear();
        LayoutInflater inflater = getLayoutInflater();
        for (User member : members) {
            View row = inflater.inflate(R.layout.item_participant, participantsContainer, false);
            // Every row reuses the same view ids, so Android must not save/restore their
            // state; the ViewModel already keeps each member's input across rotation.
            row.setSaveFromParentEnabled(false);
            ParticipantViews views = new ParticipantViews(row);
            String uid = member.getUid();
            views.check.setText(displayName(member));
            views.check.setOnCheckedChangeListener((button, checked) -> {
                if (!syncing) {
                    viewModel.toggleParticipant(uid, checked);
                }
            });
            views.input.addTextChangedListener(new SimpleTextWatcher(text -> {
                if (!syncing) {
                    viewModel.setSplitInput(uid, text);
                }
            }));
            participantsContainer.addView(row);
            participantViews.put(uid, views);
        }
    }

    /** Updates every participant row from the ViewModel (FR 3.6 share preview). */
    private void renderRows(List<ParticipantRow> rows) {
        if (rows == null) {
            return;
        }
        SplitMethod method = viewModel.getSplitMethod().getValue();
        boolean showInput = method != null && method != SplitMethod.EQUAL;
        syncing = true;
        for (ParticipantRow row : rows) {
            ParticipantViews views = participantViews.get(row.getUid());
            if (views == null) {
                continue;
            }
            views.check.setChecked(row.isChecked());
            // FR 2.6: the payer always participates and cannot be deselected.
            views.check.setEnabled(!row.isPayer());
            UiUtils.setVisible(views.payerBadge, row.isPayer());
            UiUtils.setVisible(views.inputLayout, showInput);
            if (showInput) {
                views.inputLayout.setEnabled(row.isChecked());
                views.inputLayout.setPrefixText(method == SplitMethod.EXACT ? "$" : null);
                views.inputLayout.setSuffixText(method == SplitMethod.PERCENTAGE ? "%" : null);
                if (!UiUtils.textOf(views.input).equals(row.getInputText())) {
                    views.input.setText(row.getInputText());
                    views.input.setSelection(views.input.length());
                }
            }
            views.share.setText(row.isChecked() && row.getPreviewCents() != null
                    ? Money.format(row.getPreviewCents()) : getString(R.string.share_none));
            views.share.setAlpha(row.isChecked() ? 1f : 0.5f);
            UiUtils.setVisible(views.error, row.getInputError() != null);
            views.error.setText(row.getInputError());
        }
        syncing = false;
    }

    private void renderStatus(SplitStatus status) {
        if (status == null) {
            return;
        }
        splitStatusText.setText(status.getMessage());
        boolean neutral = UiUtils.textOf(amountInput).trim().isEmpty();
        int color;
        int icon;
        if (status.isValid()) {
            color = R.color.amount_positive;
            icon = R.drawable.ic_check_circle;
        } else if (neutral) {
            color = R.color.amount_neutral;
            icon = R.drawable.ic_info;
        } else {
            color = R.color.amount_negative;
            icon = R.drawable.ic_info;
        }
        int resolved = UiUtils.color(this, color);
        splitStatusText.setTextColor(resolved);
        splitStatusText.setCompoundDrawablesRelativeWithIntrinsicBounds(icon, 0, 0, 0);
        TextViewCompat.setCompoundDrawableTintList(splitStatusText,
                ColorStateList.valueOf(resolved));
    }

    private void showFieldError(ValidationResult result) {
        titleLayout.setError(null);
        payerLayout.setError(null);
        noteLayout.setError(null);
        amountLayout.setError(viewModel.getAmountError().getValue());
        if (result == null || result.isValid()) {
            return;
        }
        String field = result.getField();
        String message = result.getErrorMessage();
        if (ValidationResult.FIELD_TITLE.equals(field)) {
            titleLayout.setError(message);
            titleInput.requestFocus();
        } else if (ValidationResult.FIELD_AMOUNT.equals(field)) {
            amountLayout.setError(message);
            amountInput.requestFocus();
        } else if (ValidationResult.FIELD_PAYER.equals(field)) {
            payerLayout.setError(message);
        } else if (ValidationResult.FIELD_NOTE.equals(field)) {
            noteLayout.setError(message);
        } else {
            // Participants or split problems: point at the preview.
            showMessage(message);
            scrollView.post(() -> scrollView.smoothScrollTo(0, participantsContainer.getTop()));
        }
    }

    // ---- Pickers and mappings ----

    private void showPayerPicker() {
        List<User> members = viewModel.getMembers().getValue();
        if (members == null || members.isEmpty()) {
            return;
        }
        String payerId = viewModel.getPayer().getValue();
        String[] names = new String[members.size()];
        int checked = -1;
        for (int i = 0; i < members.size(); i++) {
            names[i] = displayName(members.get(i));
            if (members.get(i).getUid().equals(payerId)) {
                checked = i;
            }
        }
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.title_who_paid)
                .setSingleChoiceItems(names, checked, (dialog, which) -> {
                    viewModel.setPayer(members.get(which).getUid());
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private String displayName(User member) {
        return member.getUid().equals(viewModel.getCurrentUserId())
                ? getString(R.string.member_you, member.getName()) : member.getName();
    }

    private Category selectedCategory() {
        int id = categoryGroup.getCheckedChipId();
        if (id == R.id.chip_category_food) {
            return Category.FOOD;
        }
        if (id == R.id.chip_category_housing) {
            return Category.HOUSING;
        }
        if (id == R.id.chip_category_transportation) {
            return Category.TRANSPORTATION;
        }
        if (id == R.id.chip_category_entertainment) {
            return Category.ENTERTAINMENT;
        }
        return Category.OTHER;
    }

    private static int chipFor(Category category) {
        switch (category) {
            case FOOD:
                return R.id.chip_category_food;
            case HOUSING:
                return R.id.chip_category_housing;
            case TRANSPORTATION:
                return R.id.chip_category_transportation;
            case ENTERTAINMENT:
                return R.id.chip_category_entertainment;
            default:
                return R.id.chip_category_other;
        }
    }

    private static SplitMethod methodFor(int buttonId) {
        if (buttonId == R.id.split_exact) {
            return SplitMethod.EXACT;
        }
        if (buttonId == R.id.split_percent) {
            return SplitMethod.PERCENTAGE;
        }
        return SplitMethod.EQUAL;
    }

    private static int buttonFor(SplitMethod method) {
        if (method == SplitMethod.EXACT) {
            return R.id.split_exact;
        }
        if (method == SplitMethod.PERCENTAGE) {
            return R.id.split_percent;
        }
        return R.id.split_equal;
    }

    /** Views of one row in item_participant.xml. */
    private static final class ParticipantViews {
        final CheckBox check;
        final TextView payerBadge;
        final TextInputLayout inputLayout;
        final TextInputEditText input;
        final TextView share;
        final TextView error;

        ParticipantViews(View row) {
            check = row.findViewById(R.id.participant_check);
            payerBadge = row.findViewById(R.id.participant_payer_badge);
            inputLayout = row.findViewById(R.id.participant_input_layout);
            input = row.findViewById(R.id.participant_input);
            share = row.findViewById(R.id.participant_share);
            error = row.findViewById(R.id.participant_error);
        }
    }
}
