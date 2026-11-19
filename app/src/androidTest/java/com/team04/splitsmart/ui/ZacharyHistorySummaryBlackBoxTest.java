package com.team04.splitsmart.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isChecked;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.team04.splitsmart.BuildConfig;
import com.team04.splitsmart.R;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Project 2.4 black-box tests. Author: Zachary Chung (zchung@usc.edu).
 * The History and Summary tabs (FR 5.4, FR 5.6, FR 5.7). Each test signs in as the seeded demo
 * user Alice, opens the seeded Dinner Club group, types or taps on the real screen, and checks
 * only what the screen shows. Dinner Club holds six transactions: Concert tickets ($30.00) and
 * Farmers market ($30.00) last month, then Pizza night ($30.00), Uber to the concert ($20.00),
 * a $5.00 settlement noted "Venmo for pizza" and a $4.00 settlement noted "Cash" this month,
 * created in that order. The tests only read data, so nothing needs to be cleaned up.
 * Requires the Firebase Auth and Firestore emulators with fresh demo data from
 * npm run seed:emulator, and a build with -Psplitsmart.emulatorHost=10.0.2.2.
 */
@RunWith(AndroidJUnit4.class)
public class ZacharyHistorySummaryBlackBoxTest {
    private static final String PASSWORD = "splitsmart-demo";
    private static final String GROUP_NAME = "Dinner Club";
    private static final long TIMEOUT_MS = 15_000;
    private static final DateTimeFormatter MONTH_LABEL =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private final Context context =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    private String groupId;

    @Before
    public void signInAsAliceAndFindDinnerClub() throws Exception {
        assertTrue("Use -Psplitsmart.emulatorHost=10.0.2.2",
                !BuildConfig.FIREBASE_EMULATOR_HOST.isEmpty());
        FirebaseAuth auth = FirebaseAuth.getInstance();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(auth::signOut);
        Tasks.await(auth.signInWithEmailAndPassword("alice.demo@usc.edu", PASSWORD),
                20, TimeUnit.SECONDS);
        groupId = groupIdOf(GROUP_NAME, auth.getUid());
    }

    // ZC-B01: History is newest first by default (FR 5.4), and tapping the sort chip
    // reverses it (FR 5.6). The newest record is the $4.00 settlement; the oldest is
    // Concert tickets from last month. Sorting never changes how many records are shown.
    @Test
    public void historyIsNewestFirstAndSortChipReversesIt() throws Exception {
        try (ActivityScenario<GroupDetailActivity> screen = openTab(GroupDetailActivity.TAB_HISTORY)) {
            waitForCount("6 transactions");
            onView(withId(R.id.chip_sort)).check(matches(withText("Newest first")));
            waitUntil(() -> assertRow(0, "Settlement", "$4.00"));

            onView(withId(R.id.chip_sort)).perform(click());
            waitUntil(() -> onView(withId(R.id.chip_sort))
                    .check(matches(withText("Oldest first"))));
            waitUntil(() -> assertRow(0, "Concert tickets", "$30.00"));
            onView(withId(R.id.history_count)).check(matches(withText("6 transactions")));
        }
    }

    // ZC-B02: Keyword search ignores case by default and searches both expense titles and
    // notes (FR 5.6). "pizza" in lower case matches the title "Pizza night" and the
    // settlement note "Venmo for pizza", and nothing else.
    @Test
    public void searchIgnoresCaseAndCoversTitlesAndNotes() throws Exception {
        try (ActivityScenario<GroupDetailActivity> screen = openTab(GroupDetailActivity.TAB_HISTORY)) {
            waitForCount("6 transactions");

            search("pizza");
            waitForCount("Showing 2 of 6");
            waitUntil(() -> assertRow(0, "Settlement", "$5.00"));
            assertRow(1, "Pizza night", "$30.00");
        }
    }

    // ZC-B03: The Match case chip makes the search exact (FR 5.6, R13). "Pizza" first finds
    // both pizza records because case is ignored; turning on Match case drops the settlement
    // whose note says "pizza" in lower case and keeps only the "Pizza night" title.
    @Test
    public void matchCaseKeepsOnlyTheExactCapitalization() throws Exception {
        try (ActivityScenario<GroupDetailActivity> screen = openTab(GroupDetailActivity.TAB_HISTORY)) {
            waitForCount("6 transactions");

            search("Pizza");
            waitForCount("Showing 2 of 6");

            onView(withId(R.id.chip_match_case)).perform(click());
            waitForCount("Showing 1 of 6");
            onView(withId(R.id.chip_match_case)).check(matches(isChecked()));
            waitUntil(() -> assertRow(0, "Pizza night", "$30.00"));
        }
    }

    // ZC-B04: Filters combine, and Clear filters restores the full history (FR 5.6). The
    // Settlements chip leaves the two settlements; adding the keyword "pizza" removes the
    // Pizza night expense (wrong type) and the Cash settlement (no match), leaving one.
    // Clear filters then brings back all six records, reselects All, and empties the search.
    @Test
    public void filtersCombineAndClearFiltersRestoresEverything() throws Exception {
        try (ActivityScenario<GroupDetailActivity> screen = openTab(GroupDetailActivity.TAB_HISTORY)) {
            waitForCount("6 transactions");

            onView(withId(R.id.chip_type_settlements)).perform(click());
            waitForCount("Showing 2 of 6");

            search("pizza");
            waitForCount("Showing 1 of 6");
            waitUntil(() -> assertRow(0, "Settlement", "$5.00"));

            onView(withId(R.id.history_clear_filters)).perform(click());
            waitForCount("6 transactions");
            onView(withId(R.id.chip_type_all)).check(matches(isChecked()));
            onView(withId(R.id.history_search)).check(matches(withText("")));
            onView(withId(R.id.history_clear_filters)).check(matches(not(isDisplayed())));
        }
    }

    // ZC-B05: The monthly summary counts only expenses created in the chosen month and
    // leaves settlements out (FR 5.7). This month has two expenses totaling $50.00; if the
    // $5.00 and $4.00 settlements were counted it would show 4 and $59.00. The previous-month
    // button moves to last month, which has two expenses totaling $60.00.
    @Test
    public void summaryCountsOnlyThatMonthsExpenses() throws Exception {
        YearMonth thisMonth = YearMonth.now();
        try (ActivityScenario<GroupDetailActivity> screen = openTab(GroupDetailActivity.TAB_SUMMARY)) {
            waitUntil(() -> onView(withId(R.id.summary_total)).check(matches(withText("$50.00"))));
            onView(withId(R.id.summary_month))
                    .check(matches(withText(thisMonth.format(MONTH_LABEL))));
            onView(withId(R.id.summary_count)).check(matches(withText("2")));

            onView(withId(R.id.summary_prev)).perform(click());
            waitUntil(() -> onView(withId(R.id.summary_month))
                    .check(matches(withText(thisMonth.minusMonths(1).format(MONTH_LABEL)))));
            waitUntil(() -> onView(withId(R.id.summary_total)).check(matches(withText("$60.00"))));
            onView(withId(R.id.summary_count)).check(matches(withText("2")));
        }
    }

    // ---- Screen helpers ----

    private ActivityScenario<GroupDetailActivity> openTab(int tab) {
        return ActivityScenario.launch(GroupDetailActivity.newIntent(context, groupId, tab, false));
    }

    private static void search(String keyword) {
        onView(withId(R.id.history_search)).perform(replaceText(keyword), closeSoftKeyboard());
    }

    /** Waits for the line above the list: "6 transactions" or "Showing X of 6". */
    private static void waitForCount(String text) throws InterruptedException {
        waitUntil(() -> onView(withId(R.id.history_count)).check(matches(withText(text))));
    }

    /** Checks the title and amount shown in one row of the history list, counted from the top. */
    private static void assertRow(int position, String title, String amount) {
        onView(allOf(withId(R.id.tx_title), isDescendantOfA(historyRow(position))))
                .check(matches(withText(title)));
        onView(allOf(withId(R.id.tx_amount), isDescendantOfA(historyRow(position))))
                .check(matches(withText(amount)));
    }

    /** The row at {@code position} of the history list (not rows on other tabs). */
    private static Matcher<View> historyRow(int position) {
        return new TypeSafeMatcher<View>() {
            @Override
            protected boolean matchesSafely(View view) {
                if (!(view.getParent() instanceof RecyclerView)) {
                    return false;
                }
                RecyclerView list = (RecyclerView) view.getParent();
                return list.getId() == R.id.history_list
                        && list.getChildAdapterPosition(view) == position;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("row " + position + " of the history list");
            }
        };
    }

    /** Retries a check until it passes, for screens that wait on the database. */
    private static void waitUntil(Runnable check) throws InterruptedException {
        long deadline = SystemClock.uptimeMillis() + TIMEOUT_MS;
        while (SystemClock.uptimeMillis() < deadline) {
            try {
                check.run();
                return;
            } catch (Throwable notYet) {
                Thread.sleep(200);
            }
        }
        check.run();
    }

    // ---- Fixture helper (setup only; no test result is read from here) ----

    private static String groupIdOf(String name, String uid) throws Exception {
        if (uid == null) {
            throw new IllegalStateException("Not signed in");
        }
        QuerySnapshot groups = Tasks.await(FirebaseFirestore.getInstance().collection("groups")
                .whereArrayContains("memberIds", uid).get(), 10, TimeUnit.SECONDS);
        String found = null;
        for (DocumentSnapshot group : groups.getDocuments()) {
            if (name.equals(group.getString("name"))) {
                if (found != null) {
                    throw new IllegalStateException("More than one group named " + name);
                }
                found = group.getId();
            }
        }
        if (found == null) {
            throw new IllegalStateException(name + " not found. Run npm run seed:emulator.");
        }
        return found;
    }
}
