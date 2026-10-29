package com.team04.splitsmart.data;

import android.content.Context;

import com.google.firebase.FirebaseApp;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.team04.splitsmart.model.User;

/**
 * Account registration, login, logout and the current session (FR 1.1 to FR 1.3). This is the
 * only class that talks to Firebase Authentication (C5), which stores each password as a salted
 * hash (NFR 4).
 */
public class AuthRepository {
    private final FirebaseAuth auth;
    private final FirebaseFirestore db;

    public AuthRepository(FirebaseAuth auth, FirebaseFirestore db) {
        this.auth = auth;
        this.db = db;
    }

    /** Repository backed by the default Firebase project from google-services.json. */
    public static AuthRepository create() {
        return new AuthRepository(FirebaseAuth.getInstance(), FirebaseFirestore.getInstance());
    }

    /**
     * False when the app was built without {@code app/google-services.json}, in which case
     * Firebase cannot start and the app shows setup instructions instead of crashing.
     */
    public static boolean isFirebaseConfigured(Context context) {
        return !FirebaseApp.getApps(context).isEmpty();
    }

    /** Uid of the logged-in user, or {@code null}. Firebase keeps the session across restarts. */
    public String getCurrentUserId() {
        FirebaseUser user = auth.getCurrentUser();
        return user == null ? null : user.getUid();
    }

    public boolean isLoggedIn() {
        return auth.getCurrentUser() != null;
    }

    /**
     * Creates an account (design Figure 3). Firebase Authentication rejects an email that is
     * already registered; the USC ID is reserved by creating {@code uscIds/{uscId}} in the same
     * Firestore transaction that creates {@code users/{uid}}, so two accounts can never share a
     * USC ID (FR 1.2). If the USC ID turns out to be taken, the new login is deleted again.
     *
     * @param email    already validated and lower-cased USC email
     * @param uscId    already validated 10-digit USC ID
     * @param nowMillis creation time stored on the profile
     */
    public void register(String name, String email, String uscId, String password, long nowMillis,
                         DataCallback<User> callback) {
        auth.createUserWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser == null) {
                        callback.onFailure(new RepositoryException(FirebaseErrors.GENERIC));
                        return;
                    }
                    User user = new User(firebaseUser.getUid(), name, email, uscId);
                    saveProfile(firebaseUser, user, nowMillis, callback);
                })
                .addOnFailureListener(e -> callback.onFailure(registrationError(e)));
    }

    private void saveProfile(FirebaseUser firebaseUser, User user, long nowMillis,
                             DataCallback<User> callback) {
        DocumentReference uscIdRef =
                db.collection(FirestoreMapper.USC_IDS).document(user.getUscId());
        DocumentReference userRef =
                db.collection(FirestoreMapper.USERS).document(user.getUid());
        db.runTransaction(transaction -> {
                    DocumentSnapshot existing = transaction.get(uscIdRef);
                    if (existing.exists()) {
                        throw new UscIdTakenException();
                    }
                    transaction.set(uscIdRef, FirestoreMapper.uscIdToMap(user.getUid()));
                    transaction.set(userRef, FirestoreMapper.userToMap(user, nowMillis));
                    return null;
                })
                .addOnSuccessListener(ignored -> callback.onSuccess(user))
                .addOnFailureListener(e -> {
                    // Undo the half-created account so the email can be used again.
                    RepositoryException error = isUscIdTaken(e)
                            ? new RepositoryException("An account with this USC ID already exists.")
                            : FirebaseErrors.wrap(e);
                    firebaseUser.delete().addOnCompleteListener(task -> {
                        auth.signOut();
                        callback.onFailure(error);
                    });
                });
    }

    /** Logs in and loads the user's profile (FR 1.3). */
    public void login(String email, String password, DataCallback<User> callback) {
        auth.signInWithEmailAndPassword(email, password)
                .addOnSuccessListener(result -> {
                    FirebaseUser firebaseUser = result.getUser();
                    if (firebaseUser == null) {
                        callback.onFailure(new RepositoryException(FirebaseErrors.GENERIC));
                        return;
                    }
                    loadProfile(firebaseUser, callback);
                })
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    /** Loads the profile of the user who is already logged in. */
    public void getCurrentUser(DataCallback<User> callback) {
        FirebaseUser firebaseUser = auth.getCurrentUser();
        if (firebaseUser == null) {
            callback.onFailure(new RepositoryException("Please log in again."));
            return;
        }
        loadProfile(firebaseUser, callback);
    }

    private void loadProfile(FirebaseUser firebaseUser, DataCallback<User> callback) {
        db.collection(FirestoreMapper.USERS).document(firebaseUser.getUid()).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        callback.onSuccess(FirestoreMapper.toUser(snapshot));
                        return;
                    }
                    // Registration was interrupted before the profile was written. Remove the
                    // incomplete login so the person can sign up again with the same email.
                    firebaseUser.delete().addOnCompleteListener(task -> {
                        auth.signOut();
                        callback.onFailure(new RepositoryException(
                                "Your registration did not finish. Please create your account again."));
                    });
                })
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    /** Ends the session (FR 1.3). */
    public void logout() {
        auth.signOut();
    }

    private static RepositoryException registrationError(Exception e) {
        if (e instanceof FirebaseAuthInvalidCredentialsException
                && !(e instanceof FirebaseAuthWeakPasswordException)) {
            return new RepositoryException("That email address is not valid.", e);
        }
        return FirebaseErrors.wrap(e);
    }

    private static boolean isUscIdTaken(Throwable e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof UscIdTakenException) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    /** Thrown inside the registration transaction to abort it without retries. */
    private static final class UscIdTakenException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
