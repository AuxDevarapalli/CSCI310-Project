package com.team04.splitsmart.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.history.SpendingInsights;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class SpendingInsightsPanelTest {
    @Test
    public void trendRowsRenderAndSelectAMonthInLightMode() throws Exception {
        checkPanel(Configuration.UI_MODE_NIGHT_NO);
    }

    @Test
    public void trendRowsRenderAndSelectAMonthInDarkMode() throws Exception {
        checkPanel(Configuration.UI_MODE_NIGHT_YES);
    }

    private void checkPanel(int nightMode) throws Exception {
        FutureTask<Void> check = new FutureTask<>(() -> {
            Context app = InstrumentationRegistry.getInstrumentation().getTargetContext();
            Configuration configuration = new Configuration(app.getResources().getConfiguration());
            configuration.uiMode = (configuration.uiMode & ~Configuration.UI_MODE_NIGHT_MASK)
                    | nightMode;
            Context theme = new ContextThemeWrapper(
                    app.createConfigurationContext(configuration), R.style.Theme_SplitSmart);
            View root = LayoutInflater.from(theme).inflate(R.layout.panel_spending_insights, null);
            AtomicReference<String> selectedMonth = new AtomicReference<>();
            SpendingInsightsPanel panel = new SpendingInsightsPanel(
                    root, expense -> { }, selectedMonth::set);

            panel.render(SpendingInsights.calculate(Collections.emptyList(), "2026-11", "alice"));

            assertEquals(View.VISIBLE, root.getVisibility());
            LinearLayout trend = root.findViewById(R.id.insights_trend);
            assertEquals(6, trend.getChildCount());
            View selectedRow = trend.getChildAt(5);
            assertTrue(selectedRow.isSelected());
            TextView label = selectedRow.findViewById(R.id.trend_month);
            assertEquals("Nov", label.getText().toString());
            TextView selectedAmount = root.findViewById(R.id.trend_selected_amount);
            assertEquals("$0.00", selectedAmount.getText().toString());
            trend.getChildAt(4).performClick();
            assertEquals("2026-10", selectedMonth.get());
            return null;
        });
        InstrumentationRegistry.getInstrumentation().runOnMainSync(check);
        check.get();
    }
}
