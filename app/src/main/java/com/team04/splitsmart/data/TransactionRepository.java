package com.team04.splitsmart.data;

import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Transaction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Stores expenses and settlements in {@code groups/{groupId}/transactions/{id}}
 * (FR 2.1, FR 2.7, FR 2.9, FR 5.1, FR 5.3). Balances and summaries are never stored; they are
 * recomputed from these documents, so every create, edit and delete is reflected immediately.
 */
public class TransactionRepository {
    private final FirebaseFirestore db;

    public TransactionRepository(FirebaseFirestore db) {
        this.db = db;
    }

    public static TransactionRepository create() {
        return new TransactionRepository(FirebaseFirestore.getInstance());
    }

    private CollectionReference transactions(String groupId) {
        return db.collection(FirestoreMapper.GROUPS).document(groupId)
                .collection(FirestoreMapper.TRANSACTIONS);
    }

    /** Every expense and settlement in the group, in no particular order. */
    public void getTransactions(String groupId, DataCallback<List<Transaction>> callback) {
        transactions(groupId).get()
                .addOnSuccessListener(query -> {
                    List<Transaction> result = new ArrayList<>();
                    for (DocumentSnapshot snapshot : query.getDocuments()) {
                        Transaction transaction = FirestoreMapper.toTransaction(snapshot, groupId);
                        if (transaction != null) {
                            result.add(transaction);
                        }
                    }
                    callback.onSuccess(result);
                })
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    /** One transaction, for the detail screen and edit forms (FR 5.5). */
    public void getTransaction(String groupId, String transactionId,
                               DataCallback<Transaction> callback) {
        transactions(groupId).document(transactionId).get()
                .addOnSuccessListener(snapshot -> {
                    Transaction transaction = snapshot.exists()
                            ? FirestoreMapper.toTransaction(snapshot, groupId)
                            : null;
                    if (transaction == null) {
                        callback.onFailure(new RepositoryException(
                                "This transaction no longer exists.", true, null));
                    } else {
                        callback.onSuccess(transaction);
                    }
                })
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    /** Saves a new expense and returns its generated id (design Figure 4). */
    public void addExpense(Expense expense, long nowMillis, DataCallback<String> callback) {
        add(expense.getGroupId(), FirestoreMapper.expenseToMap(expense, nowMillis), callback);
    }

    /** Replaces an existing expense; only its creator may do this (FR 2.7). */
    public void updateExpense(Expense expense, long nowMillis, DataCallback<Void> callback) {
        update(expense.getGroupId(), expense.getId(),
                FirestoreMapper.expenseToMap(expense, nowMillis), callback);
    }

    /** Saves a new settlement and returns its generated id (design Figure 6). */
    public void addSettlement(Settlement settlement, long nowMillis, DataCallback<String> callback) {
        add(settlement.getGroupId(), FirestoreMapper.settlementToMap(settlement, nowMillis), callback);
    }

    /** Replaces an existing settlement; only its creator may do this (FR 5.3). */
    public void updateSettlement(Settlement settlement, long nowMillis,
                                 DataCallback<Void> callback) {
        update(settlement.getGroupId(), settlement.getId(),
                FirestoreMapper.settlementToMap(settlement, nowMillis), callback);
    }

    /** Deletes an expense or settlement (FR 2.7, FR 5.3). */
    public void deleteTransaction(String groupId, String transactionId,
                                  DataCallback<Void> callback) {
        transactions(groupId).document(transactionId).delete()
                .addOnSuccessListener(ignored -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    private void add(String groupId, Map<String, Object> data, DataCallback<String> callback) {
        DocumentReference ref = transactions(groupId).document();
        ref.set(data)
                .addOnSuccessListener(ignored -> callback.onSuccess(ref.getId()))
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }

    /** Uses update() rather than set() so editing a record someone just deleted fails cleanly. */
    private void update(String groupId, String transactionId, Map<String, Object> data,
                        DataCallback<Void> callback) {
        transactions(groupId).document(transactionId).update(data)
                .addOnSuccessListener(ignored -> callback.onSuccess(null))
                .addOnFailureListener(e -> callback.onFailure(FirebaseErrors.wrap(e)));
    }
}
