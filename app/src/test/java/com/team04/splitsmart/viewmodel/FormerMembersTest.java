package com.team04.splitsmart.viewmodel;

import static com.team04.splitsmart.logic.TestData.ALICE;
import static com.team04.splitsmart.logic.TestData.BOB;
import static com.team04.splitsmart.logic.TestData.CAROL;
import static com.team04.splitsmart.logic.TestData.DAVID;
import static com.team04.splitsmart.logic.TestData.expense;
import static com.team04.splitsmart.logic.TestData.settlement;
import static com.team04.splitsmart.logic.TestData.share;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.team04.splitsmart.model.Transaction;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

public class FormerMembersTest {

    @Test
    public void findsPeopleInTransactionsWhoAreNoLongerMembers() {
        List<Transaction> history = Arrays.asList(
                expense(ALICE, share(ALICE, 500), share(BOB, 500)),
                settlement(DAVID, ALICE, 300),
                expense(ALICE, share(ALICE, 200), share(BOB, 200)));
        assertEquals(Arrays.asList(BOB, DAVID),
                FormerMembers.missingUserIds(history, Arrays.asList(ALICE, CAROL)));
    }

    @Test
    public void nothingIsMissingWhenEveryoneIsStillAMember() {
        List<Transaction> history = Arrays.asList(
                expense(ALICE, share(ALICE, 500), share(BOB, 500)));
        assertTrue(FormerMembers.missingUserIds(history, Arrays.asList(ALICE, BOB)).isEmpty());
    }
}
