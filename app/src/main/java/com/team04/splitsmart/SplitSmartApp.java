package com.team04.splitsmart;

import android.app.Application;
import android.content.Context;

import com.team04.splitsmart.data.AuthRepository;
import com.team04.splitsmart.data.FirebaseEmulators;
import com.team04.splitsmart.viewmodel.AppContainer;

/**
 * Application entry point. Builds the {@link AppContainer} once per process. If the app was
 * built without {@code app/google-services.json}, Firebase cannot start; the container is then
 * left empty and the login screen explains how to finish the setup.
 */
public class SplitSmartApp extends Application {
    private AppContainer container;
    private boolean firebaseConfigured;

    @Override
    public void onCreate() {
        super.onCreate();
        firebaseConfigured = AuthRepository.isFirebaseConfigured(this);
        if (firebaseConfigured) {
            if (!BuildConfig.FIREBASE_EMULATOR_HOST.isEmpty()) {
                FirebaseEmulators.connect(BuildConfig.FIREBASE_EMULATOR_HOST);
            }
            container = AppContainer.createDefault();
        }
    }

    public static SplitSmartApp from(Context context) {
        return (SplitSmartApp) context.getApplicationContext();
    }

    public boolean isFirebaseConfigured() {
        return firebaseConfigured;
    }

    /** The shared dependencies; {@code null} only when Firebase is not configured. */
    public AppContainer getContainer() {
        return container;
    }
}
