package com.team04.splitsmart.logic;

import static com.team04.splitsmart.logic.TestData.ALICE;
import static com.team04.splitsmart.logic.TestData.BOB;
import static com.team04.splitsmart.logic.TestData.CAROL;
import static com.team04.splitsmart.logic.TestData.DAVID;
import static com.team04.splitsmart.logic.TestData.expense;
import static com.team04.splitsmart.logic.TestData.settlement;
import static com.team04.splitsmart.logic.TestData.share;
import static com.team04.splitsmart.logic.TestData.users;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.team04.splitsmart.logic.balance.BalanceCalculator;
import com.team04.splitsmart.logic.balance.BalanceSummary;
import com.team04.splitsmart.logic.balance.PairBalance;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Transaction;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** FR 3.7 and FR 4.1 to FR 4.5, using the customer's own examples. */
public class BalanceCalculatorTest {
    private final BalanceCalculator calculator = new BalanceCalculator();

    @Test
    public void payerOwesNothingForOwnShare() {
        // Alice pays $30.00 for Alice, Bob and Carol: Bob and Carol each owe Alice $10.00.
        List<Transaction> txs = Collections.singletonList(
                expense(ALICE, share(ALICE, 1000), share(BOB, 1000), share(CAROL, 1000)));
        assertEquals(1000, calculator.netBetween(ALICE, BOB, txs));
        assertEquals(1000, calculator.netBetween(ALICE, CAROL, txs));
        assertEquals(-1000, calculator.netBetween(BOB, ALICE, txs));
        assertEquals(0, calculator.netBetween(BOB, CAROL, txs));
    }

    @Test
    public void individualBalanceCombinesEveryTransactionBetweenThePair() {
        // Alice owes Bob $10 and $5 from two expenses, Bob owes Alice $4 -> Alice owes Bob $11.
        List<Transaction> txs = Arrays.asList(
                expense(BOB, share(BOB, 1000), share(ALICE, 1000)),
                expense(BOB, share(BOB, 500), share(ALICE, 500)),
                expense(ALICE, share(ALICE, 400), share(BOB, 400)));
        assertEquals(-1100, calculator.netBetween(ALICE, BOB, txs));
        assertEquals(1100, calculator.netBetween(BOB, ALICE, txs));
    }

    @Test
    public void totalsAreKeptSeparateAndNeverNetted() {
        // Bob owes Alice $10, Carol owes Alice $5, Alice owes David $4:
        // receive $15.00 and pay $4.00, not a single net $11.00.
        List<Transaction> txs = Arrays.asList(
                expense(ALICE, share(ALICE, 0), share(BOB, 1000)),
                expense(ALICE, share(ALICE, 0), share(CAROL, 500)),
                expense(DAVID, share(DAVID, 0), share(ALICE, 400)));
        BalanceSummary summary = calculator.computeSummary(ALICE,
                users(ALICE, BOB, CAROL, DAVID), txs);
        assertEquals(1500, summary.getTotalToReceiveCents());
        assertEquals(400, summary.getTotalToPayCents());
        assertEquals(3, summary.getPairs().size());
    }

    @Test
    public void debtsAreNeverSimplifiedAcrossPairs() {
        // Alice owes Bob $10 and Bob owes Carol $10: both balances stay, Alice/Carol stays 0.
        List<Transaction> txs = Arrays.asList(
                expense(BOB, share(BOB, 0), share(ALICE, 1000)),
                expense(CAROL, share(CAROL, 0), share(BOB, 1000)));
        assertEquals(-1000, calculator.netBetween(ALICE, BOB, txs));
        assertEquals(-1000, calculator.netBetween(BOB, CAROL, txs));
        assertEquals(0, calculator.netBetween(ALICE, CAROL, txs));

        BalanceSummary bob = calculator.computeSummary(BOB, users(ALICE, BOB, CAROL), txs);
        assertEquals(1000, bob.getTotalToReceiveCents());
        assertEquals(1000, bob.getTotalToPayCents());
    }

    @Test
    public void settlementsReduceTheBalance() {
        List<Transaction> txs = Arrays.asList(
                expense(BOB, share(BOB, 1100), share(ALICE, 1100)),
                settlement(ALICE, BOB, 500));
        assertEquals(-600, calculator.netBetween(ALICE, BOB, txs));
        assertEquals(600, calculator.amountOwed(ALICE, BOB, txs));
        assertEquals(0, calculator.amountOwed(BOB, ALICE, txs));
    }

    @Test
    public void recipientRecordedSettlementUsesActualPayerForBalance() {
        Transaction expense = expense(ALICE, share(ALICE, 0), share(BOB, 1100));
        Settlement received = new Settlement("payment", expense.getGroupId(), ALICE,
                "2026-11-03", 2, "", BOB, ALICE, 500);

        List<Transaction> transactions = Arrays.asList(expense, received);
        assertEquals(600, calculator.amountOwed(BOB, ALICE, transactions));
        assertEquals(0, calculator.amountOwed(ALICE, BOB, transactions));
    }

    @Test
    public void fullSettlementClearsTheBalance() {
        List<Transaction> txs = Arrays.asList(
                expense(BOB, share(BOB, 1100), share(ALICE, 1100)),
                settlement(ALICE, BOB, 1100));
        assertEquals(0, calculator.netBetween(ALICE, BOB, txs));
        BalanceSummary summary = calculator.computeSummary(ALICE, users(ALICE, BOB), txs);
        assertTrue(summary.isAllSettled());
        assertTrue(summary.getPairs().get(0).isSettled());
    }

    @Test
    public void transactionsWithAThirdMemberDoNotAffectThePair() {
        List<Transaction> txs = Arrays.asList(
                expense(CAROL, share(CAROL, 500), share(DAVID, 500)),
                settlement(CAROL, DAVID, 500));
        assertEquals(0, calculator.netBetween(ALICE, BOB, txs));
    }

    @Test
    public void summaryListsEveryOtherMember() {
        BalanceSummary summary = calculator.computeSummary(ALICE,
                users(ALICE, BOB, CAROL), Collections.<Transaction>emptyList());
        assertEquals(2, summary.getPairs().size());
        for (PairBalance pair : summary.getPairs()) {
            assertTrue(pair.isSettled());
        }
        assertEquals(0, summary.getTotalToPayCents());
        assertEquals(0, summary.getTotalToReceiveCents());
    }

    @Test
    public void computeTotalsMatchesComputeSummary() {
        List<Transaction> txs = Arrays.asList(
                expense(ALICE, share(ALICE, 0), share(BOB, 1000)),
                expense(DAVID, share(DAVID, 0), share(ALICE, 400)),
                settlement(BOB, ALICE, 250));
        long[] totals = calculator.computeTotals(ALICE,
                Arrays.asList(ALICE, BOB, CAROL, DAVID), txs);
        BalanceSummary summary = calculator.computeSummary(ALICE,
                users(ALICE, BOB, CAROL, DAVID), txs);
        assertEquals(summary.getTotalToPayCents(), totals[0]);
        assertEquals(summary.getTotalToReceiveCents(), totals[1]);
        assertEquals(750, totals[1]);
    }
}
