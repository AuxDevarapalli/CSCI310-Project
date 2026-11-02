package com.team04.splitsmart.data;

import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SnapshotMetadata;

/**
 * A live listener started by a repository's {@code observe...} method. ViewModels keep it and
 * call {@link #remove()} in {@code onCleared()}, so no Firebase type leaves the data layer.
 */
public final class Subscription {
    private final ListenerRegistration registration;

    Subscription(ListenerRegistration registration) {
        this.registration = registration;
    }

    public void remove() {
        registration.remove();
    }

    /** Reads the metadata of a document or query snapshot. */
    interface MetadataOf<T> {
        SnapshotMetadata get(T snapshot);
    }

    /**
     * Wraps a change callback for {@code addSnapshotListener}. The first snapshot (the data the
     * screen just loaded) and this device's own unsaved writes are ignored, so {@code onChange}
     * only runs when something changed in the database, usually on another member's phone.
     * An error (for example losing access after being removed) is also reported as a change,
     * so the screen reloads and shows what happened.
     */
    static <T> EventListener<T> changesOnly(MetadataOf<T> metadata, Runnable onChange) {
        boolean[] first = {true};
        return (snapshot, error) -> {
            if (error != null) {
                onChange.run();
                return;
            }
            if (first[0]) {
                first[0] = false;
                return;
            }
            if (snapshot != null && !metadata.get(snapshot).hasPendingWrites()) {
                onChange.run();
            }
        };
    }
}
