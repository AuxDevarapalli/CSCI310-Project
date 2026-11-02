package com.team04.splitsmart.viewmodel;

import com.team04.splitsmart.model.Transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Finds users who appear in a group's transactions but are no longer members (they left or were
 * removed), so screens can still show their names instead of "Unknown user".
 */
final class FormerMembers {
    private FormerMembers() {
    }

    static List<String> missingUserIds(List<? extends Transaction> transactions,
                                       Collection<String> knownIds) {
        Set<String> missing = new LinkedHashSet<>();
        for (Transaction transaction : transactions) {
            for (String uid : transaction.getInvolvedUserIds()) {
                if (uid != null && !knownIds.contains(uid)) {
                    missing.add(uid);
                }
            }
            String creator = transaction.getCreatorId();
            if (creator != null && !knownIds.contains(creator)) {
                missing.add(creator);
            }
        }
        return new ArrayList<>(missing);
    }
}
