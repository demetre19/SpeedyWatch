package com.speedywatch.app;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

final class SavedListOrder {
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
            boolean byChannel,
            boolean descending
    ) {
        if (byChannel) {
            boolean leftUnknown = left.channelName.isEmpty();
            boolean rightUnknown = right.channelName.isEmpty();
            if (leftUnknown != rightUnknown) {
                return leftUnknown ? 1 : -1;
            }
            int channelOrder = descending
                    ? String.CASE_INSENSITIVE_ORDER.compare(right.channelName, left.channelName)
                    : String.CASE_INSENSITIVE_ORDER.compare(left.channelName, right.channelName);
            if (channelOrder != 0) {
                return channelOrder;
            }
            int newestWithinChannel = Long.compare(right.createdAt, left.createdAt);
            return newestWithinChannel != 0
                    ? newestWithinChannel
                    : Long.compare(right.id, left.id);
        }
        int dateOrder = descending
                ? Long.compare(right.createdAt, left.createdAt)
                : Long.compare(left.createdAt, right.createdAt);
        return dateOrder != 0
                ? dateOrder
                : (descending
                        ? Long.compare(right.id, left.id)
                        : Long.compare(left.id, right.id));
    }
}
