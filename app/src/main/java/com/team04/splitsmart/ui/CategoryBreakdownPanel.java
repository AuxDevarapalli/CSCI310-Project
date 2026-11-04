package com.team04.splitsmart.ui;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.history.MonthlySummary;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.model.Category;

import java.util.EnumMap;
import java.util.Map;

/** Owns category row presentation; the fragment handles selection and navigation. */
final class CategoryBreakdownPanel {
    interface OnCategorySelected {
        void onSelected(Category category);
    }

    private final Map<Category, Row> rows = new EnumMap<>(Category.class);

    CategoryBreakdownPanel(LinearLayout container, OnCategorySelected onSelected) {
        container.removeAllViews();
        LayoutInflater inflater = LayoutInflater.from(container.getContext());
        for (Category category : Category.values()) {
            View view = inflater.inflate(R.layout.item_summary_category, container, false);
            Row row = new Row(view, category);
            view.setOnClickListener(v -> onSelected.onSelected(category));
            view.setFocusable(true);
            container.addView(view);
            rows.put(category, row);
        }
    }

    void render(MonthlySummary summary) {
        if (summary == null) {
            return;
        }
        for (Map.Entry<Category, Row> entry : rows.entrySet()) {
            entry.getValue().render(summary.getCategoryTotal(entry.getKey()), summary.getTotalCents());
        }
    }

    /** Cache child views once instead of searching the hierarchy on every summary update. */
    private static final class Row {
        private final View view;
        private final TextView amountText;
        private final TextView percentText;
        private final LinearProgressIndicator progress;

        Row(View view, Category category) {
            this.view = view;
            amountText = view.findViewById(R.id.category_amount);
            percentText = view.findViewById(R.id.category_percent);
            progress = view.findViewById(R.id.category_progress);
            ImageView icon = view.findViewById(R.id.category_icon);
            TextView name = view.findViewById(R.id.category_name);
            UiUtils.bindIconBadge(icon, UiUtils.iconFor(category), UiUtils.colorFor(category));
            name.setText(category.getDisplayName());
            int accent = UiUtils.color(view.getContext(), UiUtils.colorFor(category));
            progress.setIndicatorColor(accent);
            progress.setTrackColor(ColorUtils.setAlphaComponent(accent, 48));
        }

        void render(long amount, long total) {
            Context context = view.getContext();
            int percent = total == 0 ? 0 : (int) Math.round(amount * 100.0 / total);
            amountText.setText(Money.format(amount));
            percentText.setText(context.getString(R.string.percent_value, percent));
            progress.setProgressCompat(percent, true);
            view.setAlpha(amount == 0 ? 0.6f : 1f);
        }
    }
}
