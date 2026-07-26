package com.speedywatch.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.net.Uri;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class SavedSummaryStore extends SQLiteOpenHelper {

    static final class Entry {
        final long id;
        final String videoTitle;
        final String summaryLabel;
        final String summaryText;
        final String sourceUrl;
        final long createdAt;

        Entry(
                long id,
                String videoTitle,
                String summaryLabel,
                String summaryText,
                String sourceUrl,
                long createdAt
        ) {
            this.id = id;
            this.videoTitle = videoTitle;
            this.summaryLabel = summaryLabel;
            this.summaryText = summaryText;
            this.sourceUrl = sourceUrl;
            this.createdAt = createdAt;
        }
    }

    private static final String DATABASE_NAME = "saved_summaries.db";
    private static final int DATABASE_VERSION = 3;
    private static final String TABLE = "saved_summaries";
    private static final String CACHE_TABLE = "summary_cache";

    SavedSummaryStore(Context context) {
        super(context.getApplicationContext(), DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE " + TABLE + " ("
                        + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                        + "video_title TEXT NOT NULL,"
                        + "summary_label TEXT NOT NULL,"
                        + "summary_text TEXT NOT NULL,"
                        + "source_url TEXT NOT NULL,"
                        + "created_at INTEGER NOT NULL)"
        );
        database.execSQL(
                "CREATE INDEX saved_summaries_created_at ON " + TABLE + " (created_at DESC)"
        );
        createCacheTable(database);
    }

    @Override
    public void onUpgrade(SQLiteDatabase database, int oldVersion, int newVersion) {
        if (oldVersion < 3) {
            createCacheTable(database);
        }
    }

    private static void createCacheTable(SQLiteDatabase database) {
        database.execSQL(
                "CREATE TABLE IF NOT EXISTS " + CACHE_TABLE + " ("
                        + "cache_key TEXT PRIMARY KEY,"
                        + "summary_text TEXT NOT NULL,"
                        + "created_at INTEGER NOT NULL)"
        );
    }


    synchronized void save(
            String videoTitle,
            String summaryLabel,
            String summaryText,
            String sourceUrl
    ) {
        String normalizedTitle = requireText(videoTitle, "Video title");
        String normalizedLabel = requireText(summaryLabel, "Saved item label");
        String normalizedSummary = requireText(summaryText, "Saved item content");
        String normalizedUrl = requireText(sourceUrl, "Source URL");
        if (!isSupportedSourceUrl(normalizedUrl)) {
            throw new IllegalArgumentException("Original YouTube URL is unavailable");
        }

        SQLiteDatabase database = getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("video_title", normalizedTitle);
        values.put("summary_label", normalizedLabel);
        values.put("summary_text", normalizedSummary);
        values.put("source_url", normalizedUrl);
        values.put("created_at", System.currentTimeMillis());
        if (database.insertOrThrow(TABLE, null, values) < 0) {
            throw new IllegalStateException("Item could not be saved");
        }
    }



    synchronized void cacheSummary(String cacheKey, String summaryText) {
        ContentValues values = new ContentValues();
        values.put("cache_key", requireText(cacheKey, "Summary cache key"));
        values.put("summary_text", requireText(summaryText, "Summary cache content"));
        values.put("created_at", System.currentTimeMillis());
        if (getWritableDatabase().insertWithOnConflict(
                CACHE_TABLE,
                null,
                values,
                SQLiteDatabase.CONFLICT_REPLACE
        ) < 0) {
            throw new IllegalStateException("Summary could not be cached");
        }
    }

    synchronized String loadCachedSummary(String cacheKey) {
        String normalizedKey = requireText(cacheKey, "Summary cache key");
        try (Cursor cursor = getReadableDatabase().query(
                CACHE_TABLE,
                new String[]{"summary_text"},
                "cache_key = ?",
                new String[]{normalizedKey},
                null,
                null,
                null,
                "1"
        )) {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        }
    }

    synchronized List<Entry> loadAll() {
        List<Entry> entries = new ArrayList<>();
        SQLiteDatabase database = getReadableDatabase();
        try (Cursor cursor = database.query(
                TABLE,
                new String[]{
                        "id",
                        "video_title",
                        "summary_label",
                        "summary_text",
                        "source_url",
                        "created_at"
                },
                null,
                null,
                null,
                null,
                "created_at DESC, id DESC"
        )) {
            while (cursor.moveToNext()) {
                entries.add(new Entry(
                        cursor.getLong(0),
                        cursor.getString(1),
                        cursor.getString(2),
                        cursor.getString(3),
                        cursor.getString(4),
                        cursor.getLong(5)
                ));
            }
        }
        return entries;
    }

    synchronized void replaceAll(List<Entry> restoredEntries) {
        if (restoredEntries == null) {
            throw new IllegalArgumentException("Saved items are unavailable");
        }
        List<Entry> validated = new ArrayList<>(restoredEntries.size());
        for (Entry entry : restoredEntries) {
            String title = requireText(entry.videoTitle, "Video title");
            String label = requireText(entry.summaryLabel, "Saved item label");
            String text = requireText(entry.summaryText, "Saved item content");
            String sourceUrl = requireText(entry.sourceUrl, "Source URL");
            if (!isSupportedSourceUrl(sourceUrl) || entry.createdAt <= 0) {
                throw new IllegalArgumentException("Saved item data is invalid");
            }
            validated.add(new Entry(0, title, label, text, sourceUrl, entry.createdAt));
        }

        SQLiteDatabase database = getWritableDatabase();
        database.beginTransaction();
        try {
            database.delete(TABLE, null, null);
            for (Entry entry : validated) {
                ContentValues values = new ContentValues();
                values.put("video_title", entry.videoTitle);
                values.put("summary_label", entry.summaryLabel);
                values.put("summary_text", entry.summaryText);
                values.put("source_url", entry.sourceUrl);
                values.put("created_at", entry.createdAt);
                database.insertOrThrow(TABLE, null, values);
            }
            database.setTransactionSuccessful();
        } finally {
            database.endTransaction();
        }
    }

    synchronized boolean delete(long id) {
        return id > 0 && getWritableDatabase().delete(TABLE, "id = ?", new String[]{Long.toString(id)}) > 0;
    }

    static boolean isSupportedSourceUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        Uri uri = Uri.parse(value.trim());
        String host = uri.getHost();
        if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null) {
            return false;
        }
        String normalizedHost = host.toLowerCase(Locale.US);
        return normalizedHost.equals("youtube.com")
                || normalizedHost.endsWith(".youtube.com")
                || normalizedHost.equals("youtu.be");
    }

    private static String requireText(String value, String label) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(label + " is unavailable");
        }
        return normalized;
    }
}
