package com.team04.splitsmart.model;

import java.util.ArrayList;
import java.util.List;

/**
 * A payment made outside the app (for example through Venmo or Zelle) from a member who owes
 * money to the member they owe (FR 5.1). Settlements reduce the pairwise balance between the
 * two users but are never part of the monthly expense summary (FR 5.7).
 */
public class Settlement extends Transaction {
    private final String payerId;
    private final String receiverId;
    private final long amountCents;

    public Settlement(String id, String groupId, String creatorId, String date, long createdAt,
                      String note, String payerId, String receiverId, long amountCents) {
        super(id, groupId, creatorId, date, createdAt, note);
        this.payerId = payerId;
        this.receiverId = receiverId;
        this.amountCents = amountCents;
    }

    @Override
    public TransactionType getType() {
        return TransactionType.SETTLEMENT;
    }

    @Override
    public long getAmountCents() {
        return amountCents;
    }

    /** The user who paid (and who owed the money). */
    public String getPayerId() {
        return payerId;
    }

    /** The user who received the payment. */
    public String getReceiverId() {
        return receiverId;
    }

    @Override
    public List<String> getInvolvedUserIds() {
        List<String> ids = new ArrayList<>(2);
        if (payerId != null) {
            ids.add(payerId);
        }
        if (receiverId != null && !receiverId.equals(payerId)) {
            ids.add(receiverId);
        }
        return ids;
    }
}
