package com.speedywatch.app;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.TreeMap;

final class SavedListOrder {
    static final class CreatorCount {
        final String channelName;
        final int count;

        CreatorCount(String channelName, int count) {
            this.channelName = channelName;
            this.count = count;
        }

        String displayName() {
            return channelName == null
                    ? "All creators"
                    : (channelName.isEmpty() ? "Unknown creator" : channelName);
        }
    }

    private SavedListOrder() {
    }

    static String dayLabel(
            long timestamp,
            long now,
            Locale locale,
            TimeZone timeZone
    ) {
        Calendar saved = Calendar.getInstance(timeZone, locale);
        saved.setTimeInMillis(timestamp);
        Calendar current = Calendar.getInstance(timeZone, locale);
        current.setTimeInMillis(now);
        String pattern = saved.get(Calendar.YEAR) == current.get(Calendar.YEAR)
                ? "d MMM"
                : "d MMM yyyy";
        SimpleDateFormat formatter = new SimpleDateFormat(pattern, locale);
        formatter.setTimeZone(timeZone);
        return formatter.format(new Date(timestamp));
    }

    static int compareEntries(
            SavedSummaryStore.Entry left,
            SavedSummaryStore.Entry right,
            boolean descending
    ) {
        int dateOrder = descending
                ? Long.compare(right.createdAt, left.createdAt)
                : Long.compare(left.createdAt, right.createdAt);
        return dateOrder != 0
                ? dateOrder
                : (descending
                        ? Long.compare(right.id, left.id)
                        : Long.compare(left.id, right.id));
    }

    static boolean matchesEntry(
            SavedSummaryStore.Entry entry,
            String query,
            String selectedCreator
    ) {
        if (selectedCreator != null) {
            boolean creatorMatches = selectedCreator.isEmpty()
                    ? entry.channelName.isEmpty()
                    : selectedCreator.equalsIgnoreCase(entry.channelName);
            if (!creatorMatches) {
                return false;
            }
        }
        String normalized = query == null ? "" : query.toLowerCase(Locale.US).trim();
        if (normalized.isEmpty()) {
            return true;
        }
        String searchable = (
                entry.videoTitle + " "
                        + entry.summaryLabel + " "
                        + entry.channelName + " "
                        + entry.summaryText
        ).toLowerCase(Locale.US);
        return searchable.contains(normalized);
    }

    static List<CreatorCount> creatorCounts(List<SavedSummaryStore.Entry> entries) {
        TreeMap<String, Integer> known = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        int unknown = 0;
        for (SavedSummaryStore.Entry entry : entries) {
            if (entry.channelName.isEmpty()) {
                unknown++;
            } else {
                known.merge(entry.channelName, 1, Integer::sum);
            }
        }
        List<CreatorCount> counts = new ArrayList<>(known.size() + (unknown == 0 ? 0 : 1));
        for (Map.Entry<String, Integer> creator : known.entrySet()) {
            counts.add(new CreatorCount(creator.getKey(), creator.getValue()));
        }
        if (unknown > 0) {
            counts.add(new CreatorCount("", unknown));
        }
        return counts;
    }
}
