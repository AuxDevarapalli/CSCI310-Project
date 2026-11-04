package com.team04.splitsmart.data;

import com.google.firebase.firestore.Blob;
import com.google.firebase.firestore.DocumentSnapshot;
import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Share;
import com.team04.splitsmart.model.SplitMethod;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.TransactionType;
import com.team04.splitsmart.model.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts between domain objects and Firestore documents (design section 5.3). Field names
 * live here so the schema is defined in exactly one place.
 */
final class FirestoreMapper {
    // Collections
    static final String USERS = "users";
    static final String USC_IDS = "uscIds";
    static final String GROUPS = "groups";
    static final String TRANSACTIONS = "transactions";

    // users/{uid}
    static final String NAME = "name";
    static final String USC_EMAIL = "uscEmail";
    static final String USC_ID = "uscId";
    static final String CREATED_AT = "createdAt";
    // uscIds/{uscId}
    static final String UID = "uid";
    // groups/{groupId}
    static final String DESCRIPTION = "description";
    static final String OWNER_ID = "ownerId";
    static final String MEMBER_IDS = "memberIds";
    // groups/{groupId}/transactions/{id}
    static final String TYPE = "type";
    static final String GROUP_PHOTO = "photo";
    static final String CREATOR_ID = "creatorId";
    static final String DATE = "date";
    static final String NOTE = "note";
    static final String UPDATED_AT = "updatedAt";
    static final String TITLE = "title";
    static final String TOTAL_CENTS = "totalCents";
    static final String CATEGORY = "category";
    static final String PAYER_ID = "payerId";
    static final String SPLIT_METHOD = "splitMethod";
    static final String SHARES = "shares";
    static final String PARTICIPANT_IDS = "participantIds";
    static final String PARTICIPANT_ID = "participantId";
    static final String AMOUNT_CENTS = "amountCents";
    static final String PERCENT_BASIS_POINTS = "percentBasisPoints";
    static final String RECEIVER_ID = "receiverId";

    private FirestoreMapper() {
    }

    // ---- Users ----

    static Map<String, Object> userToMap(User user, long createdAt) {
        Map<String, Object> map = new HashMap<>();
        map.put(NAME, user.getName());
        map.put(USC_EMAIL, user.getUscEmail());
        map.put(USC_ID, user.getUscId());
        map.put(CREATED_AT, createdAt);
        return map;
    }

    static User toUser(DocumentSnapshot doc) {
        return new User(doc.getId(), doc.getString(NAME), doc.getString(USC_EMAIL),
                doc.getString(USC_ID));
    }

    static Map<String, Object> uscIdToMap(String uid) {
        Map<String, Object> map = new HashMap<>();
        map.put(UID, uid);
        return map;
    }

    // ---- Groups ----

    static Map<String, Object> groupToMap(Group group) {
        Map<String, Object> map = new HashMap<>();
        map.put(NAME, group.getName());
        map.put(DESCRIPTION, group.getDescription());
        map.put(OWNER_ID, group.getOwnerId());
        map.put(MEMBER_IDS, new ArrayList<>(group.getMemberIds()));
        map.put(CREATED_AT, group.getCreatedAt());
        return map;
    }

    static Group toGroup(DocumentSnapshot doc) {
        Object photo = doc.get(GROUP_PHOTO);
        return new Group(doc.getId(), doc.getString(NAME), doc.getString(DESCRIPTION),
                doc.getString(OWNER_ID), asStringList(doc.get(MEMBER_IDS)),
                asLong(doc.get(CREATED_AT)), photo instanceof Blob ? ((Blob) photo).toBytes() : null);
    }

    // ---- Transactions ----

    static Map<String, Object> expenseToMap(Expense expense, long updatedAt) {
        Map<String, Object> map = baseTransactionMap(expense, updatedAt);
        map.put(TITLE, expense.getTitle());
        map.put(TOTAL_CENTS, expense.getTotalCents());
        map.put(CATEGORY, expense.getCategory().name());
        map.put(PAYER_ID, expense.getPayerId());
        map.put(SPLIT_METHOD, expense.getSplitMethod().name());
        List<Map<String, Object>> shares = new ArrayList<>();
        for (Share share : expense.getShares()) {
            Map<String, Object> shareMap = new HashMap<>();
            shareMap.put(PARTICIPANT_ID, share.getParticipantId());
            shareMap.put(AMOUNT_CENTS, share.getAmountCents());
            shareMap.put(PERCENT_BASIS_POINTS, (long) share.getPercentBasisPoints());
            shares.add(shareMap);
        }
        map.put(SHARES, shares);
        map.put(PARTICIPANT_IDS, expense.getParticipantIds());
        return map;
    }

    static Map<String, Object> settlementToMap(Settlement settlement, long updatedAt) {
        Map<String, Object> map = baseTransactionMap(settlement, updatedAt);
        map.put(PAYER_ID, settlement.getPayerId());
        map.put(RECEIVER_ID, settlement.getReceiverId());
        map.put(AMOUNT_CENTS, settlement.getAmountCents());
        return map;
    }

    private static Map<String, Object> baseTransactionMap(Transaction transaction, long updatedAt) {
        Map<String, Object> map = new HashMap<>();
        map.put(TYPE, transaction.getType().name());
        map.put(CREATOR_ID, transaction.getCreatorId());
        map.put(DATE, transaction.getDate());
        map.put(CREATED_AT, transaction.getCreatedAt());
        map.put(UPDATED_AT, updatedAt);
        map.put(NOTE, transaction.getNote());
        return map;
    }

    /** Parses a transaction document; returns {@code null} for unknown or malformed types. */
    static Transaction toTransaction(DocumentSnapshot doc, String groupId) {
        TransactionType type = TransactionType.fromName(doc.getString(TYPE));
        if (type == null) {
            return null;
        }
        String id = doc.getId();
        String creatorId = doc.getString(CREATOR_ID);
        String date = doc.getString(DATE);
        long createdAt = asLong(doc.get(CREATED_AT));
        String note = doc.getString(NOTE);
        if (type == TransactionType.SETTLEMENT) {
            return new Settlement(id, groupId, creatorId, date, createdAt, note,
                    doc.getString(PAYER_ID), doc.getString(RECEIVER_ID),
                    asLong(doc.get(AMOUNT_CENTS)));
        }
        List<Share> shares = new ArrayList<>();
        Object rawShares = doc.get(SHARES);
        if (rawShares instanceof List) {
            for (Object item : (List<?>) rawShares) {
                if (item instanceof Map) {
                    Map<?, ?> shareMap = (Map<?, ?>) item;
                    Object participant = shareMap.get(PARTICIPANT_ID);
                    if (participant instanceof String) {
                        shares.add(new Share((String) participant,
                                asLong(shareMap.get(AMOUNT_CENTS)),
                                (int) asLong(shareMap.get(PERCENT_BASIS_POINTS))));
                    }
                }
            }
        }
        return new Expense(id, groupId, creatorId, date, createdAt, note,
                doc.getString(TITLE), asLong(doc.get(TOTAL_CENTS)),
                Category.fromName(doc.getString(CATEGORY)), doc.getString(PAYER_ID),
                SplitMethod.fromName(doc.getString(SPLIT_METHOD)), shares);
    }

    // ---- Helpers ----

    static long asLong(Object value) {
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return 0;
    }

    static List<String> asStringList(Object value) {
        List<String> list = new ArrayList<>();
        if (value instanceof List) {
            for (Object item : (List<?>) value) {
                if (item instanceof String) {
                    list.add((String) item);
                }
            }
        }
        return list;
    }
}
