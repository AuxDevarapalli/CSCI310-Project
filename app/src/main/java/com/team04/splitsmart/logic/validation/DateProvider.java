package com.team04.splitsmart.logic.validation;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;

/**
 * Supplies the automatically recorded transaction dates (FR 2.4, FR 5.1) from the device's
 * local clock, and the month helpers used by the monthly summary (FR 5.7). The clock can be
 * replaced in tests.
 */
public class DateProvider {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.US);
    private static final DateTimeFormatter MONTH_LABEL_FORMAT =
            DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US);

    private final Clock clock;

    public DateProvider() {
        this(Clock.systemDefaultZone());
    }

    public DateProvider(Clock clock) {
        this.clock = clock;
    }

    /** Today's device-local date in YYYY-MM-DD form, e.g. "2026-09-17". */
    public String today() {
        return LocalDate.now(clock).format(DATE_FORMAT);
    }

    /** Current time in epoch milliseconds, used to order transactions within a day. */
    public long nowMillis() {
        return clock.millis();
    }

    /** The current month as "YYYY-MM". */
    public String currentMonth() {
        return YearMonth.now(clock).toString();
    }

    /** The "YYYY-MM" month a "YYYY-MM-DD" date belongs to, or "" if the date is malformed. */
    public static String monthOf(String date) {
        if (date == null || date.length() < 7) {
            return "";
        }
        return date.substring(0, 7);
    }

    /** Moves a "YYYY-MM" month forwards or backwards: ("2026-01", -1) -> "2025-12". */
    public static String shiftMonth(String yearMonth, int months) {
        return YearMonth.parse(yearMonth).plusMonths(months).toString();
    }

    /** Human-readable month label: "2026-09" -> "September 2026". */
    public static String formatMonthLabel(String yearMonth) {
        try {
            return YearMonth.parse(yearMonth).format(MONTH_LABEL_FORMAT);
        } catch (DateTimeParseException e) {
            return yearMonth;
        }
    }
}
