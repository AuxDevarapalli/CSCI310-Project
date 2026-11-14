package com.team04.splitsmart.ui;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.firebase.auth.FirebaseAuth;
import com.team04.splitsmart.BuildConfig;
import com.team04.splitsmart.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Project 2.4 black-box tests. Author: Edward Zhao (ebzhao@usc.edu).
 * Inputs and assertions use only the real screen; Firebase sign-out is fixture cleanup.
 * Build with -Psplitsmart.emulatorHost=10.0.2.2 to isolate these tests from production.
 */
@RunWith(AndroidJUnit4.class)
public class EdwardRegistrationBlackBoxTest {
    @Before public void isolateSession() {
        assertTrue("Use -Psplitsmart.emulatorHost=10.0.2.2", 
                !BuildConfig.FIREBASE_EMULATOR_HOST.isEmpty());
        InstrumentationRegistry.getInstrumentation().runOnMainSync(
                () -> FirebaseAuth.getInstance().signOut());
    }

    // EZ-B01: Missing required name, with every other input valid.
    @Test public void blankNameShowsRequiredError() {
        reject("   ", "edward.test@usc.edu", "1234567890", "secret", "secret",
                R.id.register_name, "Enter your name");
    }

    // EZ-B02: A plausible address outside the allowed university domain.
    @Test public void nonUscEmailShowsDomainError() {
        reject("Edward", "edward.test@gmail.com", "1234567890", "secret", "secret",
                R.id.register_email, "Use a USC email address ending in @usc.edu");
    }

    // EZ-B03: Correct domain suffix without an account name is not a valid address.
    @Test public void missingEmailLocalPartShowsFormatError() {
        reject("Edward", "@usc.edu", "1234567890", "secret", "secret",
                R.id.register_email, "Enter a valid USC email address");
    }

    // EZ-B04: Nine digits is immediately below the exact ten-digit ID boundary.
    @Test public void nineDigitIdShowsLengthError() {
        reject("Edward", "edward.test@usc.edu", "123456789", "secret", "secret",
                R.id.register_usc_id, "USC ID must be exactly 10 digits");
    }

    // EZ-B05: Ten characters is insufficient if one character is not a digit.
    @Test public void alphabeticIdShowsFormatError() {
        reject("Edward", "edward.test@usc.edu", "123456789a", "secret", "secret",
                R.id.register_usc_id, "USC ID must be exactly 10 digits");
    }

    private void reject(String name, String email, String id, String password,
                        String confirm, int errorInput, String message) {
        try (ActivityScenario<RegisterActivity> screen = ActivityScenario.launch(RegisterActivity.class)) {
            fill(R.id.register_name, name);
            fill(R.id.register_email, email);
            fill(R.id.register_usc_id, id);
            fill(R.id.register_password, password);
            fill(R.id.register_confirm, confirm);
            onView(withId(R.id.register_button)).perform(scrollTo(), click());
            onView(withId(errorInput)).perform(scrollTo());
            onView(withText(message)).check(matches(isDisplayed()));
            onView(withId(R.id.register_button)).perform(scrollTo()).check(matches(isEnabled()));
        }
    }

    private static void fill(int id, String value) {
        onView(withId(id)).perform(scrollTo(), replaceText(value), closeSoftKeyboard());
    }
}
