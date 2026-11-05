package com.team04.splitsmart.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.firebase.FirebaseNetworkException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.auth.FirebaseAuthInvalidUserException;
import com.google.firebase.auth.FirebaseAuthUserCollisionException;
import com.google.firebase.auth.FirebaseAuthWeakPasswordException;
import com.google.firebase.firestore.FirebaseFirestoreException;

import org.junit.Test;

public class FirebaseErrorsTest {

    @Test
    public void messageFor_handlesNetworkException() {
        FirebaseNetworkException e = new FirebaseNetworkException("offline");
        assertEquals(FirebaseErrors.OFFLINE, FirebaseErrors.messageFor(e));
    }

    @Test
    public void messageFor_handlesTooManyRequests() {
        FirebaseTooManyRequestsException e = new FirebaseTooManyRequestsException("rate limit");
        assertEquals("Too many attempts. Please wait a moment and try again.",
                FirebaseErrors.messageFor(e));
    }

    @Test
    public void messageFor_handlesAuthCollisions() {
        FirebaseAuthUserCollisionException e =
                new FirebaseAuthUserCollisionException("collision", "email taken");
        assertEquals("An account with this USC email already exists.",
                FirebaseErrors.messageFor(e));
    }

    @Test
    public void messageFor_handlesWeakPassword() {
        FirebaseAuthWeakPasswordException e =
                new FirebaseAuthWeakPasswordException("weak", "reason", "password");
        assertEquals("That password is too weak. Use at least 6 characters.",
                FirebaseErrors.messageFor(e));
    }

    @Test
    public void messageFor_handlesInvalidCredentials() {
        FirebaseAuthInvalidUserException e1 =
                new FirebaseAuthInvalidUserException("code", "no user");
        FirebaseAuthInvalidCredentialsException e2 =
                new FirebaseAuthInvalidCredentialsException("code", "bad pass");

        assertEquals("Incorrect email or password.", FirebaseErrors.messageFor(e1));
        assertEquals("Incorrect email or password.", FirebaseErrors.messageFor(e2));
    }

    @Test
    public void messageFor_handlesFirestoreExceptions() {
        FirebaseFirestoreException unavailable = new FirebaseFirestoreException("offline",
                FirebaseFirestoreException.Code.UNAVAILABLE);
        FirebaseFirestoreException denied = new FirebaseFirestoreException("denied",
                FirebaseFirestoreException.Code.PERMISSION_DENIED);
        FirebaseFirestoreException notFound = new FirebaseFirestoreException("missing",
                FirebaseFirestoreException.Code.NOT_FOUND);

        assertEquals(FirebaseErrors.OFFLINE, FirebaseErrors.messageFor(unavailable));
        assertEquals("You don't have permission to do that.", FirebaseErrors.messageFor(denied));
        assertEquals("That item no longer exists.", FirebaseErrors.messageFor(notFound));
    }

    @Test
    public void messageFor_returnsGenericMessageForUnknownException() {
        Exception e = new RuntimeException("random error");
        assertEquals(FirebaseErrors.GENERIC, FirebaseErrors.messageFor(e));
    }

    @Test
    public void wrap_preservesRepositoryExceptionAndSetsNotFoundFlag() {
        RepositoryException custom = new RepositoryException("Custom", true, null);
        assertEquals(custom, FirebaseErrors.wrap(custom));

        FirebaseFirestoreException notFound = new FirebaseFirestoreException("missing",
                FirebaseFirestoreException.Code.NOT_FOUND);
        RepositoryException wrappedNotFound = FirebaseErrors.wrap(notFound);
        assertTrue(wrappedNotFound.isNotFound());
        assertEquals("That item no longer exists.", wrappedNotFound.getMessage());

        FirebaseNetworkException network = new FirebaseNetworkException("offline");
        RepositoryException wrappedNetwork = FirebaseErrors.wrap(network);
        assertFalse(wrappedNetwork.isNotFound());
        assertEquals(FirebaseErrors.OFFLINE, wrappedNetwork.getMessage());
    }
}
