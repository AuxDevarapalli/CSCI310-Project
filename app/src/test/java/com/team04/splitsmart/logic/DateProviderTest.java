package com.team04.splitsmart.logic;

import static org.junit.Assert.assertEquals;

import com.team04.splitsmart.logic.validation.DateProvider;

import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

/** FR 2.4: dates come from the device's local date in YYYY-MM-DD form. */
public class DateProviderTest {

    @Test
    public void todayUsesYearMonthDayFormat() {
        Clock clock = Clock.fixed(Instant.parse("2026-09-17T18:30:00Z"), ZoneId.of("UTC"));
        DateProvider provider = new DateProvider(clock);
        assertEquals("2026-09-17", provider.today());
        assertEquals("2026-09", provider.currentMonth());
    }

    @Test
    public void todayUsesTheLocalTimeZone() {
        // 2026-09-18 03:00 UTC is still 2026-09-17 in Los Angeles.
        Clock clock = Clock.fixed(Instant.parse("2026-09-18T03:00:00Z"),
                ZoneId.of("America/Los_Angeles"));
        assertEquals("2026-09-17", new DateProvider(clock).today());
    }

    @Test
    public void monthHelpers() {
        assertEquals("2026-09", DateProvider.monthOf("2026-09-17"));
        assertEquals("2025-12", DateProvider.shiftMonth("2026-01", -1));
        assertEquals("2027-01", DateProvider.shiftMonth("2026-12", 1));
        assertEquals("September 2026", DateProvider.formatMonthLabel("2026-09"));
    }
}
