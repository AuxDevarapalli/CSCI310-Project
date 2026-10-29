package com.team04.splitsmart.viewmodel;

import com.team04.splitsmart.model.User;

/**
 * One member's row on the expense form: whether they participate, what was typed for them
 * (exact amount or percentage), and the share preview (FR 3.6).
 */
public class ParticipantRow {
    private final User user;
    private final boolean payer;
    private final boolean currentUser;
    private final boolean checked;
    private final String inputText;
    private final Long previewCents;
    private final String inputError;

    public ParticipantRow(User user, boolean payer, boolean currentUser, boolean checked,
                          String inputText, Long previewCents, String inputError) {
        this.user = user;
        this.payer = payer;
        this.currentUser = currentUser;
        this.checked = checked;
        this.inputText = inputText == null ? "" : inputText;
        this.previewCents = previewCents;
        this.inputError = inputError;
    }

    public User getUser() {
        return user;
    }

    public String getUid() {
        return user.getUid();
    }

    /** The payer is always a participant and cannot be deselected (FR 2.6). */
    public boolean isPayer() {
        return payer;
    }

    public boolean isCurrentUser() {
        return currentUser;
    }

    public boolean isChecked() {
        return checked;
    }

    /** Text typed for this member in exact or percentage mode. */
    public String getInputText() {
        return inputText;
    }

    /** This member's share in cents, or {@code null} if it cannot be computed yet. */
    public Long getPreviewCents() {
        return previewCents;
    }

    /** Problem with this member's input, or {@code null}. */
    public String getInputError() {
        return inputError;
    }
}
