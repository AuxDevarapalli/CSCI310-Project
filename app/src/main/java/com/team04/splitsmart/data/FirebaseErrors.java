package com.team04.splitsmart.data;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.firestore.FirebaseFirestoreException;

/** Translates Firebase exceptions into {@link RepositoryException}s with friendly messages. */
final class FirebaseErrors {
    static final String OFFLINE =
            "You appear to be offline. Check your internet connection and try again.";
    static final String GENERIC = "Something went wrong. Please try again.";

    private FirebaseErrors() {
    }

    /** Wraps any exception from Firestore or Firebase Authentication. */
    static RepositoryException wrap(Exception e) {
        if (e instanceof RepositoryException) {
            return (RepositoryException) e;
        }
        return new RepositoryException(messageFor(e), isNotFound(e), e);
    }

    /** Missing documents and lost access are permanent, unlike being offline. */
    private static boolean isNotFound(Exception e) {
        if (e instanceof FirebaseFirestoreException) {
            FirebaseFirestoreException.Code code = ((FirebaseFirestoreException) e).getCode();
            return code == FirebaseFirestoreException.Code.NOT_FOUND
                    || code == FirebaseFirestoreException.Code.PERMISSION_DENIED;
        }
        return false;
    }

    static String messageFor(Exception e) {
        if (e instanceof FirebaseNetworkException) {
            return OFFLINE;
        }
        if (e instanceof FirebaseTooManyRequestsException) {
            return "Too many attempts. Please wait a moment and try again.";
        }
        if (e instanceof FirebaseAuthUserCollisionException) {
            return "An account with this USC email already exists.";
        }
        if (e instanceof FirebaseAuthWeakPasswordException) {
            return "That password is too weak. Use at least 6 characters.";
        }
        if (e instanceof FirebaseAuthInvalidUserException
                || e instanceof FirebaseAuthInvalidCredentialsException) {
            return "Incorrect email or password.";
        }
        if (e instanceof FirebaseFirestoreException) {
            switch (((FirebaseFirestoreException) e).getCode()) {
                case UNAVAILABLE:
                case DEADLINE_EXCEEDED:
                    return OFFLINE;
                case PERMISSION_DENIED:
                case UNAUTHENTICATED:
                    return "You don't have permission to do that.";
                case NOT_FOUND:
                    return "That item no longer exists.";
                default:
                    return GENERIC;
            }
        }
        return GENERIC;
    }
}
