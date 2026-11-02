package com.team04.splitsmart.ui;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.validation.ValidationResult;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.model.User;
import com.team04.splitsmart.viewmodel.GroupViewModel;

import java.util.List;

/**
 * The group's member list, visible to every member (FR 1.6). The owner can add registered
 * users by their USC email (FR 1.5), and can also make another member the owner or remove a
 * member who is settled up. Shares its {@link GroupViewModel} with the activity.
 */
public class MembersFragment extends BaseFragment implements Refreshable {
    private static final String ARG_GROUP_ID = "groupId";

    private GroupViewModel viewModel;
    private String groupId;
    private SwipeRefreshLayout refreshLayout;
    private View addCard;
    private View ownerOnlyNote;
    private TextView ownerOnlyNoteText;
    private TextView countTitle;
    private LinearLayout rows;
    private TextInputLayout emailLayout;
    private TextInputEditText emailInput;
    private MaterialButton addButton;

    public static MembersFragment newInstance(String groupId) {
        MembersFragment fragment = new MembersFragment();
        Bundle args = new Bundle();
        args.putString(ARG_GROUP_ID, groupId);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_members, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        groupId = requireArguments().getString(ARG_GROUP_ID);
        viewModel = obtainActivityViewModel(GroupViewModel.class);

        refreshLayout = view.findViewById(R.id.members_refresh);
        addCard = view.findViewById(R.id.members_add_card);
        ownerOnlyNote = view.findViewById(R.id.members_owner_only_note);
        ownerOnlyNoteText = (TextView) ownerOnlyNote;
        countTitle = view.findViewById(R.id.members_count_title);
        rows = view.findViewById(R.id.members_rows);
        emailLayout = view.findViewById(R.id.members_email_layout);
        emailInput = view.findViewById(R.id.members_email);
        addButton = view.findViewById(R.id.members_add_button);

        refreshLayout.setOnRefreshListener(this::refresh);
        addButton.setOnClickListener(v -> addMember());
        emailInput.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                addMember();
                return true;
            }
            return false;
        });

        viewModel.getGroup().observe(getViewLifecycleOwner(), this::renderGroup);
        viewModel.getMembers().observe(getViewLifecycleOwner(), this::renderMembers);
        viewModel.getLoading().observe(getViewLifecycleOwner(), loading -> {
            boolean busy = Boolean.TRUE.equals(loading);
            refreshLayout.setRefreshing(busy);
            addButton.setEnabled(!busy);
        });
        viewModel.getFieldError().observe(getViewLifecycleOwner(), result -> emailLayout.setError(
                result != null && !result.isValid()
                        && ValidationResult.FIELD_EMAIL.equals(result.getField())
                        ? result.getErrorMessage() : null));
        viewModel.getMemberAdded().observe(getViewLifecycleOwner(), event -> {
            String name = event.getContentIfNotHandled();
            if (name != null) {
                emailInput.setText("");
                emailLayout.setError(null);
                showMessage(getString(R.string.message_member_added, name));
            }
        });
    }

    @Override
    public void refresh() {
        if (viewModel != null && groupId != null) {
            viewModel.load(groupId);
        }
    }

    private void addMember() {
        UiUtils.hideKeyboard(emailInput);
        viewModel.addMember(UiUtils.textOf(emailInput));
    }

    private void renderGroup(Group group) {
        if (group == null) {
            return;
        }
        boolean owner = viewModel.isOwner();
        UiUtils.setVisible(addCard, owner);
        ownerOnlyNoteText.setText(owner ? R.string.members_manage_hint : R.string.members_owner_only);
        UiUtils.setVisible(ownerOnlyNote, !owner || group.getMemberCount() > 1);
        countTitle.setText(getResources().getQuantityString(R.plurals.member_count,
                group.getMemberCount(), group.getMemberCount()));
    }

    private void renderMembers(List<User> members) {
        rows.removeAllViews();
        if (members == null) {
            return;
        }
        Group group = viewModel.getGroup().getValue();
        String currentUid = viewModel.getCurrentUserId();
        boolean owner = viewModel.isOwner();
        LayoutInflater inflater = LayoutInflater.from(requireContext());
        for (User member : members) {
            View row = inflater.inflate(R.layout.item_member, rows, false);
            TextView avatar = row.findViewById(R.id.member_avatar);
            TextView name = row.findViewById(R.id.member_name);
            TextView email = row.findViewById(R.id.member_email);
            View ownerBadge = row.findViewById(R.id.member_owner_badge);
            UiUtils.bindAvatar(avatar, member);
            name.setText(member.getUid().equals(currentUid)
                    ? getString(R.string.member_you, member.getName()) : member.getName());
            email.setText(member.getUscEmail());
            UiUtils.setVisible(ownerBadge, group != null && group.isOwner(member.getUid()));
            View more = row.findViewById(R.id.member_more);
            boolean manageable = owner && !member.getUid().equals(currentUid);
            UiUtils.setVisible(more, manageable);
            if (manageable) {
                more.setOnClickListener(v -> showMemberOptions(member));
            }
            rows.addView(row);
        }
    }

    /** Owner only: "Make owner" or "Remove from group" for another member. */
    private void showMemberOptions(User member) {
        String[] options = {getString(R.string.action_make_owner),
                getString(R.string.action_remove_member)};
        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(member.getName())
                .setItems(options, (dialog, which) -> {
                    if (which == 0) {
                        new ConfirmDialog(requireContext(),
                                getString(R.string.confirm_transfer_title, member.getName()),
                                getString(R.string.confirm_transfer_message, member.getName()),
                                getString(R.string.action_transfer),
                                () -> viewModel.transferOwnership(member.getUid())).show();
                    } else {
                        new ConfirmDialog(requireContext(),
                                getString(R.string.confirm_remove_member_title, member.getName()),
                                getString(R.string.confirm_remove_member_message,
                                        member.getName()),
                                getString(R.string.action_remove),
                                () -> viewModel.removeMember(member.getUid())).show();
                    }
                })
                .show();
    }
}
