package com.team04.splitsmart.ui;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.snackbar.Snackbar;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Small view helpers shared by the screens. */
public final class UiUtils {
    private static final int[] AVATAR_COLORS = {
            R.color.avatar_1, R.color.avatar_2, R.color.avatar_3, R.color.avatar_4,
            R.color.avatar_5, R.color.avatar_6, R.color.avatar_7, R.color.avatar_8
    };

    private UiUtils() {
    }

    /**
     * Pads the root view so content is not drawn under the status bar, navigation bar or
     * keyboard. Apps targeting Android 15+ are always drawn edge to edge.
     */
    public static void applySystemBarInsets(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            view.setPadding(bars.left, bars.top, bars.right, Math.max(bars.bottom, ime.bottom));
            return WindowInsetsCompat.CONSUMED;
        });
    }

    public static void setVisible(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    public static String textOf(EditText editText) {
        return editText.getText() == null ? "" : editText.getText().toString();
    }

    public static void hideKeyboard(View view) {
        InputMethodManager imm = (InputMethodManager)
                view.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    public static void showSnackbar(View anchor, String message) {
        if (anchor != null && message != null) {
            Snackbar.make(anchor, message, Snackbar.LENGTH_LONG).show();
        }
    }

    public static int color(Context context, @ColorRes int colorRes) {
        return ContextCompat.getColor(context, colorRes);
    }

    // ---- Avatars and icons ----

    /** Up to two initials from any name, e.g. "Spring Break Trip" -> "ST". */
    public static String initialsOf(String name) {
        String trimmed = name == null ? "" : name.trim();
        if (trimmed.isEmpty()) {
            return "?";
        }
        String[] parts = trimmed.split("\\s+");
        StringBuilder initials = new StringBuilder();
        initials.append(Character.toUpperCase(parts[0].charAt(0)));
        if (parts.length > 1) {
            initials.append(Character.toUpperCase(parts[parts.length - 1].charAt(0)));
        }
        return initials.toString();
    }

    /** Shows initials on a colored circle; the same seed always gets the same color. */
    public static void bindAvatar(TextView avatar, String initials, String seed) {
        avatar.setText(initials);
        int index = Math.floorMod(seed == null ? 0 : seed.hashCode(), AVATAR_COLORS.length);
        avatar.setBackgroundTintList(
                ColorStateList.valueOf(color(avatar.getContext(), AVATAR_COLORS[index])));
    }

    public static void bindAvatar(TextView avatar, User user) {
        bindAvatar(avatar, user.getInitials(), user.getUid());
    }

    /** Shows an icon on a colored circle. */
    public static void bindIconBadge(ImageView view, @DrawableRes int icon, @ColorRes int background) {
        Context context = view.getContext();
        view.setImageResource(icon);
        view.setImageTintList(ColorStateList.valueOf(color(context, R.color.on_category)));
        view.setBackgroundTintList(ColorStateList.valueOf(color(context, background)));
    }

    @DrawableRes
    public static int iconFor(Category category) {
        switch (category) {
            case FOOD:
                return R.drawable.ic_category_food;
            case HOUSING:
                return R.drawable.ic_category_housing;
            case TRANSPORTATION:
                return R.drawable.ic_category_transportation;
            case ENTERTAINMENT:
                return R.drawable.ic_category_entertainment;
            default:
                return R.drawable.ic_category_other;
        }
    }

    @ColorRes
    public static int colorFor(Category category) {
        switch (category) {
            case FOOD:
                return R.color.category_food;
            case HOUSING:
                return R.color.category_housing;
            case TRANSPORTATION:
                return R.color.category_transportation;
            case ENTERTAINMENT:
                return R.color.category_entertainment;
            default:
                return R.color.category_other;
        }
    }

    /** Category icon for expenses, the swap icon for settlements. */
    public static void bindTransactionIcon(ImageView view, Transaction transaction) {
        if (transaction instanceof Expense) {
            Category category = ((Expense) transaction).getCategory();
            bindIconBadge(view, iconFor(category), colorFor(category));
        } else {
            bindIconBadge(view, R.drawable.ic_swap, R.color.settlement_accent);
        }
    }

    // ---- Names and descriptions ----

    /** "You" for the current user, otherwise the member's name. */
    public static String nameOf(Map<String, User> users, String uid, String currentUid) {
        if (uid != null && uid.equals(currentUid)) {
            return "You";
        }
        User user = users == null || uid == null ? null : users.get(uid);
        return user == null ? "Unknown user" : user.getName();
    }

    /** Like {@link #nameOf} but lower-case "you", for use in the middle of a sentence. */
    public static String nameInSentence(Map<String, User> users, String uid, String currentUid) {
        return uid != null && uid.equals(currentUid) ? "you" : nameOf(users, uid, currentUid);
    }

    /** "Expense · Food" or "Settlement" (FR 5.4: whether it is an expense or a settlement). */
    public static String typeLabel(Transaction transaction) {
        if (transaction instanceof Expense) {
            return transaction.getType().getDisplayName() + " · "
                    + ((Expense) transaction).getCategory().getDisplayName();
        }
        return transaction.getType().getDisplayName();
    }

    /** The title shown in lists: the expense title or the label "Settlement" (FR 5.4). */
    public static String titleOf(Transaction transaction) {
        if (transaction instanceof Expense) {
            return ((Expense) transaction).getTitle();
        }
        return transaction.getType().getDisplayName();
    }

    /** The users involved (FR 5.4), e.g. "Paid by You · for You, Bob, Carol". */
    public static String peopleSummary(Transaction transaction, Map<String, User> users,
                                       String currentUid) {
        if (transaction instanceof Expense) {
            Expense expense = (Expense) transaction;
            List<String> names = new ArrayList<>();
            for (String uid : expense.getParticipantIds()) {
                names.add(nameOf(users, uid, currentUid));
            }
            return "Paid by " + nameOf(users, expense.getPayerId(), currentUid)
                    + " · for " + String.join(", ", names);
        }
        Settlement settlement = (Settlement) transaction;
        return nameOf(users, settlement.getPayerId(), currentUid) + " paid "
                + nameInSentence(users, settlement.getReceiverId(), currentUid);
    }

    /** What a transaction means for the current user, with a color. Extra feature. */
    public static Effect effectFor(Transaction transaction, String currentUid) {
        if (currentUid == null) {
            return new Effect("", R.color.amount_neutral);
        }
        if (transaction instanceof Expense) {
            Expense expense = (Expense) transaction;
            if (currentUid.equals(expense.getPayerId())) {
                long lent = expense.getTotalCents() - expense.getShareFor(currentUid);
                return lent > 0
                        ? new Effect("You lent " + Money.format(lent), R.color.amount_positive)
                        : new Effect("You paid only for yourself", R.color.amount_neutral);
            }
            if (expense.isParticipant(currentUid)) {
                return new Effect("You borrowed " + Money.format(expense.getShareFor(currentUid)),
                        R.color.amount_negative);
            }
            return new Effect("You are not involved", R.color.amount_neutral);
        }
        Settlement settlement = (Settlement) transaction;
        if (currentUid.equals(settlement.getPayerId())) {
            return new Effect("You paid back " + Money.format(settlement.getAmountCents()),
                    R.color.amount_neutral);
        }
        if (currentUid.equals(settlement.getReceiverId())) {
            return new Effect("You received " + Money.format(settlement.getAmountCents()),
                    R.color.amount_neutral);
        }
        return new Effect("You are not involved", R.color.amount_neutral);
    }

    /** A short sentence plus the color resource to draw it in. */
    public static final class Effect {
        public final String text;
        @ColorRes
        public final int color;

        Effect(String text, @ColorRes int color) {
            this.text = text;
            this.color = color;
        }
    }
}
