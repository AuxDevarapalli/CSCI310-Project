package com.team04.splitsmart.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.assertTrue;

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
 * Project 2.4 black-box tests. Author: Swayam Parekh (swayampa@usc.edu).
 * Group creation form (FR 1.4): required name, name and description length limits
 * and the error-recovery transition, observed only through the real screen. These
 * tests need a signed-in session, so unlike the registration tests they require the
 * Firebase Auth emulator to be running (firebase.json maps it to port 9099). The
 * invalid-input cases still stop before any Firestore write, but SW-B03 saves a
 * group, so the Firestore emulator (port 8080) must be running too. Build with
 * -Psplitsmart.emulatorHost=10.0.2.2.
 */
@RunWith(AndroidJUnit4.class)
public class SwayamGroupFormBlackBoxTest {
    private static final String NAME_ERROR = "Enter a group name";
    private static final String NAME_LENGTH_ERROR = "Group name must be at most 50 characters";
    private static final String DESCRIPTION_LENGTH_ERROR =
            "Description must be at most 200 characters";

    @Before public void signInOnEmulator() throws Exception {
        assertTrue("Use -Psplitsmart.emulatorHost=10.0.2.2",
                !BuildConfig.FIREBASE_EMULATOR_HOST.isEmpty());
        FirebaseAuth auth = FirebaseAuth.getInstance();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(auth::signOut);
        try {
            Tasks.await(auth.createUserWithEmailAndPassword(
                    "swayam.blackbox@usc.edu", "secret1"), 20, TimeUnit.SECONDS);
        } catch (Exception alreadyExists) {
            Tasks.await(auth.signInWithEmailAndPassword(
                    "swayam.blackbox@usc.edu", "secret1"), 20, TimeUnit.SECONDS);
        }
    }

    // SW-B01: Missing required group name; the empty equivalence class for FR 1.4.
    @Test public void blankNameShowsRequiredError() {
        try (ActivityScenario<GroupFormActivity> screen =
                     ActivityScenario.launch(GroupFormActivity.class)) {
            fill(R.id.group_description, "Apartment expenses");
            onView(withId(R.id.group_save_button)).perform(scrollTo(), click());
            onView(withText(NAME_ERROR)).check(matches(isDisplayed()));
            onView(withId(R.id.group_save_button)).perform(scrollTo())
                    .check(matches(isEnabled()));
        }
    }

    // SW-B02: Fifty-one characters is immediately above the name-length boundary.
    @Test public void fiftyOneCharNameShowsLengthError() {
        try (ActivityScenario<GroupFormActivity> screen =
                     ActivityScenario.launch(GroupFormActivity.class)) {
            fill(R.id.group_name, repeat('a', 51));
            onView(withId(R.id.group_save_button)).perform(scrollTo(), click());
            onView(withText(NAME_LENGTH_ERROR)).check(matches(isDisplayed()));
            onView(withId(R.id.group_save_button)).perform(scrollTo())
                    .check(matches(isEnabled()));
        }
    }

    // SW-B03: Exactly fifty characters is the accepted side of the name boundary;
    // the save completes and the group detail screen shows the new group. The name
    // is unique to this run so the cleanup removes only what this run created.
    @Test public void fiftyCharNamePassesValidation() throws Exception {
        String groupName = uniqueFiftyCharName();
        try (ActivityScenario<GroupFormActivity> screen =
                     ActivityScenario.launch(GroupFormActivity.class)) {
            fill(R.id.group_name, groupName);
            onView(withId(R.id.group_save_button)).perform(scrollTo(), click());
            waitForViewWithText(groupName);
        } finally {
            deleteGroupsNamed(groupName);
        }
    }

    // SW-B04: Two hundred and one characters is immediately above the description
    // boundary; the valid name isolates the description check (FR 1.7).
    @Test public void twoHundredOneCharDescriptionShowsLengthError() {
        try (ActivityScenario<GroupFormActivity> screen =
                     ActivityScenario.launch(GroupFormActivity.class)) {
            fill(R.id.group_name, "Roommates");
            fill(R.id.group_description, repeat('d', 201));
            onView(withId(R.id.group_save_button)).perform(scrollTo(), click());
            onView(withText(DESCRIPTION_LENGTH_ERROR)).check(matches(isDisplayed()));
            onView(withId(R.id.group_save_button)).perform(scrollTo())
                    .check(matches(isEnabled()));
        }
    }

    // SW-B05: Correcting the name clears its stale error and exposes the next
    // invalid field, the observable error-recovery transition.
    @Test public void correctingNameMovesErrorToDescription() {
        try (ActivityScenario<GroupFormActivity> screen =
                     ActivityScenario.launch(GroupFormActivity.class)) {
            onView(withId(R.id.group_save_button)).perform(scrollTo(), click());
            onView(withText(NAME_ERROR)).check(matches(isDisplayed()));
            fill(R.id.group_name, "Roommates");
            fill(R.id.group_description, repeat('d', 201));
            onView(withId(R.id.group_save_button)).perform(scrollTo(), click());
            onView(withText(DESCRIPTION_LENGTH_ERROR)).check(matches(isDisplayed()));
            onView(withText(NAME_ERROR)).check(doesNotExist());
        }
    }

    private void fill(int viewId, String text) {
        onView(withId(viewId)).perform(scrollTo(), replaceText(text), closeSoftKeyboard());
    }

    private static String repeat(char c, int count) {
        char[] chars = new char[count];
        java.util.Arrays.fill(chars, c);
        return new String(chars);
    }

    /** Exactly fifty characters, unique per run: "SW-B03-" plus a timestamp, padded. */
    private static String uniqueFiftyCharName() {
        StringBuilder name = new StringBuilder("SW-B03-" + System.currentTimeMillis());
        while (name.length() < 50) {
            name.append('a');
        }
        return name.substring(0, 50);
    }

    private static void waitForViewWithText(String expectedText) throws Exception {
        long deadline = System.currentTimeMillis() + 10_000;
        while (System.currentTimeMillis() < deadline) {
            try {
                onView(withText(expectedText)).check(matches(isDisplayed()));
                return;
            } catch (Throwable notReady) {
                Thread.sleep(200);
            }
        }
        onView(withText(expectedText)).check(matches(isDisplayed()));
    }

    /**
     * Deletes this run's group, whether the test passed or failed. The save is
     * asynchronous, so a failed run may reach cleanup before the write lands;
     * poll briefly so that group is still found and removed.
     */
    private static void deleteGroupsNamed(String groupName) throws Exception {
        String uid = FirebaseAuth.getInstance().getUid();
        if (uid == null) {
            return;
        }
        FirebaseFirestore db = FirebaseFirestore.getInstance();
        long deadline = System.currentTimeMillis() + 10_000;
        do {
            QuerySnapshot snap = Tasks.await(db.collection("groups")
                    .whereArrayContains("memberIds", uid)
                    .get(), 10, TimeUnit.SECONDS);
            boolean deleted = false;
            for (DocumentSnapshot doc : snap.getDocuments()) {
                if (groupName.equals(doc.getString("name"))) {
                    Tasks.await(doc.getReference().delete(), 10, TimeUnit.SECONDS);
                    deleted = true;
                }
            }
            if (deleted) {
                return;
            }
            Thread.sleep(500);
        } while (System.currentTimeMillis() < deadline);
    }
}
