package com.team04.splitsmart.logic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.team04.splitsmart.logic.validation.InvalidAmountException;
import com.team04.splitsmart.logic.validation.Money;

import org.junit.Test;

/** NFR 2 and FR 2.3: amounts are parsed into integer cents with at most two decimals. */
public class MoneyTest {

    @Test
    public void parseDollars_acceptsCommonFormats() throws Exception {
        assertEquals(1000, Money.parseDollars("10"));
        assertEquals(1050, Money.parseDollars("10.5"));
        assertEquals(1050, Money.parseDollars("10.50"));
        assertEquals(1050, Money.parseDollars("$10.50"));
        assertEquals(1050, Money.parseDollars("  10.50 "));
        assertEquals(1000, Money.parseDollars("10."));
        assertEquals(50, Money.parseDollars(".5"));
        assertEquals(1, Money.parseDollars("0.01"));
        assertEquals(0, Money.parseDollars("0"));
        assertEquals(123456, Money.parseDollars("1,234.56"));
        assertEquals(700, Money.parseDollars("007"));
    }

    @Test
    public void parseDollars_rejectsMoreThanTwoDecimalPlaces() {
        assertRejected("10.123", "at most 2 decimal places");
        assertRejected("0.001", "at most 2 decimal places");
    }

    @Test
    public void parseDollars_rejectsInvalidInput() {
        assertRejected("", "Enter an amount");
        assertRejected(null, "Enter an amount");
        assertRejected("-5", "cannot be negative");
        assertRejected("abc", "valid amount");
        assertRejected("1.2.3", "valid amount");
        assertRejected(".", "valid amount");
        assertRejected("+5", "valid amount");
        assertRejected("1e5", "valid amount");
    }

    @Test
    public void parseDollars_rejectsAmountsAboveTheLimit() {
        assertRejected("1000000.01", "cannot be more than");
        assertRejected("99999999999999", "too large");
    }

    @Test
    public void parseDollars_acceptsTheLimitItself() throws Exception {
        assertEquals(Money.MAX_CENTS, Money.parseDollars("1000000"));
    }

    @Test
    public void format_usesDollarSignAndTwoDecimals() {
        assertEquals("$0.00", Money.format(0));
        assertEquals("$0.01", Money.format(1));
        assertEquals("$3.34", Money.format(334));
        assertEquals("$10.00", Money.format(1000));
        assertEquals("$1,234.56", Money.format(123456));
        assertEquals("-$10.50", Money.format(-1050));
    }

    @Test
    public void formatPlain_isSuitableForInputFields() {
        assertEquals("0.05", Money.formatPlain(5));
        assertEquals("1234.56", Money.formatPlain(123456));
    }

    @Test
    public void parsePercent_returnsBasisPoints() throws Exception {
        assertEquals(3333, Money.parsePercent("33.33"));
        assertEquals(5000, Money.parsePercent("50"));
        assertEquals(1250, Money.parsePercent("12.5%"));
        assertEquals(10000, Money.parsePercent("100"));
        assertEquals(0, Money.parsePercent("0"));
    }

    @Test
    public void parsePercent_rejectsInvalidInput() {
        try {
            Money.parsePercent("100.01");
            fail("expected rejection");
        } catch (InvalidAmountException e) {
            assertTrue(e.getMessage().contains("100%"));
        }
        try {
            Money.parsePercent("12.345");
            fail("expected rejection");
        } catch (InvalidAmountException e) {
            assertTrue(e.getMessage().contains("2 decimal places"));
        }
        try {
            Money.parsePercent("-1");
            fail("expected rejection");
        } catch (InvalidAmountException e) {
            assertTrue(e.getMessage().contains("negative"));
        }
    }

    @Test
    public void formatPercent_dropsUnneededZeros() {
        assertEquals("33.33%", Money.formatPercent(3333));
        assertEquals("50%", Money.formatPercent(5000));
        assertEquals("12.5%", Money.formatPercent(1250));
        assertEquals("0.05%", Money.formatPercent(5));
        assertEquals("33.33", Money.formatPercentPlain(3333));
    }

    private static void assertRejected(String input, String expectedMessagePart) {
        try {
            Money.parseDollars(input);
            fail("Expected '" + input + "' to be rejected");
        } catch (InvalidAmountException e) {
            assertTrue("Message was: " + e.getMessage(),
                    e.getMessage().contains(expectedMessagePart));
        }
    }
}
