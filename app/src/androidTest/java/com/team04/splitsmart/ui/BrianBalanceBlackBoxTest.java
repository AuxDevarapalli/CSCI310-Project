package com.team04.splitsmart.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.content.Intent;

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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

/**
 * Project 2.4 black-box tests. Author: Brian Shi (brianshi@usc.edu).
 * Balance and settlement UI validation (FR 4.1 - 4.4) using the pre-seeded demo data
 * for Alice (alice.demo@usc.edu). Requires the Firebase emulator / emulatorHost flag.
 */
@RunWith(AndroidJUnit4.class)
public class BrianBalanceBlackBoxTest {

    @Before
    public void signInAsAlice() throws Exception {
        assertTrue("Use -Psplitsmart.emulatorHost=10.0.2.2",
                !BuildConfig.FIREBASE_EMULATOR_HOST.isEmpty());
        FirebaseAuth auth = FirebaseAuth.getInstance();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(auth::signOut);
        Tasks.await(auth.signInWithEmailAndPassword(
                "alice.demo@usc.edu", "splitsmart-demo"), 20, TimeUnit.SECONDS);
    }

    // BR-B01: Apartment 4B displays $4.00 Total to Pay and $15.00 Total to Receive.
    @Test
    public void apartmentShowsSeparateTotals() throws Exception {
        Intent intent = createBalancesIntent("Apartment 4B");
        try (ActivityScenario<GroupDetailActivity> ignored = ActivityScenario.launch(intent)) {
            waitForViewWithText("$4.00");
            onView(withId(R.id.balance_total_pay)).check(matches(withText("$4.00")));
            onView(withId(R.id.balance_total_receive)).check(matches(withText("$15.00")));
        }
    }

    // BR-B02: Apartment 4B displays "Bob owes you $10.00".
    @Test
    public void apartmentShowsBobOwesAliceTenDollars() throws Exception {
        Intent intent = createBalancesIntent("Apartment 4B");
        try (ActivityScenario<GroupDetailActivity> ignored = ActivityScenario.launch(intent)) {
            waitForViewWithText("Bob owes you $10.00");
        }
    }

    // BR-B03: Apartment 4B displays "You owe David $4.00".
    @Test
    public void apartmentShowsAliceOwesDavidFourDollars() throws Exception {
        Intent intent = createBalancesIntent("Apartment 4B");
        try (ActivityScenario<GroupDetailActivity> ignored = ActivityScenario.launch(intent)) {
            waitForViewWithText("You owe David $4.00");
        }
    }

    // BR-B04: Road Trip starts at $11.00 debt; recording a $5 settlement reduces it to $6.00 ("You owe Bob $6.00").
    @Test
    public void roadTripSettlementReducesDebtToSixDollars() throws Exception {
        String groupId = resolveGroupId("Road Trip");
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = GroupDetailActivity.newIntent(context, groupId, GroupDetailActivity.TAB_BALANCES, false);
        String uniqueNote = "BR-B04-test-" + System.currentTimeMillis();

        try (ActivityScenario<GroupDetailActivity> ignored = ActivityScenario.launch(intent)) {
            waitForViewWithText("You owe Bob $11.00");

            onView(withId(R.id.settle_up_button)).perform(click());
            waitForSettlementForm();

            onView(withId(R.id.settlement_amount)).perform(scrollTo(), replaceText("5.00"));
            onView(withId(R.id.settlement_note)).perform(scrollTo(), replaceText(uniqueNote), closeSoftKeyboard());
            onView(withId(R.id.settlement_save_button)).perform(scrollTo(), click());

            waitForViewWithText("You owe Bob $6.00");
        } finally {
            cleanupSettlementByNote(groupId, uniqueNote);
        }
    }

    // BR-B05: Study Group does not simplify chain debt (Alice owes Bob $10, but Alice and Carol remain settled up).
    @Test
    public void studyGroupDoesNotSimplifyChainDebt() throws Exception {
        Intent intent = createBalancesIntent("Study Group");
        try (ActivityScenario<GroupDetailActivity> ignored = ActivityScenario.launch(intent)) {
            waitForViewWithText("You owe Bob $10.00");
            onView(withText("You and Carol are settled up")).check(matches(isDisplayed()));
        }
    }

    private Intent createBalancesIntent(String groupName) throws Exception {
        String groupId = resolveGroupId(groupName);
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        return GroupDetailActivity.newIntent(context, groupId, GroupDetailActivity.TAB_BALANCES, false);
    }

    private String resolveGroupId(String groupName) throws Exception {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            throw new IllegalStateException("Not signed in");
        }
        QuerySnapshot snap = Tasks.await(FirebaseFirestore.getInstance()
                .collection("groups")
                .whereArrayContains("memberIds", uid)
                .get(), 10, TimeUnit.SECONDS);
        String foundId = null;
        for (DocumentSnapshot doc : snap.getDocuments()) {
            if (groupName.equals(doc.getString("name"))) {
                if (foundId != null) {
                    throw new IllegalStateException("Multiple groups found with name: " + groupName);
                }
                foundId = doc.getId();
            }
        }
        if (foundId == null) {
            throw new IllegalStateException("Group not found for member " + uid + ": " + groupName);
        }
        return foundId;
    }

    private static void waitForSettlementForm() throws Exception {
        long deadline = android.os.SystemClock.uptimeMillis() + 10_000;
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            try {
                onView(withId(R.id.settlement_scroll)).check(matches(isDisplayed()));
                return;
            } catch (androidx.test.espresso.NoMatchingViewException
                     | AssertionError notReady) {
                Thread.sleep(100);
            }
        }
        onView(withId(R.id.settlement_scroll)).check(matches(isDisplayed()));
    }

    private static void waitForViewWithText(String expectedText) throws Exception {
        long end = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < end) {
            try {
                onView(withText(expectedText)).check(matches(isDisplayed()));
                return;
            } catch (Throwable ignored) {
                Thread.sleep(200);
            }
        }
        onView(withText(expectedText)).check(matches(isDisplayed()));
    }

    private static void cleanupSettlementByNote(String groupId, String note) throws Exception {
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        QuerySnapshot snap = Tasks.await(db.collection("groups")
                .document(groupId)
                .collection("transactions")
                .whereEqualTo("note", note)
                .get(), 10, TimeUnit.SECONDS);
        for (DocumentSnapshot doc : snap.getDocuments()) {
            Tasks.await(doc.getReference().delete(), 10, TimeUnit.SECONDS);
        }
    }
}
