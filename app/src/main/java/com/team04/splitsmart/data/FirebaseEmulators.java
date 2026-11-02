package com.team04.splitsmart.data;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;

/**
 * Points Firebase Authentication and Cloud Firestore at the local Firebase emulators. Only used
 * for testing, when the app is built with {@code -Psplitsmart.emulatorHost=<host>}; normal
 * builds always use the real Firebase project.
 */
public final class FirebaseEmulators {
    public static final int AUTH_PORT = 9099;
    public static final int FIRESTORE_PORT = 8080;

    private FirebaseEmulators() {
    }

    /** Must be called before any repository is created. */
    public static void connect(String host) {
        FirebaseAuth.getInstance().useEmulator(host, AUTH_PORT);
        FirebaseFirestore.getInstance().useEmulator(host, FIRESTORE_PORT);
    }
}
