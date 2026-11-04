package com.team04.splitsmart.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.res.Configuration;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.team04.splitsmart.R;
import com.team04.splitsmart.logic.history.MonthlySummary;
import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.model.Category;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.concurrent.FutureTask;
import java.util.concurrent.atomic.AtomicReference;

@RunWith(AndroidJUnit4.class)
public class CategoryBreakdownPanelTest {
    @Test
    public void categoryRowsUpdateAndRemainSelectableInLightMode() throws Exception {
        checkPanel(Configuration.UI_MODE_NIGHT_NO);
    }

    @Test
    public void categoryRowsUpdateAndRemainSelectableInDarkMode() throws Exception {
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
            LinearLayout container = new LinearLayout(theme);
            AtomicReference<Category> selected = new AtomicReference<>();
            CategoryBreakdownPanel panel = new CategoryBreakdownPanel(container, selected::set);
            Category populated = Category.values()[0];
            panel.render(new MonthlySummary("2026-11", 1, 1234,
                    Collections.singletonMap(populated, 1234L)));

            assertEquals(Category.values().length, container.getChildCount());
            for (int i = 0; i < Category.values().length; i++) {
                Category category = Category.values()[i];
                View row = container.getChildAt(i);
                assertEquals(category.getDisplayName(),
                        ((TextView) row.findViewById(R.id.category_name)).getText().toString());
                assertTrue(row.isFocusable());
                assertTrue(row.performClick());
                assertEquals(category, selected.get());
                assertEquals(Money.format(i == 0 ? 1234 : 0),
                        ((TextView) row.findViewById(R.id.category_amount)).getText().toString());
                assertEquals(theme.getString(R.string.percent_value, i == 0 ? 100 : 0),
                        ((TextView) row.findViewById(R.id.category_percent)).getText().toString());
                assertEquals(i == 0 ? 1f : 0.6f, row.getAlpha(), 0.001f);
            }

            View firstRow = container.getChildAt(0);
            panel.render(new MonthlySummary("2026-12", 0, 0, Collections.emptyMap()));
            assertSame(firstRow, container.getChildAt(0));
            assertEquals(Money.format(0),
                    ((TextView) firstRow.findViewById(R.id.category_amount)).getText().toString());
            assertEquals(theme.getString(R.string.percent_value, 0),
                    ((TextView) firstRow.findViewById(R.id.category_percent)).getText().toString());
            assertEquals(0.6f, firstRow.getAlpha(), 0.001f);
            return null;
        });
        InstrumentationRegistry.getInstrumentation().runOnMainSync(check);
        check.get();
    }
}
