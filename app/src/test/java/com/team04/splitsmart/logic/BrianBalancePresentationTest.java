package com.team04.splitsmart.logic;

import static org.junit.Assert.*;

import com.team04.splitsmart.logic.balance.BalanceSummary;
import com.team04.splitsmart.logic.balance.PairBalance;
import com.team04.splitsmart.model.User;

import org.junit.Test;

import java.util.Collections;

/** Project 2.4 white-box tests. Author: Brian Shi (brianshi@usc.edu). */
public class BrianBalancePresentationTest {

    private final User bob = new User("bob", "Bob", "bob@usc.edu", "1234567890");

    // BR-W01: Positive balance means the other member owes the current user.
    @Test
    public void positiveBalanceMeansOtherMemberOwesUser() {
        PairBalance pair = new PairBalance(bob, 1000);
        assertEquals(bob, pair.getOtherUser());
        assertEquals("bob", pair.getOtherUserId());
        assertEquals(1000, pair.getNetCents());
        assertTrue(pair.owesCurrentUser());
        assertFalse(pair.currentUserOwes());
        assertFalse(pair.isSettled());
        assertEquals(1000, pair.getAbsoluteCents());
    }

    // BR-W02: Negative balance means the current user owes the other member.
    @Test
    public void negativeBalanceMeansCurrentUserOwesOtherMember() {
        PairBalance pair = new PairBalance(bob, -400);
        assertEquals(-400, pair.getNetCents());
        assertFalse(pair.owesCurrentUser());
        assertTrue(pair.currentUserOwes());
        assertFalse(pair.isSettled());
        assertEquals(400, pair.getAbsoluteCents());
    }

    // BR-W03: Zero balance represents a settled relationship.
    @Test
    public void zeroBalanceIsSettled() {
        PairBalance pair = new PairBalance(bob, 0);
        assertEquals(0, pair.getNetCents());
        assertFalse(pair.owesCurrentUser());
        assertFalse(pair.currentUserOwes());
        assertTrue(pair.isSettled());
        assertEquals(0, pair.getAbsoluteCents());
    }

    // BR-W04: Summary defensively treats null pairs list as an empty list.
    @Test
    public void summaryTreatsNullPairsAsEmpty() {
        BalanceSummary summary = new BalanceSummary(100, 200, null);
        assertEquals(100, summary.getTotalToPayCents());
        assertEquals(200, summary.getTotalToReceiveCents());
        assertNotNull(summary.getPairs());
        assertTrue(summary.getPairs().isEmpty());
        assertFalse(summary.isAllSettled());
    }

    // BR-W05: Summary is settled only when both pay and receive totals are zero.
    @Test
    public void summaryIsSettledOnlyWhenBothTotalsAreZero() {
        BalanceSummary settled = new BalanceSummary(0, 0, Collections.emptyList());
        assertTrue(settled.isAllSettled());

        BalanceSummary payOnly = new BalanceSummary(500, 0, Collections.emptyList());
        assertFalse(payOnly.isAllSettled());

        BalanceSummary receiveOnly = new BalanceSummary(0, 500, Collections.emptyList());
        assertFalse(receiveOnly.isAllSettled());

        BalanceSummary bothNonZero = new BalanceSummary(300, 400, Collections.emptyList());
        assertFalse(bothNonZero.isAllSettled());
    }
}
