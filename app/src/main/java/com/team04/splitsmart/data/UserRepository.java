package com.team04.splitsmart.data;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.team04.splitsmart.model.User;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Reads and updates user profiles in {@code users/{uid}} (FR 1.3, FR 1.5, FR 1.6). */
public class UserRepository {
    private final FirebaseFirestore db;

    public UserRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public static UserRepository create() {
        return new UserRepository(FirebaseFirestore.getInstance());
    }

    public void getUser(String uid, DataCallback<User> callback) {
        db.collection(FirestoreMapper.USERS).document(uid).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        callback.onSuccess(FirestoreMapper.toUser(snapshot));
                    } else {
                        callback.onFailure(new RepositoryException("That user no longer exists."));
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    /**
     * Loads several users, keeping the order of {@code uids}. A missing profile is replaced by a
     * placeholder so one bad record cannot break a whole screen.
     */
    public void getUsers(List<String> uids, DataCallback<List<User>> callback) {
        if (uids == null || uids.isEmpty()) {
            callback.onSuccess(Collections.emptyList());
            return;
        }
        List<Task<DocumentSnapshot>> reads = new ArrayList<>();
        for (String uid : uids) {
            reads.add(db.collection(FirestoreMapper.USERS).document(uid).get());
        }
        Tasks.<DocumentSnapshot>whenAllSuccess(reads)
                .addOnSuccessListener(snapshots -> {
                    List<User> users = new ArrayList<>();
                    for (int i = 0; i < uids.size(); i++) {
                        DocumentSnapshot snapshot = snapshots.get(i);
                        users.add(snapshot.exists()
                                ? FirestoreMapper.toUser(snapshot)
                                : new User(uids.get(i), "Unknown user", "", ""));
                    }
                    callback.onSuccess(users);
                })
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    /**
     * Finds the account registered with a USC email (FR 1.5). Calls {@code onSuccess(null)} when
     * nobody has registered that email.
     *
     * @param email already lower-cased email
     */
    public void findByEmail(String email, DataCallback<User> callback) {
        db.collection(FirestoreMapper.USERS)
                .whereEqualTo(FirestoreMapper.USC_EMAIL, email)
                .limit(1)
                .get()
                .addOnSuccessListener(query -> {
                    if (query.isEmpty()) {
                        callback.onSuccess(null);
                    } else {
                        callback.onSuccess(FirestoreMapper.toUser(query.getDocuments().get(0)));
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    /**
     * Updates only the name (FR 1.3). The USC email and USC ID are never written after
     * registration, and the Firestore rules reject any attempt to change them.
     */
    public void updateName(User user, String newName, DataCallback<User> callback) {
        db.collection(FirestoreMapper.USERS).document(user.getUid())
                .update(FirestoreMapper.NAME, newName)
                .addOnSuccessListener(ignored -> callback.onSuccess(user.withName(newName)))
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }
}
