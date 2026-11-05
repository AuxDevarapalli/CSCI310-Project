package com.team04.splitsmart.data;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Share;
import com.team04.splitsmart.model.SplitMethod;
import com.team04.splitsmart.model.TransactionType;
import com.team04.splitsmart.model.User;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class FirestoreMapperTest {

    @Test
    public void userToMap_mapsAllFieldsCorrectly() {
        User user = new User("u1", "Tommy Trojan", "ttrojan@usc.edu", "1234567890");
        Map<String, Object> map = FirestoreMapper.userToMap(user, 1000L);

        assertEquals("Tommy Trojan", map.get(FirestoreMapper.NAME));
        assertEquals("ttrojan@usc.edu", map.get(FirestoreMapper.USC_EMAIL));
        assertEquals("1234567890", map.get(FirestoreMapper.USC_ID));
        assertEquals(1000L, map.get(FirestoreMapper.CREATED_AT));
    }

    @Test
    public void uscIdToMap_mapsUidCorrectly() {
        Map<String, Object> map = FirestoreMapper.uscIdToMap("u1");
        assertEquals("u1", map.get(FirestoreMapper.UID));
    }

    @Test
    public void groupToMap_mapsAllFieldsCorrectly() {
        Group group = new Group("g1", "Roommates", "Apt 4B", "u1",
                Arrays.asList("u1", "u2"), 5000L);
        Map<String, Object> map = FirestoreMapper.groupToMap(group);

        assertEquals("Roommates", map.get(FirestoreMapper.NAME));
        assertEquals("Apt 4B", map.get(FirestoreMapper.DESCRIPTION));
        assertEquals("u1", map.get(FirestoreMapper.OWNER_ID));
        assertEquals(Arrays.asList("u1", "u2"), map.get(FirestoreMapper.MEMBER_IDS));
        assertEquals(5000L, map.get(FirestoreMapper.CREATED_AT));
    }

    @Test
    public void expenseToMap_mapsAllFieldsAndSharesCorrectly() {
        Expense expense = new Expense("e1", "g1", "u1", "2026-11-04", 1000L, "Note",
                "Groceries", 3000L, Category.FOOD, "u1", SplitMethod.EQUAL,
                Arrays.asList(new Share("u1", 1500), new Share("u2", 1500)));

        Map<String, Object> map = FirestoreMapper.expenseToMap(expense, 2000L);

        assertEquals(TransactionType.EXPENSE.name(), map.get(FirestoreMapper.TYPE));
        assertEquals("u1", map.get(FirestoreMapper.CREATOR_ID));
        assertEquals("2026-11-04", map.get(FirestoreMapper.DATE));
        assertEquals(1000L, map.get(FirestoreMapper.CREATED_AT));
        assertEquals(2000L, map.get(FirestoreMapper.UPDATED_AT));
        assertEquals("Note", map.get(FirestoreMapper.NOTE));
        assertEquals("Groceries", map.get(FirestoreMapper.TITLE));
        assertEquals(3000L, map.get(FirestoreMapper.TOTAL_CENTS));
        assertEquals("FOOD", map.get(FirestoreMapper.CATEGORY));
        assertEquals("u1", map.get(FirestoreMapper.PAYER_ID));
        assertEquals("EQUAL", map.get(FirestoreMapper.SPLIT_METHOD));
        assertEquals(Arrays.asList("u1", "u2"), map.get(FirestoreMapper.PARTICIPANT_IDS));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> shares = (List<Map<String, Object>>) map.get(FirestoreMapper.SHARES);
        assertEquals(2, shares.size());
        assertEquals("u1", shares.get(0).get(FirestoreMapper.PARTICIPANT_ID));
        assertEquals(1500L, shares.get(0).get(FirestoreMapper.AMOUNT_CENTS));
    }

    @Test
    public void settlementToMap_mapsAllFieldsCorrectly() {
        Settlement settlement = new Settlement("s1", "g1", "u1", "2026-11-04", 1000L,
                "Venmo", "u1", "u2", 500L);

        Map<String, Object> map = FirestoreMapper.settlementToMap(settlement, 2000L);

        assertEquals(TransactionType.SETTLEMENT.name(), map.get(FirestoreMapper.TYPE));
        assertEquals("u1", map.get(FirestoreMapper.CREATOR_ID));
        assertEquals("u1", map.get(FirestoreMapper.PAYER_ID));
        assertEquals("u2", map.get(FirestoreMapper.RECEIVER_ID));
        assertEquals(500L, map.get(FirestoreMapper.AMOUNT_CENTS));
    }

    @Test
    public void asLong_handlesVariousTypesAndNulls() {
        assertEquals(10L, FirestoreMapper.asLong(10));
        assertEquals(100L, FirestoreMapper.asLong(100L));
        assertEquals(5L, FirestoreMapper.asLong(5.7));
        assertEquals(0L, FirestoreMapper.asLong(null));
        assertEquals(0L, FirestoreMapper.asLong("not a number"));
    }

    @Test
    public void asStringList_handlesVariousTypesAndNulls() {
        assertEquals(Arrays.asList("a", "b"),
                FirestoreMapper.asStringList(Arrays.asList("a", "b")));
        assertEquals(Collections.singletonList("a"),
                FirestoreMapper.asStringList(Arrays.asList("a", 123, null)));
        assertTrue(FirestoreMapper.asStringList(null).isEmpty());
        assertTrue(FirestoreMapper.asStringList("not a list").isEmpty());
    }
}
