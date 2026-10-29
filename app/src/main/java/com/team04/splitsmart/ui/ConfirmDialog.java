package com.team04.splitsmart.ui;

import android.content.Context;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.team04.splitsmart.R;

/**
 * Asks before a destructive action such as deleting an expense, a settlement or a group
 * (FR 1.7, FR 2.8, FR 5.3). The action only runs if the user confirms.
 */
public class ConfirmDialog {
    private final Context context;
    private final String title;
    private final String message;
    private final String confirmLabel;
    private final Runnable onConfirm;

    public ConfirmDialog(Context context, String title, String message, String confirmLabel,
                         Runnable onConfirm) {
        this.context = context;
        this.title = title;
        this.message = message;
        this.confirmLabel = confirmLabel;
        this.onConfirm = onConfirm;
    }

    public void show() {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(R.string.action_cancel, null)
                .setPositiveButton(confirmLabel, (dialog, which) -> onConfirm.run())
                .show();
    }
}
