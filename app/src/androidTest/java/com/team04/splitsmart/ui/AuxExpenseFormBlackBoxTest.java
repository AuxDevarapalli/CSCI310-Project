package com.team04.splitsmart.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.closeSoftKeyboard;
import static androidx.test.espresso.action.ViewActions.replaceText;
import static androidx.test.espresso.action.ViewActions.scrollTo;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.hasSibling;
import static androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import android.view.View;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.espresso.matcher.ViewMatchers.Visibility;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QuerySnapshot;
import com.team04.splitsmart.BuildConfig;
import com.team04.splitsmart.R;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Project 2.4 black-box tests. Author: Aux Devarapalli (adevarap@usc.edu).
 * The Add Expense screen (FR 2.3, FR 2.9, FR 3.2 to FR 3.7): every input is typed into the
 * real form and every check reads what the screen shows. Each test signs in as the seeded
 * demo user Alice and works in a new group of Alice, Bob and Carol that it deletes
 * afterwards, so the seeded demo groups other tests check are never changed. Requires the
 * Firebase Auth and Firestore emulators with the demo data from npm run seed:emulator,
 * and a build with -Psplitsmart.emulatorHost=10.0.2.2.
 */
@RunWith(AndroidJUnit4.class)
public class AuxExpenseFormBlackBoxTest {
    private static final String PASSWORD = "splitsmart-demo";
    private static final long TIMEOUT_MS = 15_000;

    private final Context context =
            InstrumentationRegistry.getInstrumentation().getTargetContext();
    /** Alice's own row reads "Alice (you)" because she is the signed-in user. */
    private final String aliceRow = context.getString(R.string.member_you, "Alice");
    private String groupId;

    @Before
    public void signInAsAliceWithAFreshGroup() throws Exception {
        assertTrue("Use -Psplitsmart.emulatorHost=10.0.2.2",
                !BuildConfig.FIREBASE_EMULATOR_HOST.isEmpty());
        FirebaseAuth auth = FirebaseAuth.getInstance();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(auth::signOut);
        Tasks.await(auth.signInWithEmailAndPassword("alice.demo@usc.edu", PASSWORD),
                20, TimeUnit.SECONDS);
        String alice = auth.getUid();
        groupId = createGroup(alice, Arrays.asList(alice,
                uidOf("bob.demo@usc.edu"), uidOf("carol.demo@usc.edu")));
    }

    /** Deletes the test's group and anything saved in it, whether the test passed or not. */
    @After
    public void deleteTheGroup() throws Exception {
        if (groupId == null) {
            return;
        }
        DocumentReference group = FirebaseFirestore.getInstance()
                .collection("groups").document(groupId);
        QuerySnapshot saved = Tasks.await(group.collection("transactions").get(),
                10, TimeUnit.SECONDS);
        for (DocumentSnapshot transaction : saved.getDocuments()) {
            Tasks.await(transaction.getReference().delete(), 10, TimeUnit.SECONDS);
        }
        Tasks.await(group.delete(), 10, TimeUnit.SECONDS);
    }

    // AD-B01: Equal split (FR 3.2, FR 3.5). The customer's example: $10.00 among Alice, Bob
    // and Carol previews $3.34, $3.33, $3.33. Retyping the total as $10.01 must update the
    // preview to the FR 3.5 example, $3.34, $3.34, $3.33.
    @Test
    public void equalSplitGivesLeftoverCentsToTheFirstParticipants() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> form = openAddExpense()) {
            tick("Bob");
            tick("Carol");
            type(R.id.expense_amount, "10.00");
            assertShares("$3.34", "$3.33", "$3.33");

            type(R.id.expense_amount, "10.01");
            assertShares("$3.34", "$3.34", "$3.33");
            assertStatus("Split equally: $3.34 or $3.33 each "
                    + "(extra cents go to the first people listed)");
        }
    }

    // AD-B02: Exact split that does not add up (FR 3.3, FR 3.6). The requirement's example:
    // $10.00 + $10.00 + $8.00 against a $30.00 total shows the $2.00 difference and cannot
    // be saved.
    @Test
    public void exactAmountsThatDoNotAddUpCannotBeSaved() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> form = openAddExpense()) {
            type(R.id.expense_title, "AD-B02 groceries");
            tick("Bob");
            tick("Carol");
            type(R.id.expense_amount, "30.00");
            onView(withId(R.id.split_exact)).perform(scrollTo(), click());
            typeShare(aliceRow, "10.00");
            typeShare("Bob", "10.00");
            typeShare("Carol", "8.00");
            String mismatch = "Amounts add up to $28.00, not $30.00 ($2.00 left to assign)";
            assertStatus(mismatch);

            onView(withId(R.id.expense_save_button)).perform(scrollTo(), click());
            // The refusal repeats the message in a snackbar, a second view besides the status.
            waitUntil(() -> onView(allOf(withText(mismatch),
                    not(withId(R.id.expense_split_status)))).check(matches(isDisplayed())));
            assertStaysOpen(form);
        }
    }

    // AD-B03: Percentage split (FR 3.4, FR 3.5). 50% + 49.99% is one hundredth of a percent
    // short and is refused; correcting it to 50% / 50% of $10.01 previews the requirement's
    // example, $5.01 and $5.00. Carol is not selected, so she has no share.
    @Test
    public void percentagesMustTotalExactlyOneHundred() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> form = openAddExpense()) {
            tick("Bob");
            type(R.id.expense_amount, "10.01");
            onView(withId(R.id.split_percent)).perform(scrollTo(), click());
            typeShare(aliceRow, "50");
            typeShare("Bob", "49.99");
            assertStatus("Percentages add up to 99.99%, not 100%");

            typeShare("Bob", "50");
            assertStatus("Percentages add up to 100%");
            onView(shareOf(aliceRow)).check(matches(withText("$5.01")));
            onView(shareOf("Bob")).check(matches(withText("$5.00")));
            onView(shareOf("Carol")).check(matches(withText(R.string.share_none)));
        }
    }

    // AD-B04: Total amount boundaries (FR 2.3). 10.005 has three decimals and is rejected as
    // soon as it is typed. 0.01, the smallest valid total, is accepted and split. 0.00 is
    // refused when the user taps Save.
    @Test
    public void totalMustBePositiveWithAtMostTwoDecimals() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> form = openAddExpense()) {
            type(R.id.expense_title, "AD-B04 coffee");
            tick("Bob");
            tick("Carol");
            type(R.id.expense_amount, "10.005");
            showAmountField();
            onView(withText("Amounts can have at most 2 decimal places"))
                    .check(matches(isDisplayed()));
            assertStatus("Enter a valid total to see each share");

            type(R.id.expense_amount, "0.01");
            assertShares("$0.01", "$0.00", "$0.00");

            type(R.id.expense_amount, "0.00");
            onView(withId(R.id.expense_save_button)).perform(scrollTo(), click());
            showAmountField();
            onView(withText("Amount must be greater than $0.00"))
                    .check(matches(isDisplayed()));
            assertStaysOpen(form);
        }
    }

    // AD-B05: Saving updates balances (FR 2.9, FR 3.7). Alice saves $30.00 split equally with
    // Bob and Carol. The form closes and the Balances tab shows that each of them owes her
    // $10.00, $20.00 in total, while she owes nothing for her own share.
    @Test
    public void savedEqualSplitShowsUpOnTheBalancesTab() throws Exception {
        try (ActivityScenario<ExpenseFormActivity> form = openAddExpense()) {
            type(R.id.expense_title, "AD-B05 dinner");
            tick("Bob");
            tick("Carol");
            type(R.id.expense_amount, "30.00");
            assertShares("$10.00", "$10.00", "$10.00");
            onView(withId(R.id.expense_save_button)).perform(scrollTo(), click());
            waitUntil(() -> assertEquals(Lifecycle.State.DESTROYED, form.getState()));
        }
        try (ActivityScenario<GroupDetailActivity> group = ActivityScenario.launch(
                GroupDetailActivity.newIntent(context, groupId,
                        GroupDetailActivity.TAB_BALANCES, false))) {
            waitUntil(() -> onView(withText("Bob owes you $10.00"))
                    .check(matches(isDisplayed())));
            onView(withText("Carol owes you $10.00")).check(matches(isDisplayed()));
            onView(withId(R.id.balance_total_receive)).check(matches(withText("$20.00")));
            onView(withId(R.id.balance_total_pay)).check(matches(withText("$0.00")));
        }
    }

    // ---- Screen helpers ----

    /** Opens Add Expense for the test group and waits until its members are loaded. */
    private ActivityScenario<ExpenseFormActivity> openAddExpense() throws Exception {
        ActivityScenario<ExpenseFormActivity> form = ActivityScenario.launch(
                ExpenseFormActivity.newCreateIntent(context, groupId));
        waitUntil(() -> onView(checkboxOf("Carol"))
                .check(matches(withEffectiveVisibility(Visibility.VISIBLE))));
        return form;
    }

    private static void type(int fieldId, String text) {
        onView(withId(fieldId)).perform(scrollTo(), replaceText(text), closeSoftKeyboard());
    }

    /** Types an exact amount or a percentage into one member's row. */
    private static void typeShare(String member, String text) {
        onView(inputOf(member)).perform(scrollTo(), replaceText(text), closeSoftKeyboard());
    }

    /** Selects a member as a participant. */
    private static void tick(String member) {
        onView(checkboxOf(member)).perform(scrollTo(), click());
    }

    private void assertShares(String alice, String bob, String carol) {
        onView(shareOf(aliceRow)).check(matches(withText(alice)));
        onView(shareOf("Bob")).check(matches(withText(bob)));
        onView(shareOf("Carol")).check(matches(withText(carol)));
    }

    /** Scrolls the whole amount field, including the error line under it, into view. */
    private static void showAmountField() {
        onView(withId(R.id.expense_amount_layout)).perform(scrollTo());
    }

    private static void assertStatus(String message) {
        onView(withId(R.id.expense_split_status)).check(matches(withText(message)));
    }

    /** A refused save keeps the form open; a successful one closes it once the write lands. */
    private static void assertStaysOpen(ActivityScenario<?> form) throws InterruptedException {
        Thread.sleep(2_000);
        assertEquals(Lifecycle.State.RESUMED, form.getState());
    }

    private static Matcher<View> checkboxOf(String member) {
        return allOf(withId(R.id.participant_check), withText(member));
    }

    /** The share preview in the same row as the member's checkbox. */
    private static Matcher<View> shareOf(String member) {
        return allOf(withId(R.id.participant_share), hasSibling(withText(member)));
    }

    /** The amount or percentage field in the same row as the member's checkbox. */
    private static Matcher<View> inputOf(String member) {
        return allOf(withId(R.id.participant_input), isDescendantOfA(allOf(
                withId(R.id.participant_input_layout), hasSibling(withText(member)))));
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

    // ---- Fixture helpers (setup and cleanup only; no test result is read from here) ----

    private static String uidOf(String email) throws Exception {
        QuerySnapshot users = Tasks.await(FirebaseFirestore.getInstance().collection("users")
                .whereEqualTo("uscEmail", email).limit(1).get(), 10, TimeUnit.SECONDS);
        if (users.isEmpty()) {
            throw new IllegalStateException(email + " not found. Run npm run seed:emulator.");
        }
        return users.getDocuments().get(0).getId();
    }

    /** Creates the group the way the app does: owner first, then the other members. */
    private static String createGroup(String owner, List<String> members) throws Exception {
        Map<String, Object> group = new HashMap<>();
        group.put("name", "AD black-box " + System.currentTimeMillis());
        group.put("description", "Project 2.4 test group, deleted after each test");
        group.put("ownerId", owner);
        group.put("memberIds", Collections.singletonList(owner));
        group.put("createdAt", System.currentTimeMillis());
        DocumentReference ref = Tasks.await(FirebaseFirestore.getInstance()
                .collection("groups").add(group), 10, TimeUnit.SECONDS);
        Tasks.await(ref.update("memberIds", members), 10, TimeUnit.SECONDS);
        return ref.getId();
    }
}
