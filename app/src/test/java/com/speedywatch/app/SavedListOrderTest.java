package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

public final class SavedListOrderTest {
    private static final TimeZone UTC = TimeZone.getTimeZone("UTC");

    @Test
    public void dayLabel_omitsCurrentYearAndIncludesPastYear() {
        long now = timestamp(2026, Calendar.AUGUST, 5, 12, 0);

        assertEquals(
                "5 Aug",
                SavedListOrder.dayLabel(
                        timestamp(2026, Calendar.AUGUST, 5, 2, 7),
                        now,
                        Locale.US,
                        UTC
                )
        );
        assertEquals(
                "31 Jul 2025",
                SavedListOrder.dayLabel(
                        timestamp(2025, Calendar.JULY, 31, 12, 1),
                        now,
                        Locale.US,
                        UTC
                )
        );
    }

    @Test
    public void compareEntries_supportsDateAndChannelDirections() {
        SavedSummaryStore.Entry alphaNew = entry(3, "Alpha", 300);
        SavedSummaryStore.Entry alphaOld = entry(2, "Alpha", 200);
        SavedSummaryStore.Entry beta = entry(1, "Beta", 100);
        SavedSummaryStore.Entry unknown = entry(4, "", 400);

        assertTrue(SavedListOrder.compareEntries(alphaNew, alphaOld, false, true) < 0);
        assertTrue(SavedListOrder.compareEntries(alphaNew, alphaOld, false, false) > 0);
        assertTrue(SavedListOrder.compareEntries(alphaNew, beta, true, false) < 0);
        assertTrue(SavedListOrder.compareEntries(alphaNew, beta, true, true) > 0);
        assertTrue(SavedListOrder.compareEntries(alphaNew, alphaOld, true, false) < 0);
        assertTrue(SavedListOrder.compareEntries(unknown, beta, true, false) > 0);
        assertTrue(SavedListOrder.compareEntries(unknown, beta, true, true) > 0);
    }

    private static SavedSummaryStore.Entry entry(long id, String channel, long createdAt) {
        return new SavedSummaryStore.Entry(
                id,
                "Video",
                "Summary One",
                "Content",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                channel,
                createdAt
        );
    }

    private static long timestamp(int year, int month, int day, int hour, int minute) {
        Calendar calendar = Calendar.getInstance(UTC, Locale.US);
        calendar.clear();
        calendar.set(year, month, day, hour, minute);
        return calendar.getTimeInMillis();
    }
}
