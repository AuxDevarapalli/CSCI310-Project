package com.team04.splitsmart.logic.validation;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Converts between user-entered text and integer amounts (NFR 2). Every amount in the app is a
 * {@code long} number of US cents and every percentage is an {@code int} number of basis points
 * (percent x 100), so no floating-point rounding ever happens.
 */
public final class Money {
    /** Largest accepted amount: $1,000,000.00. Guards against typos and overflow. */
    public static final long MAX_CENTS = 100_000_000L;
    /** 100% expressed in basis points. */
    public static final int FULL_PERCENT_BASIS_POINTS = 10_000;

    private static final Pattern DECIMAL = Pattern.compile("^(\\d*)(?:\\.(\\d*))?$");

    private Money() {
    }

    /**
     * Parses a dollar amount such as "12", "12.5", "$1,234.56" or ".99" into cents.
     * Zero is accepted here; callers decide whether zero is allowed.
     *
     * @throws InvalidAmountException if the text is empty, negative, not a number, has more
     *                                than two decimal places (FR 2.3) or exceeds {@link #MAX_CENTS}
     */
    public static long parseDollars(String text) throws InvalidAmountException {
        String cleaned = clean(text, "$");
        if (cleaned.isEmpty()) {
            throw new InvalidAmountException("Enter an amount");
        }
        long cents = parseScaled(cleaned, "amount", "Amounts");
        if (cents > MAX_CENTS) {
            throw new InvalidAmountException("Amount cannot be more than " + format(MAX_CENTS));
        }
        return cents;
    }

    /**
     * Parses a percentage such as "50", "33.33" or "12.5%" into basis points (33.33% -> 3333).
     *
     * @throws InvalidAmountException if the text is empty, negative, not a number, has more than
     *                                two decimal places or is above 100% (FR 3.4)
     */
    public static int parsePercent(String text) throws InvalidAmountException {
        String cleaned = clean(text, "%");
        if (cleaned.isEmpty()) {
            throw new InvalidAmountException("Enter a percentage");
        }
        long basisPoints = parseScaled(cleaned, "percentage", "Percentages");
        if (basisPoints > FULL_PERCENT_BASIS_POINTS) {
            throw new InvalidAmountException("Percentage cannot be more than 100%");
        }
        return (int) basisPoints;
    }

    /** Formats cents for display with a dollar sign and two decimals: 123456 -> "$1,234.56". */
    public static String format(long cents) {
        String sign = cents < 0 ? "-" : "";
        long abs = Math.abs(cents);
        return String.format(Locale.US, "%s$%,d.%02d", sign, abs / 100, abs % 100);
    }

    /** Formats cents for an input field without symbols: 123456 -> "1234.56". */
    public static String formatPlain(long cents) {
        String sign = cents < 0 ? "-" : "";
        long abs = Math.abs(cents);
        return String.format(Locale.US, "%s%d.%02d", sign, abs / 100, abs % 100);
    }

    /** Formats basis points as a percentage: 3333 -> "33.33%", 5000 -> "50%", 1250 -> "12.5%". */
    public static String formatPercent(int basisPoints) {
        return formatPercentPlain(basisPoints) + "%";
    }

    /** Formats basis points for an input field: 3333 -> "33.33", 5000 -> "50". */
    public static String formatPercentPlain(int basisPoints) {
        String sign = basisPoints < 0 ? "-" : "";
        int abs = Math.abs(basisPoints);
        int whole = abs / 100;
        int fraction = abs % 100;
        if (fraction == 0) {
            return sign + whole;
        }
        if (fraction % 10 == 0) {
            return sign + whole + "." + (fraction / 10);
        }
        return String.format(Locale.US, "%s%d.%02d", sign, whole, fraction);
    }

    private static String clean(String text, String symbol) {
        if (text == null) {
            return "";
        }
        String cleaned = text.trim().replace(symbol, "").replace(",", "").replace(" ", "");
        return cleaned.trim();
    }

    /** Parses a non-negative decimal with at most two fraction digits into hundredths. */
    private static long parseScaled(String cleaned, String noun, String pluralNoun)
            throws InvalidAmountException {
        if (cleaned.startsWith("-")) {
            throw new InvalidAmountException(capitalize(noun) + " cannot be negative");
        }
        Matcher matcher = DECIMAL.matcher(cleaned);
        if (!matcher.matches()) {
            throw new InvalidAmountException("Enter a valid " + noun);
        }
        String whole = matcher.group(1);
        String fraction = matcher.group(2) == null ? "" : matcher.group(2);
        if (whole.isEmpty() && fraction.isEmpty()) {
            throw new InvalidAmountException("Enter a valid " + noun);
        }
        if (fraction.length() > 2) {
            throw new InvalidAmountException(pluralNoun + " can have at most 2 decimal places");
        }
        // Strip leading zeros so very long inputs like "000000000001" still parse.
        String digits = whole.replaceFirst("^0+(?=\\d)", "");
        if (digits.length() > 12) {
            throw new InvalidAmountException("That " + noun + " is too large");
        }
        long wholePart = digits.isEmpty() ? 0 : Long.parseLong(digits);
        long fractionPart = fraction.isEmpty() ? 0 : Long.parseLong((fraction + "0").substring(0, 2));
        return wholePart * 100 + fractionPart;
    }

    private static String capitalize(String word) {
        return Character.toUpperCase(word.charAt(0)) + word.substring(1);
    }
}
