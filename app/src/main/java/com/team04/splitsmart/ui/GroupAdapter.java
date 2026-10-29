package com.team04.splitsmart.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.viewmodel.GroupListItem;

/** Group cards on the home screen. */
public class GroupAdapter extends ListAdapter<GroupListItem, GroupAdapter.Holder> {

    public interface OnGroupClickListener {
        void onGroupClick(GroupListItem item);
    }

    private static final DiffUtil.ItemCallback<GroupListItem> DIFF =
            new DiffUtil.ItemCallback<GroupListItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull GroupListItem a, @NonNull GroupListItem b) {
                    return a.getGroup().getId().equals(b.getGroup().getId());
                }

                @Override
                public boolean areContentsTheSame(@NonNull GroupListItem a,
                                                  @NonNull GroupListItem b) {
                    return a.getGroup().equals(b.getGroup())
                            && a.isBalanceLoaded() == b.isBalanceLoaded()
                            && a.getTotalToPayCents() == b.getTotalToPayCents()
                            && a.getTotalToReceiveCents() == b.getTotalToReceiveCents();
                }
            };

    private final String currentUid;
    private final OnGroupClickListener listener;

    public GroupAdapter(String currentUid, OnGroupClickListener listener) {
        super(DIFF);
        this.currentUid = currentUid;
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_group, parent, false);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        holder.bind(getItem(position));
    }

    class Holder extends RecyclerView.ViewHolder {
        private final TextView avatar;
        private final TextView name;
        private final TextView description;
        private final TextView meta;
        private final TextView firstBalanceLine;
        private final TextView secondBalanceLine;

        Holder(View itemView) {
            super(itemView);
            avatar = itemView.findViewById(R.id.group_avatar);
            name = itemView.findViewById(R.id.group_name);
            description = itemView.findViewById(R.id.group_description);
            meta = itemView.findViewById(R.id.group_meta);
            firstBalanceLine = itemView.findViewById(R.id.group_balance_label);
            secondBalanceLine = itemView.findViewById(R.id.group_balance_amount);
        }

        void bind(GroupListItem item) {
            Context context = itemView.getContext();
            Group group = item.getGroup();
            UiUtils.bindAvatar(avatar, UiUtils.initialsOf(group.getName()), group.getId());
            name.setText(group.getName());
            description.setText(group.getDescription());
            UiUtils.setVisible(description, group.hasDescription());
            String members = context.getResources().getQuantityString(
                    R.plurals.member_count, group.getMemberCount(), group.getMemberCount());
            meta.setText(group.isOwner(currentUid)
                    ? context.getString(R.string.group_meta_owner, members)
                    : members);

            UiUtils.setVisible(secondBalanceLine, false);
            if (!item.isBalanceLoaded()) {
                firstBalanceLine.setText("");
            } else if (item.isSettled()) {
                firstBalanceLine.setText(R.string.balance_settled_short);
                firstBalanceLine.setTextColor(UiUtils.color(context, R.color.amount_neutral));
            } else {
                showLine(firstBalanceLine, item.getTotalToPayCents(), item.getTotalToReceiveCents());
                if (item.getTotalToPayCents() > 0 && item.getTotalToReceiveCents() > 0) {
                    UiUtils.setVisible(secondBalanceLine, true);
                    secondBalanceLine.setText(context.getString(R.string.balance_you_are_owed_short,
                            Money.format(item.getTotalToReceiveCents())));
                    secondBalanceLine.setTextColor(UiUtils.color(context, R.color.amount_positive));
                }
            }
            itemView.setOnClickListener(v -> listener.onGroupClick(item));
        }

        /** Shows what the user owes, or, if nothing, what they are owed. */
        private void showLine(TextView line, long toPay, long toReceive) {
            Context context = line.getContext();
            if (toPay > 0) {
                line.setText(context.getString(R.string.balance_you_owe_short, Money.format(toPay)));
                line.setTextColor(UiUtils.color(context, R.color.amount_negative));
            } else {
                line.setText(context.getString(R.string.balance_you_are_owed_short,
                        Money.format(toReceive)));
                line.setTextColor(UiUtils.color(context, R.color.amount_positive));
            }
        }
    }
}
