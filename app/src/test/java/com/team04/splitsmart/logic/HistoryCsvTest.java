package com.team04.splitsmart.logic;

import static com.team04.splitsmart.logic.TestData.ALICE;
import static com.team04.splitsmart.logic.TestData.BOB;
import static com.team04.splitsmart.logic.TestData.GROUP_ID;
import static com.team04.splitsmart.logic.TestData.expenseWithNote;
import static com.team04.splitsmart.logic.TestData.user;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.team04.splitsmart.logic.history.HistoryCsv;
import com.team04.splitsmart.logic.history.HistoryFilter;
import com.team04.splitsmart.logic.history.HistoryQuery;
import com.team04.splitsmart.logic.history.SortOrder;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Transaction;
import com.team04.splitsmart.model.User;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HistoryCsvTest {
    private static final String HEADER = "\"Date\",\"Type\",\"Title\",\"Amount (USD)\",\"Category\","
            + "\"Paid by\",\"Received by\",\"Split method\",\"Assigned shares (USD)\",\"Note\"\r\n";

    @Test
    public void includesActualPayerRecipientAndSharesButNoAccountIdentifiers() {
        Expense expense = expenseWithNote("Groceries", "Picked up by Ana", 1);
        Settlement received = new Settlement("s1", GROUP_ID, ALICE, "2026-09-18", 2, "Cash",
                BOB, ALICE, 125);
        Map<String, User> users = new HashMap<>();
        users.put(ALICE, new User(ALICE, "Ana", "private@usc.edu", "1234567890"));
        users.put(BOB, new User(BOB, "Bo", "another@usc.edu", "5555555555"));

        String csv = HistoryCsv.encode(Arrays.asList(received, expense), users, "Unknown member");

        assertEquals(HEADER
                + "\"2026-09-18\",\"Settlement\",\"Settlement\",\"1.25\",\"\",\"Bo\",\"Ana\",\"\",\"\",\"Cash\"\r\n"
                + "\"2026-09-17\",\"Expense\",\"Groceries\",\"10.00\",\"Food\",\"Ana\",\"\",\"Equal split\","
                + "\"Ana = 5.00; Bo = 5.00\",\"Picked up by Ana\"\r\n", csv);
        assertFalse(csv.contains("@usc.edu"));
        assertFalse(csv.contains("1234567890"));
    }

    @Test
    public void escapesQuotesCommasAndMultilineNotesWithoutDiscardingUnicode() {
        Expense expense = expenseWithNote("Café, \"La Plaza\"", "Line 1\nLine 2: $5", 1);
        Map<String, User> users = Collections.singletonMap(ALICE,
                new User(ALICE, "Zoë", "private@usc.edu", "1234567890"));

        String csv = HistoryCsv.encode(Collections.singletonList(expense), users, "Unknown member");

        assertTrue(csv.contains("\"Café, \"\"La Plaza\"\"\""));
        assertTrue(csv.contains("\"Line 1\nLine 2: $5\""));
        assertTrue(csv.contains("\"Zoë = 5.00; Unknown member = 5.00\""));
    }

    @Test
    public void protectsFormulaLikeTitlesNamesAndNotes() {
        Expense expense = expenseWithNote("=SUM(1,2)", "@SUM(3,4)", 1);
        Map<String, User> users = Collections.singletonMap(ALICE,
                new User(ALICE, "+1+2", "private@usc.edu", "1234567890"));

        String csv = HistoryCsv.encode(Collections.singletonList(expense), users, "Unknown member");

        assertTrue(csv.contains("\"'=SUM(1,2)\""));
        assertTrue(csv.contains("\"'+1+2\""));
        assertTrue(csv.contains("\"'@SUM(3,4)\""));
        assertTrue(csv.contains("\"'+1+2 = 5.00; Unknown member = 5.00\""));
    }

    @Test
    public void protectsFormulaPrefixesWithLeadingUnicodeWhitespace() {
        Expense expense = expenseWithNote("Formula", "\u00a0\u200b-1+2", 1);

        String csv = HistoryCsv.encode(Collections.singletonList(expense),
                Collections.emptyMap(), "Unknown member");

        assertTrue(csv.contains("\"'\u00a0\u200b-1+2\""));
    }

    @Test
    public void keepsFilterResultsAndDisplayedSortOrder() {
        Expense first = expenseWithNote("Food: first", "", 1);
        Expense last = expenseWithNote("Food: last", "", 2);
        Expense hidden = expenseWithNote("Transit", "", 3);
        HistoryQuery query = HistoryQuery.defaults();
        query.setKeyword("Food");
        query.setSortOrder(SortOrder.OLDEST_FIRST);
        List<Transaction> matching = new HistoryFilter().apply(Arrays.asList(last, hidden, first), query);

        String csv = HistoryCsv.encode(matching, Collections.singletonMap(ALICE, user(ALICE)),
                "Unknown member");

        assertTrue(csv.indexOf("Food: first") >= 0);
        assertTrue(csv.indexOf("Food: last") > csv.indexOf("Food: first"));
        assertFalse(csv.contains("Transit"));
    }

    @Test
    public void emptyExportHasJustTheColumnHeaders() {
        assertEquals(HEADER, HistoryCsv.encode(Collections.emptyList(),
                Collections.emptyMap(), "Unknown member"));
    }
}
