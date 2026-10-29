package com.team04.splitsmart.ui;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.google.android.material.appbar.MaterialToolbar;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Share;
import com.team04.splitsmart.model.SplitMethod;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.User;
import com.team04.splitsmart.viewmodel.TransactionDetailViewModel;

import java.util.Map;

/**
 * Complete information about one expense or settlement (FR 5.5). Every member can view it;
 * only its creator sees Edit and Delete (FR 2.7, FR 5.3), and deleting asks for confirmation
 * (FR 2.8).
 */
public class TransactionDetailActivity extends BaseActivity {
    private static final String EXTRA_GROUP_ID = "groupId";
    private static final String EXTRA_TRANSACTION_ID = "transactionId";

    private TransactionDetailViewModel viewModel;
    private MaterialToolbar toolbar;
    private String groupId;
    private String transactionId;

    private View content;
    private ImageView icon;
    private TextView title;
    private TextView type;
    private TextView amount;
    private TextView date;
    private TextView paidBy;
    private TextView splitMethod;
    private TextView sharesTitle;
    private LinearLayout shares;
    private TextView noteTitle;
    private TextView note;
    private TextView createdBy;
    private TextView permissionNote;

    public static Intent newIntent(Context context, String groupId, String transactionId) {
        return new Intent(context, TransactionDetailActivity.class)
                .putExtra(EXTRA_GROUP_ID, groupId)
                .putExtra(EXTRA_TRANSACTION_ID, transactionId);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!ensureSignedIn()) {
            return;
        }
        groupId = getIntent().getStringExtra(EXTRA_GROUP_ID);
        transactionId = getIntent().getStringExtra(EXTRA_TRANSACTION_ID);
        if (groupId == null || transactionId == null) {
            finish();
            return;
        }
        setContentWithInsets(R.layout.activity_transaction_detail);
        viewModel = obtainViewModel(TransactionDetailViewModel.class);

        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_edit) {
                openEditor();
                return true;
            }
            if (item.getItemId() == R.id.action_delete) {
                confirmDelete();
                return true;
            }
            return false;
        });
        content = findViewById(R.id.detail_content);
        icon = findViewById(R.id.detail_icon);
        title = findViewById(R.id.detail_title);
        type = findViewById(R.id.detail_type);
        amount = findViewById(R.id.detail_amount);
        date = findViewById(R.id.detail_date);
        paidBy = findViewById(R.id.detail_paid_by);
        splitMethod = findViewById(R.id.detail_split_method);
        sharesTitle = findViewById(R.id.detail_shares_title);
        shares = findViewById(R.id.detail_shares);
        noteTitle = findViewById(R.id.detail_note_title);
        note = findViewById(R.id.detail_note);
        createdBy = findViewById(R.id.detail_created_by);
        permissionNote = findViewById(R.id.detail_permission_note);
        TextView errorText = findViewById(R.id.detail_error);
        View progress = findViewById(R.id.detail_progress);

        viewModel.getTransaction().observe(this, transaction -> render());
        viewModel.getUsers().observe(this, users -> render());
        viewModel.getLoading().observe(this,
                loading -> UiUtils.setVisible(progress, Boolean.TRUE.equals(loading)));
        viewModel.getLoadError().observe(this, error -> {
            UiUtils.setVisible(errorText, error != null);
            errorText.setText(error);
            if (error != null) {
                content.setVisibility(View.GONE);
                setMenuVisible(false);
            }
        });
        viewModel.getDeleted().observe(this, event -> {
            if (event.getContentIfNotHandled() != null) {
                Transaction deleted = viewModel.getTransaction().getValue();
                Toast.makeText(this, deleted instanceof Settlement
                        ? R.string.message_settlement_deleted
                        : R.string.message_expense_deleted, Toast.LENGTH_SHORT).show();
                finish();
            }
        });
        observeMessages(viewModel);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (viewModel != null) {
            // Reload so changes made in the edit form (or by another member) show up.
            viewModel.load(groupId, transactionId);
        }
    }

    private void setMenuVisible(boolean visible) {
        Menu menu = toolbar.getMenu();
        menu.findItem(R.id.action_edit).setVisible(visible);
        menu.findItem(R.id.action_delete).setVisible(visible);
    }

    private void render() {
        Transaction transaction = viewModel.getTransaction().getValue();
        Map<String, User> users = viewModel.getUsers().getValue();
        if (transaction == null || users == null) {
            return;
        }
        String me = viewModel.getCurrentUserId();
        content.setVisibility(View.VISIBLE);
        setMenuVisible(viewModel.canEdit());

        UiUtils.bindTransactionIcon(icon, transaction);
        title.setText(UiUtils.titleOf(transaction));
        type.setText(UiUtils.typeLabel(transaction));
        amount.setText(Money.format(transaction.getAmountCents()));
        date.setText(getString(R.string.detail_created_on, transaction.getDate()));
        shares.removeAllViews();

        if (transaction instanceof Expense) {
            toolbar.setTitle(R.string.title_expense_details);
            renderExpense((Expense) transaction, users, me);
        } else {
            toolbar.setTitle(R.string.title_settlement_details);
            renderSettlement((Settlement) transaction, users, me);
        }

        UiUtils.setVisible(noteTitle, transaction.hasNote());
        UiUtils.setVisible(note, transaction.hasNote());
        note.setText(transaction.getNote());
        String creator = UiUtils.nameOf(users, transaction.getCreatorId(), me);
        createdBy.setText(getString(R.string.detail_recorded_by, creator, transaction.getDate()));
        boolean canEdit = viewModel.canEdit();
        UiUtils.setVisible(permissionNote, !canEdit);
        if (!canEdit) {
            permissionNote.setText(getString(transaction instanceof Expense
                    ? R.string.detail_only_creator_expense
                    : R.string.detail_only_creator_settlement, creator));
        }
    }

    private void renderExpense(Expense expense, Map<String, User> users, String me) {
        String payer = UiUtils.nameOf(users, expense.getPayerId(), me);
        paidBy.setText(getString(R.string.detail_paid_by, payer));
        UiUtils.setVisible(splitMethod, true);
        splitMethod.setText(getString(R.string.detail_split_summary,
                expense.getSplitMethod().getDisplayName(), expense.getShares().size()));
        sharesTitle.setText(R.string.section_shares);
        LayoutInflater inflater = getLayoutInflater();
        for (Share share : expense.getShares()) {
            View row = inflater.inflate(R.layout.item_share_detail, shares, false);
            String uid = share.getParticipantId();
            String name = UiUtils.nameOf(users, uid, me);
            String detail;
            if (uid.equals(expense.getPayerId())) {
                detail = getString(R.string.detail_share_payer);
            } else {
                detail = getString(R.string.detail_share_owes,
                        UiUtils.nameInSentence(users, expense.getPayerId(), me),
                        Money.format(share.getAmountCents()));
            }
            if (expense.getSplitMethod() == SplitMethod.PERCENTAGE) {
                detail = Money.formatPercent(share.getPercentBasisPoints()) + " · " + detail;
            }
            bindShareRow(row, users.get(uid), name, detail, Money.format(share.getAmountCents()));
            shares.addView(row);
        }
    }

    private void renderSettlement(Settlement settlement, Map<String, User> users, String me) {
        String payer = UiUtils.nameOf(users, settlement.getPayerId(), me);
        String receiver = UiUtils.nameInSentence(users, settlement.getReceiverId(), me);
        paidBy.setText(getString(R.string.detail_settlement_paid, payer, receiver));
        UiUtils.setVisible(splitMethod, true);
        splitMethod.setText(R.string.detail_settlement_explanation);
        sharesTitle.setText(R.string.section_people);
        String formatted = Money.format(settlement.getAmountCents());

        View from = getLayoutInflater().inflate(R.layout.item_share_detail, shares, false);
        bindShareRow(from, users.get(settlement.getPayerId()), payer,
                getString(R.string.detail_settlement_from), formatted);
        shares.addView(from);
        View to = getLayoutInflater().inflate(R.layout.item_share_detail, shares, false);
        bindShareRow(to, users.get(settlement.getReceiverId()),
                UiUtils.nameOf(users, settlement.getReceiverId(), me),
                getString(R.string.detail_settlement_to), formatted);
        shares.addView(to);
    }

    private void bindShareRow(View row, User user, String name, String detail, String amountText) {
        TextView avatar = row.findViewById(R.id.share_avatar);
        if (user != null) {
            UiUtils.bindAvatar(avatar, user);
        } else {
            UiUtils.bindAvatar(avatar, "?", name);
        }
        ((TextView) row.findViewById(R.id.share_name)).setText(name);
        ((TextView) row.findViewById(R.id.share_detail)).setText(detail);
        ((TextView) row.findViewById(R.id.share_amount)).setText(amountText);
    }

    private void openEditor() {
        Transaction transaction = viewModel.getTransaction().getValue();
        if (transaction == null || !viewModel.canEdit()) {
            return;
        }
        if (transaction instanceof Expense) {
            startActivity(ExpenseFormActivity.newEditIntent(this, groupId, transactionId));
        } else {
            startActivity(SettlementFormActivity.newEditIntent(this, groupId, transactionId));
        }
    }

    private void confirmDelete() {
        Transaction transaction = viewModel.getTransaction().getValue();
        if (transaction == null) {
            return;
        }
        boolean expense = transaction instanceof Expense;
        new ConfirmDialog(this,
                getString(expense ? R.string.confirm_delete_expense_title
                        : R.string.confirm_delete_settlement_title),
                getString(expense ? R.string.confirm_delete_expense_message
                        : R.string.confirm_delete_settlement_message),
                getString(R.string.action_delete),
                viewModel::deleteTransaction).show();
    }
}
