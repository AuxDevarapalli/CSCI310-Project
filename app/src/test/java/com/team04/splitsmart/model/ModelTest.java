package com.team04.splitsmart.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;

public class ModelTest {

    @Test
    public void expenseKnowsItsParticipantsAndShares() {
        Expense expense = new Expense("e1", "g1", "alice", "2026-09-17", 1, " note ", " Dinner ",
                3000, null, "alice", null,
                Arrays.asList(new Share("alice", 1000), new Share("bob", 2000)));
        assertEquals("Dinner", expense.getTitle());
        assertEquals("note", expense.getNote());
        assertEquals(Category.OTHER, expense.getCategory());
        assertEquals(SplitMethod.EQUAL, expense.getSplitMethod());
        assertEquals(2000, expense.getShareFor("bob"));
        assertEquals(0, expense.getShareFor("carol"));
        assertTrue(expense.involves("bob"));
        assertFalse(expense.involves("carol"));
        assertEquals(Arrays.asList("alice", "bob"), expense.getInvolvedUserIds());
        assertTrue(expense.isCreatedBy("alice"));
    }

    @Test
    public void groupCanLoseAMemberAndChangeOwner() {
        Group group = new Group("g1", "Roommates", "", "alice",
                Arrays.asList("alice", "bob", "carol"), 1);
        Group without = group.withoutMember("bob");
        assertEquals(Arrays.asList("alice", "carol"), without.getMemberIds());
        assertTrue(group.isMember("bob"));
        Group transferred = without.withOwner("carol");
        assertTrue(transferred.isOwner("carol"));
        assertFalse(transferred.isOwner("alice"));
        assertTrue(transferred.isMember("alice"));
    }

    @Test
    public void settlementInvolvesPayerAndReceiver() {
        Settlement settlement = new Settlement("s1", "g1", "bob", "2026-09-17", 1, null,
                "bob", "alice", 500);
        assertEquals(TransactionType.SETTLEMENT, settlement.getType());
        assertEquals(Arrays.asList("bob", "alice"), settlement.getInvolvedUserIds());
        assertEquals("", settlement.getNote());
    }

    @Test
    public void groupMembership() {
        Group group = new Group("g1", "Roommates", null, "alice", Arrays.asList("alice"), 0);
        assertTrue(group.isOwner("alice"));
        assertFalse(group.isMember("bob"));
        Group withBob = group.withMember("bob");
        assertTrue(withBob.isMember("bob"));
        assertSame(withBob, withBob.withMember("bob"));
        assertEquals(2, withBob.getMemberCount());
    }

    @Test
    public void enumsParseStoredNamesSafely() {
        assertEquals(Category.FOOD, Category.fromName("FOOD"));
        assertEquals(Category.OTHER, Category.fromName("bogus"));
        assertEquals(SplitMethod.PERCENTAGE, SplitMethod.fromName("PERCENTAGE"));
        assertEquals(TransactionType.EXPENSE, TransactionType.fromName("EXPENSE"));
    }

    @Test
    public void userInitials() {
        assertEquals("AD", new User("u", "Aux Devarapalli", "a@usc.edu", "1").getInitials());
        assertEquals("T", new User("u", "tommy", "t@usc.edu", "1").getInitials());
        assertEquals("Z", new User("u", " ", "zed@usc.edu", "1").getInitials());
    }
}
