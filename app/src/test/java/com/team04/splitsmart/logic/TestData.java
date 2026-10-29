package com.team04.splitsmart.logic;

import com.team04.splitsmart.model.Category;
import com.team04.splitsmart.model.Expense;
import com.team04.splitsmart.model.Group;
import com.team04.splitsmart.model.Settlement;
import com.team04.splitsmart.model.Share;
import com.team04.splitsmart.model.SplitMethod;
import com.team04.splitsmart.model.User;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Small builders that keep the unit tests readable. */
public final class TestData {
    public static final String ALICE = "alice";
    public static final String BOB = "bob";
    public static final String CAROL = "carol";
    public static final String DAVID = "david";
    public static final String GROUP_ID = "group1";

    private static long clock = 1_000;

    private TestData() {
    }

    public static User user(String uid) {
        String name = Character.toUpperCase(uid.charAt(0)) + uid.substring(1);
        return new User(uid, name, uid + "@usc.edu", "123456789" + uid.length());
    }

    public static List<User> users(String... uids) {
        List<User> users = new ArrayList<>();
        for (String uid : uids) {
            users.add(user(uid));
        }
        return users;
    }

    public static Group group(String... memberIds) {
        return new Group(GROUP_ID, "Roommates", "", memberIds[0], Arrays.asList(memberIds), 0);
    }

    public static Share share(String uid, long cents) {
        return new Share(uid, cents);
    }

    /** An expense created by its payer on the given date. */
    public static Expense expense(String title, String payer, long totalCents, String date,
                                  Category category, Share... shares) {
        return new Expense("e" + (++clock), GROUP_ID, payer, date, clock, "", title, totalCents,
                category, payer, SplitMethod.EXACT, Arrays.asList(shares));
    }

    /** An expense where only the debts matter: payer pays, shares as given. */
    public static Expense expense(String payer, Share... shares) {
        long total = 0;
        for (Share share : shares) {
            total += share.getAmountCents();
        }
        return expense("Expense", payer, total, "2026-09-17", Category.OTHER, shares);
    }

    public static Expense expenseWithNote(String title, String note, long createdAt) {
        return new Expense("n" + createdAt, GROUP_ID, ALICE, "2026-09-17", createdAt, note, title,
                1000, Category.FOOD, ALICE, SplitMethod.EQUAL,
                Arrays.asList(share(ALICE, 500), share(BOB, 500)));
    }

    public static Settlement settlement(String payer, String receiver, long cents) {
        return settlement(payer, receiver, cents, "2026-09-18", "");
    }

    public static Settlement settlement(String payer, String receiver, long cents, String date,
                                        String note) {
        return new Settlement("s" + (++clock), GROUP_ID, payer, date, clock, note, payer,
                receiver, cents);
    }
}
