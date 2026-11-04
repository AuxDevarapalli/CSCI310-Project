package com.team04.splitsmart.logic.history;

import com.team04.splitsmart.logic.validation.Money;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Share;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.User;

import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/** Spreadsheet-friendly export of the transactions currently shown in History. */
public final class HistoryCsv {
    private HistoryCsv() {
    }

    public static String encode(List<? extends Transaction> transactions, Map<String, User> users,
                                String unknownMember) {
        StringBuilder csv = new StringBuilder();
        CsvRows.appendRow(csv, "Date", "Type", "Title", "Amount (USD)", "Category", "Paid by",
                "Received by", "Split method", "Assigned shares (USD)", "Note");
        for (Transaction transaction : transactions) {
            String amount = Money.formatPlain(transaction.getAmountCents());
            if (transaction instanceof Expense) {
                Expense expense = (Expense) transaction;
                StringJoiner shares = new StringJoiner("; ");
                for (Share share : expense.getShares()) {
                    shares.add(nameOf(users, share.getParticipantId(), unknownMember)
                            + " = " + Money.formatPlain(share.getAmountCents()));
                }
                CsvRows.appendRow(csv, expense.getDate(), "Expense", expense.getTitle(), amount,
                        expense.getCategory().getDisplayName(),
                        nameOf(users, expense.getPayerId(), unknownMember), "",
                        expense.getSplitMethod().getDisplayName(), shares.toString(), expense.getNote());
            } else if (transaction instanceof Settlement) {
                Settlement settlement = (Settlement) transaction;
                CsvRows.appendRow(csv, settlement.getDate(), "Settlement", "Settlement", amount, "",
                        nameOf(users, settlement.getPayerId(), unknownMember),
                        nameOf(users, settlement.getReceiverId(), unknownMember),
                        "", "", settlement.getNote());
            }
        }
        return csv.toString();
    }

    private static String nameOf(Map<String, User> users, String uid, String fallback) {
        User user = users.get(uid);
        return user == null ? fallback : user.getName();
    }

}
