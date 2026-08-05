package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Calendar;
import java.util.List;
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
    public void compareEntries_supportsDateDirections() {
        SavedSummaryStore.Entry newer = entry(3, "Alpha", 300);
        SavedSummaryStore.Entry older = entry(2, "Alpha", 200);

        assertTrue(SavedListOrder.compareEntries(newer, older, true) < 0);
        assertTrue(SavedListOrder.compareEntries(newer, older, false) > 0);
    }

    @Test
    public void matchesEntry_searchesCreatorAndAppliesCreatorSelection() {
        SavedSummaryStore.Entry alpha = entry(3, "Alpha Creator", 300);
        SavedSummaryStore.Entry unknown = entry(2, "", 200);

        assertTrue(SavedListOrder.matchesEntry(alpha, "alpha", null));
        assertTrue(SavedListOrder.matchesEntry(alpha, "summary one", "ALPHA CREATOR"));
        assertFalse(SavedListOrder.matchesEntry(alpha, "", "Beta Creator"));
        assertTrue(SavedListOrder.matchesEntry(unknown, "", ""));
        assertFalse(SavedListOrder.matchesEntry(alpha, "", ""));
    }

    @Test
    public void creatorCounts_sortsNamesAndKeepsUnknownLast() {
        List<SavedSummaryStore.Entry> entries = Arrays.asList(
                entry(1, "Beta Creator", 100),
                entry(2, "Alpha Creator", 200),
                entry(3, "alpha creator", 300),
                entry(4, "", 400)
        );

        List<SavedListOrder.CreatorCount> counts = SavedListOrder.creatorCounts(entries);

        assertEquals(3, counts.size());
        assertEquals("Alpha Creator", counts.get(0).channelName);
        assertEquals(2, counts.get(0).count);
        assertEquals("Beta Creator", counts.get(1).channelName);
        assertEquals(1, counts.get(1).count);
        assertEquals("", counts.get(2).channelName);
        assertEquals("Unknown creator", counts.get(2).displayName());
        assertEquals(1, counts.get(2).count);
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
