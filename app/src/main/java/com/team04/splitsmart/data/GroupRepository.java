package com.team04.splitsmart.data;

import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.WriteBatch;
import com.team04.splitsmart.model.Group;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Creates, reads, edits and deletes groups in {@code groups/{groupId}} (FR 1.4 to FR 1.7). */
public class GroupRepository {
    /** Firestore allows at most 500 writes in one batch. */
    private static final int MAX_BATCH_WRITES = 450;

    private final FirebaseFirestore db;

    public GroupRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public static GroupRepository create() {
        return new GroupRepository(FirebaseFirestore.getInstance());
    }

    /** Creates a group owned by {@code ownerId}, who is also its first member (FR 1.4). */
    public void createGroup(String name, String description, String ownerId, long nowMillis,
                            DataCallback<Group> callback) {
        DocumentReference ref = db.collection(FirestoreMapper.GROUPS).document();
        List<String> members = new ArrayList<>();
        members.add(ownerId);
        Group group = new Group(ref.getId(), name, description, ownerId, members, nowMillis);
        ref.set(FirestoreMapper.groupToMap(group))
                .addOnSuccessListener(ignored -> callback.onSuccess(group))
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    /** Loads one group. Fails with a clear message if it was deleted. */
    public void getGroup(String groupId, DataCallback<Group> callback) {
        db.collection(FirestoreMapper.GROUPS).document(groupId).get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot.exists()) {
                        callback.onSuccess(FirestoreMapper.toGroup(snapshot));
                    } else {
                        callback.onFailure(new RepositoryException(
                                "This group no longer exists.", true, null));
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    /** Every group the user belongs to, newest first (FR 1.6). */
    public void getGroupsForUser(String uid, DataCallback<List<Group>> callback) {
        db.collection(FirestoreMapper.GROUPS)
                .whereArrayContains(FirestoreMapper.MEMBER_IDS, uid)
                .get()
                .addOnSuccessListener(query -> {
                    List<Group> groups = new ArrayList<>();
                    for (DocumentSnapshot snapshot : query.getDocuments()) {
                        groups.add(FirestoreMapper.toGroup(snapshot));
                    }
                    // Sorted here instead of with orderBy() so no composite index is needed.
                    Collections.sort(groups,
                            (a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));
                    callback.onSuccess(groups);
                })
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    /** Owner-only edit of the name and description (FR 1.7). */
    public void updateGroup(String groupId, String name, String description,
                            DataCallback<Void> callback) {
        Map<String, Object> changes = new HashMap<>();
        changes.put(FirestoreMapper.NAME, name);
        changes.put(FirestoreMapper.DESCRIPTION, description);
        db.collection(FirestoreMapper.GROUPS).document(groupId).update(changes)
                .addOnSuccessListener(ignored -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    /** Owner-only: adds a registered user to the group (FR 1.5). */
    public void addMember(String groupId, String uid, DataCallback<Void> callback) {
        db.collection(FirestoreMapper.GROUPS).document(groupId)
                .update(FirestoreMapper.MEMBER_IDS, FieldValue.arrayUnion(uid))
                .addOnSuccessListener(ignored -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    /**
     * Owner-only: deletes the group together with all of its expenses and settlements (FR 1.7).
     * Transactions are removed in batches and the group document goes in the final batch.
     */
    public void deleteGroup(String groupId, DataCallback<Void> callback) {
        DocumentReference groupRef = db.collection(FirestoreMapper.GROUPS).document(groupId);
        groupRef.collection(FirestoreMapper.TRANSACTIONS).get()
                .addOnSuccessListener(query -> {
                    List<DocumentReference> refs = new ArrayList<>();
                    for (DocumentSnapshot snapshot : query.getDocuments()) {
                        refs.add(snapshot.getReference());
                    }
                    refs.add(groupRef);
                    commitDeletes(refs, 0, callback);
                })
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    private void commitDeletes(List<DocumentReference> refs, int start, DataCallback<Void> callback) {
        int end = Math.min(start + MAX_BATCH_WRITES, refs.size());
        WriteBatch batch = db.batch();
        for (int i = start; i < end; i++) {
            batch.delete(refs.get(i));
        }
        batch.commit()
                .addOnSuccessListener(ignored -> {
                    if (end < refs.size()) {
                        commitDeletes(refs, end, callback);
                    } else {
                        callback.onSuccess(null);
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }
}
