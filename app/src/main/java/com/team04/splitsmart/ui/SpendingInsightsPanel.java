package com.team04.splitsmart.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.history.SpendingInsights;
import com.team04.splitsmart.logic.validation.DateProvider;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/** Binds the selected month's extra insights and its expense drill-down. */
final class SpendingInsightsPanel {
    private final View root;
    private final Context context;
    private final TextView paid;
    private final TextView share;
    private static final DateTimeFormatter SHORT_MONTH =
            DateTimeFormatter.ofPattern("MMM", Locale.US);
    private final TextView trendAmount;
    private final TextView trendPeriod;
    private final TextView change;
    private final LinearLayout trend;
    private final View browse;
    private final Consumer<Expense> openExpense;
    private final Consumer<String> selectMonth;

    SpendingInsightsPanel(View root, Consumer<Expense> openExpense, Consumer<String> selectMonth) {
        this.root = root;
        context = root.getContext();
        this.openExpense = openExpense;
        this.selectMonth = selectMonth;
        paid = root.findViewById(R.id.insights_paid);
        share = root.findViewById(R.id.insights_share);
        trendAmount = root.findViewById(R.id.trend_selected_amount);
        trendPeriod = root.findViewById(R.id.trend_selected_period);
        change = root.findViewById(R.id.insights_change);
        trend = root.findViewById(R.id.insights_trend);
        browse = root.findViewById(R.id.insights_browse);
    }

    void render(SpendingInsights insights) {
        if (insights == null) {
            root.setVisibility(View.GONE);
            return;
        }
        root.setVisibility(View.VISIBLE);
        paid.setText(Money.format(insights.getPaidCents()));
        share.setText(Money.format(insights.getShareCents()));

        long delta = insights.getChangeFromPreviousMonthCents();
        if (delta == 0) {
            change.setText(R.string.insights_no_change);
        } else {
            int text = delta > 0 ? R.string.insights_spent_more : R.string.insights_spent_less;
            change.setText(context.getString(text, Money.format(Math.abs(delta))));
        }

        renderTrend(insights);
        boolean hasExpenses = !insights.getExpenses(null).isEmpty();
        browse.setEnabled(hasExpenses);
        browse.setOnClickListener(v -> showExpenses(context, insights, null, openExpense));
    }

    private void renderTrend(SpendingInsights insights) {
        trendAmount.setText(Money.format(insights.getTrend().get(insights.getMonth())));
        trendPeriod.setText(DateProvider.formatMonthLabel(insights.getMonth()));
        LayoutInflater inflater = LayoutInflater.from(context);
        long maximum = Collections.max(insights.getTrend().values());

        int index = 0;
        for (Map.Entry<String, Long> entry : insights.getTrend().entrySet()) {
            String month = entry.getKey();
            boolean selected = month.equals(insights.getMonth());
            long cents = entry.getValue();
            View row = trend.getChildAt(index++);
            if (row == null) {
                row = inflater.inflate(R.layout.item_spending_month, trend, false);
                trend.addView(row);
            }
            TextView label = row.findViewById(R.id.trend_month);
            TrendBar bar = row.findViewById(R.id.trend_progress);
            String monthLabel = DateProvider.formatMonthLabel(month);
            String amountLabel = Money.format(cents);
            label.setText(YearMonth.parse(month).format(SHORT_MONTH));
            label.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);
            label.setTextColor(MaterialColors.getColor(label, selected
                    ? androidx.appcompat.R.attr.colorPrimary
                    : com.google.android.material.R.attr.colorOnSurfaceVariant));
            bar.setValue(maximum == 0 ? 0 : (float) ((double) cents / maximum), selected,
                    root.isAttachedToWindow());
            row.setSelected(selected);
            row.setContentDescription(context.getString(selected
                    ? R.string.insights_selected_month_accessibility
                    : R.string.insights_month_accessibility, monthLabel, amountLabel));
            row.setOnClickListener(v -> selectMonth.accept(month));
        }
    }

    static void showExpenses(Context context, SpendingInsights insights, Category category,
                             Consumer<Expense> openExpense) {
        List<Expense> expenses = insights.getExpenses(category);
        String month = DateProvider.formatMonthLabel(insights.getMonth());
        String title = category == null
                ? context.getString(R.string.insights_expenses_title, month)
                : context.getString(R.string.insights_category_title, category.getDisplayName(), month);
        MaterialAlertDialogBuilder dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setNegativeButton(R.string.action_cancel, null);
        if (expenses.isEmpty()) {
            dialog.setMessage(R.string.insights_no_expenses);
        } else {
            String[] labels = new String[expenses.size()];
            for (int i = 0; i < expenses.size(); i++) {
                Expense expense = expenses.get(i);
                labels[i] = context.getString(R.string.insights_expense_row, expense.getTitle(),
                        Money.format(expense.getTotalCents()), expense.getDate());
            }
            dialog.setItems(labels, (window, which) -> openExpense.accept(expenses.get(which)));
        }
        dialog.show();
    }
}
