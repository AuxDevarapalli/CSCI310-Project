package com.team04.splitsmart.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.User;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/** Rows of the group history (FR 5.4). Tapping a row opens its details (FR 5.5). */
public class TransactionAdapter extends ListAdapter<Transaction, TransactionAdapter.Holder> {

    public interface OnTransactionClickListener {
        void onTransactionClick(Transaction transaction);
    }

    private static final DiffUtil.ItemCallback<Transaction> DIFF =
            new DiffUtil.ItemCallback<Transaction>() {
                @Override
                public boolean areItemsTheSame(@NonNull Transaction a, @NonNull Transaction b) {
                    return Objects.equals(a.getId(), b.getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull Transaction a, @NonNull Transaction b) {
                    return signature(a).equals(signature(b));
                }
            };

    private final String currentUid;
    private final OnTransactionClickListener listener;
    private Map<String, User> users = Collections.emptyMap();

    public TransactionAdapter(String currentUid, OnTransactionClickListener listener) {
        super(DIFF);
        this.currentUid = currentUid;
        this.listener = listener;
    }

    /** Member names used to describe who is involved. */
    public void setUsers(Map<String, User> users) {
        this.users = users == null ? Collections.emptyMap() : users;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_transaction, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position));
    }

    /** Everything that is drawn for a row, so edited transactions are re-drawn. */
    private static String signature(Transaction t) {
        StringBuilder builder = new StringBuilder()
                .append(t.getType()).append('|').append(t.getAmountCents()).append('|')
                .append(t.getDate()).append('|').append(t.getNote()).append('|')
                .append(t.getInvolvedUserIds());
        if (t instanceof Expense) {
            Expense e = (Expense) t;
            builder.append('|').append(e.getTitle()).append('|').append(e.getCategory())
                    .append('|').append(e.getShares());
        } else if (t instanceof Settlement) {
            builder.append('|').append(((Settlement) t).getReceiverId());
        }
        return builder.toString();
    }

    class Holder extends RecyclerView.ViewHolder {
        private final ImageView icon;
        private final TextView title;
        private final TextView amount;
        private final TextView type;
        private final TextView date;
        private final TextView people;
        private final TextView effect;

        Holder(View itemView) {
            super(itemView);
            icon = itemView.findViewById(R.id.tx_icon);
            title = itemView.findViewById(R.id.tx_title);
            amount = itemView.findViewById(R.id.tx_amount);
            type = itemView.findViewById(R.id.tx_type);
            date = itemView.findViewById(R.id.tx_date);
            people = itemView.findViewById(R.id.tx_people);
            effect = itemView.findViewById(R.id.tx_effect);
        }

        void bind(Transaction transaction) {
            Context context = itemView.getContext();
            UiUtils.bindTransactionIcon(icon, transaction);
            title.setText(UiUtils.titleOf(transaction));
            amount.setText(Money.format(transaction.getAmountCents()));
            type.setText(UiUtils.typeLabel(transaction));
            date.setText(transaction.getDate());
            people.setText(UiUtils.peopleSummary(transaction, users, currentUid));
            UiUtils.Effect result = UiUtils.effectFor(transaction, currentUid);
            effect.setText(result.text);
            effect.setTextColor(UiUtils.color(context, result.color));
            itemView.setOnClickListener(v -> listener.onTransactionClick(transaction));
        }
    }
}
