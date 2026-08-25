package com.speedywatch.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * App-private store of links harvested from visible pages. De-duplication is enforced at the
 * database level: every row is keyed by a canonical {@code url_key} and re-encounters only
 * refresh {@code last_seen_at} metadata instead of creating duplicates.
 */
final class ScrapedLinkStore extends SQLiteOpenHelper {

    static final int MAXIMUM_STORED_ROWS = 20_000;
    private static final int MAXIMUM_URL_KEY_LENGTH = 1_200;

    /** record() results: a newly inserted row id is above zero, otherwise one of these. */
    static final long DUPLICATE = 0;
    static final long INVALID = -1;

    private static final String DATABASE_NAME = "scraped_links.db";
    private static final int DATABASE_VERSION = 2;
    private static final String TABLE = "scraped_links";

    /** Query parameters that never change a link's identity. */
    private static final Set<String> TRACKING_PARAMETERS = new HashSet<>(Arrays.asList(
            "s", "t", "si", "feature", "app", "fbclid", "gclid", "ref", "ref_src", "ref_url",
            "utm_id", "utm_source", "utm_medium", "utm_term", "utm_content", "utm_campaign"
    ));

    static final class Entry {
        final long id;
        final String urlKey;
        final String url;
        final String displayText;
        final String posterName;
        final String sourceUrl;
        final Long postedAt;
        final long firstSeenAt;
        final long lastSeenAt;
        final byte[] preview;

        Entry(
                long id,
                String urlKey,
                String url,
                String displayText,
                String posterName,
                String sourceUrl,
                Long postedAt,
                long firstSeenAt,
                long lastSeenAt
        ) {
            this(id, urlKey, url, displayText, posterName, sourceUrl, postedAt,
                    firstSeenAt, lastSeenAt, null);
        }

        Entry(
                long id,
                String urlKey,
                String url,
                String displayText,
                String posterName,
                String sourceUrl,
                Long postedAt,
                long firstSeenAt,
                long lastSeenAt,
                byte[] preview
        ) {
            this.id = id;
            this.urlKey = urlKey;
            this.url = url;
            this.displayText = displayText;
            this.posterName = posterName;
            this.sourceUrl = sourceUrl;
            this.postedAt = postedAt;
            this.firstSeenAt = firstSeenAt;
            this.lastSeenAt = lastSeenAt;
            this.preview = preview == null ? null : preview.clone();
        }

        /** Display date used for grouping: the page-reported date, else first-seen. */
        long datedAt() {
            return postedAt != null ? postedAt : firstSeenAt;
        }
    }

    ScrapedLinkStore(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE " + TABLE + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "url_key TEXT NOT NULL UNIQUE,"
                        + "url TEXT NOT NULL,"
                        + "display_text TEXT NOT NULL DEFAULT '',"
                        + "poster_name TEXT NOT NULL DEFAULT '',"
                        + "source_url TEXT NOT NULL DEFAULT '',"
                        + "posted_at INTEGER,"
                        + "first_seen_at INTEGER NOT NULL,"
                        + "last_seen_at INTEGER NOT NULL,"
                        + "preview BLOB)"
        );
        database.execSQL(
                "CREATE INDEX scraped_links_dated_at ON " + TABLE + " ("
                        + "COALESCE(posted_at, first_seen_at) DESC)"
        );
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 2) {
            database.execSQL("ALTER TABLE " + TABLE + " ADD COLUMN preview BLOB");
        }
    }

    /**
     * Records one harvested link. Returns the new row id when the link was first seen,
     * {@link #DUPLICATE} when its canonical key already exists (existing metadata keeps its
     * original dates), or {@link #INVALID} when no HTTPS destination could be resolved.
     * The visible link text is stored verbatim; {@code urlHint} carries the scheme-normalized
     * candidate used to recover the real destination behind a t.co href.
     */
    synchronized long record(
            String rawUrl,
            String urlHint,
            String displayText,
            String posterName,
            String sourceUrl,
            Long postedAt,
            long seenAt
    ) {
        String resolved = resolveRealUrl(rawUrl, urlHint);
        if (resolved == null || SupportedSite.forUrl(resolved) == SupportedSite.MEGA) {
            return INVALID;
        }
        String key = canonicalUrlKey(resolved);
        if (key == null || key.isEmpty()) {
            return INVALID;
        }
        SQLiteDatabase database = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("url_key", key);
        values.put("url", resolved);
        values.put("display_text", bounded(displayText, 500));
        values.put("poster_name", bounded(posterName, 120));
        values.put("source_url", bounded(sourceUrl, 2_000));
        values.put("posted_at", postedAt);
        values.put("first_seen_at", seenAt);
        values.put("last_seen_at", seenAt);
        long id = database.insertWithOnConflict(TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE);
        if (id != -1) {
            trimRows(database);
            return id;
        }
        ContentValues refresh = new ContentValues();
        refresh.put("last_seen_at", seenAt);
        database.updateWithOnConflict(
                TABLE, refresh, "url_key = ?", new String[]{key}, SQLiteDatabase.CONFLICT_IGNORE
        );
        return DUPLICATE;
    }

    /**
     * Replaces the stored URL after a successful short-link expansion. Fails silently when
     * the expanded destination is invalid or its key is already stored.
     */
    synchronized void updateExpandedUrl(long id, String expandedUrl) {
        String valid = SupportedSite.validatedHttpsUrl(expandedUrl);
        if (valid == null || SupportedSite.forUrl(valid) == SupportedSite.MEGA) {
            return;
        }
        String key = canonicalUrlKey(valid);
        if (key == null || key.isEmpty()) {
            return;
        }
        SQLiteDatabase database = getWritableDatabase();
        Cursor existing = database.rawQuery(
                "SELECT url_key FROM " + TABLE + " WHERE url_key = ? AND id <> ?",
                new String[]{key, Long.toString(id)}
        );
        boolean duplicate = existing.moveToFirst();
        existing.close();
        if (duplicate) {
            return;
        }
        ContentValues values = new ContentValues();
        values.put("url_key", key);
        values.put("url", valid);
        database.update(TABLE, values, "id = ?", new String[]{Long.toString(id)});
    }

    synchronized List<Entry> list() {
        List<Entry> entries = new ArrayList<>();
        SQLiteDatabase database = getReadableDatabase();
        try (Cursor cursor = database.query(
                TABLE,
                new String[]{
                        "id", "url_key", "url", "display_text", "poster_name",
                        "source_url", "posted_at", "first_seen_at", "last_seen_at", "preview"
                },
                null,
                null,
                null,
                null,
                "COALESCE(posted_at, first_seen_at) DESC"
        )) {
            while (cursor.moveToNext()) {
                long postedAt = cursor.getLong(6);
                entries.add(new Entry(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getString(5),
                        cursor.isNull(6) ? null : postedAt,
                        cursor.getLong(7),
                        cursor.getLong(8),
                        cursor.getBlob(9)
                ));
            }
        }
        return entries;
    }

    synchronized Entry get(long id) {
        for (Entry entry : list()) {
            if (entry.id == id) {
                return entry;
            }
        }
        return null;
    }

    synchronized boolean updatePreview(long id, byte[] preview) {
        if (id <= 0 || preview == null
                || preview.length == 0 || preview.length > SavedThumbnail.MAX_BYTES) {
            return false;
        }
        ContentValues values = new ContentValues();
        values.put("preview", preview);
        return getWritableDatabase().update(
                TABLE, values, "id = ?", new String[]{Long.toString(id)}
        ) == 1;
    }

    synchronized void delete(long id) {
        getWritableDatabase().delete(TABLE, "id = ?", new String[]{Long.toString(id)});
    }

    /** Deletes every row whose id is listed; returns the number of rows removed. */
    synchronized int deleteIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int removed = 0;
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            for (Long id : ids) {
                if (id != null && id > 0) {
                    removed += database.delete(
                            TABLE, "id = ?", new String[]{Long.toString(id)});
                }
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
        return removed;
    }

    /** Lowercase host without a leading {@code www.}; empty when unusable. */
    static String hostOf(String httpsUrl) {
        if (httpsUrl == null) {
            return "";
        }
        try {
            String host = URI.create(httpsUrl).getHost();
            if (host == null) {
                return "";
            }
            host = host.toLowerCase(Locale.ROOT);
            return host.startsWith("www.") ? host.substring(4) : host;
        } catch (RuntimeException error) {
            return "";
        }
    }

    /** Distinct link domains with counts, most-linked first then alphabetical. */
    synchronized LinkedHashMap<String, Integer> domainCounts() {
        LinkedHashMap<String, Integer> counts = new LinkedHashMap<>();
        for (Entry entry : list()) {
            String host = hostOf(entry.url);
            if (host.isEmpty()) {
                continue;
            }
            counts.merge(host, 1, Integer::sum);
        }
        LinkedHashMap<String, Integer> sorted = new LinkedHashMap<>();
        counts.entrySet().stream()
                .sorted(Comparator
                        .comparing((Map.Entry<String, Integer> item) -> -item.getValue())
                        .thenComparing(Map.Entry::getKey))
                .forEach(item -> sorted.put(item.getKey(), item.getValue()));
        return sorted;
    }

    /** Deletes every saved link from one domain; returns the rows removed. */
    synchronized int deleteDomain(String host) {
        String normalized = host == null ? "" : host.toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return 0;
        }
        List<Long> ids = new ArrayList<>();
        for (Entry entry : list()) {
            if (normalized.equals(hostOf(entry.url))) {
                ids.add(entry.id);
            }
        }
        return deleteIds(ids);
    }

    synchronized void clear() {
        getWritableDatabase().delete(TABLE, null, null);
    }

    synchronized List<Entry> snapshotForBackup() {
        return list();
    }

    synchronized void replaceAll(List<Entry> entries) {
        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            database.delete(TABLE, null, null);
            for (Entry entry : entries) {
                ContentValues values = new ContentValues();
                values.put("url_key", entry.urlKey);
                values.put("url", entry.url);
                values.put("display_text", bounded(entry.displayText, 500));
                values.put("poster_name", bounded(entry.posterName, 120));
                values.put("source_url", bounded(entry.sourceUrl, 2_000));
                values.put("posted_at", entry.postedAt);
                values.put("first_seen_at", entry.firstSeenAt);
                values.put("last_seen_at", entry.lastSeenAt);
                if (entry.preview != null
                        && entry.preview.length > 0
                        && entry.preview.length <= SavedThumbnail.MAX_BYTES) {
                    values.put("preview", entry.preview);
                }
                database.insertWithOnConflict(
                        TABLE, null, values, SQLiteDatabase.CONFLICT_IGNORE
                );
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    private static void trimRows(SQLiteDatabase database) {
        database.execSQL(
                "DELETE FROM " + TABLE + " WHERE id NOT IN ("
                        + "SELECT id FROM " + TABLE + " "
                        + "ORDER BY COALESCE(posted_at, first_seen_at) DESC LIMIT "
                        + MAXIMUM_STORED_ROWS + ")"
        );
    }

    private static String bounded(String value, int maximumLength) {
        String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return trimmed.length() > maximumLength
                ? trimmed.substring(0, maximumLength) : trimmed;
    }

    /**
     * Picks the best-known real destination: the hint recovered from visible link text when
     * available (X shows the unwrapped domain there), otherwise the anchor href. A t.co href
     * with no readable destination is returned as-is so it can be stored under its own key
     * and expanded later by native code.
     */
    static String resolveRealUrl(String rawUrl, String urlHint) {
        String fromHint = realUrlFromDisplay(urlHint);
        if (fromHint != null) {
            return fromHint;
        }
        return SupportedSite.validatedHttpsUrl(rawUrl);
    }

    /** Extracts an HTTPS URL from link text, ignoring bare short-link text. */
    static String realUrlFromDisplay(String displayText) {
        String candidate = SupportedSite.urlFromText(displayText);
        if (candidate == null || isShortLink(candidate)) {
            return null;
        }
        return candidate;
    }

    static boolean isShortLink(String httpsUrl) {
        try {
            String host = URI.create(httpsUrl).getHost();
            return host != null && "t.co".equals(host.toLowerCase(Locale.US));
        } catch (RuntimeException error) {
            return false;
        }
    }

    /**
     * Canonical de-duplication key for an already validated HTTPS URL: lowercase host without
     * a leading {@code www.}, path without trailing slashes, tracking parameters removed,
     * fragment dropped, scheme ignored (HTTPS was enforced by validation).
     */
    static String canonicalUrlKey(String httpsUrl) {
        if (httpsUrl == null || httpsUrl.isEmpty()) {
            return null;
        }
        try {
            URI uri = URI.create(httpsUrl);
            String rawHost = uri.getHost() == null ? "" : uri.getHost();
            String host = java.net.IDN.toASCII(rawHost).toLowerCase(Locale.US);
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }
            if (host.isEmpty()) {
                return null;
            }
            String path = uri.getPath() == null ? "" : uri.getPath();
            while (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }
            String key = host + path;
            String query = filteredQuery(uri.getRawQuery());
            if (!query.isEmpty()) {
                key += "?" + query;
            }
            return key.length() > MAXIMUM_URL_KEY_LENGTH ? null : key;
        } catch (RuntimeException error) {
            return null;
        }
    }

    private static String filteredQuery(String rawQuery) {
        if (rawQuery == null || rawQuery.isEmpty()) {
            return "";
        }
        StringBuilder kept = new StringBuilder();
        for (String pair : rawQuery.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            String name = pair.contains("=")
                    ? pair.substring(0, pair.indexOf('='))
                    : pair;
            String decoded = name.replace("+", " ").toLowerCase(Locale.US);
            if (TRACKING_PARAMETERS.contains(decoded)) {
                continue;
            }
            if (kept.length() > 0) {
                kept.append('&');
            }
            kept.append(pair);
        }
        return kept.toString();
    }
}
