package com.speedywatch.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class AppBackup {
    static final int MAXIMUM_BYTES = 16 * 1024 * 1024;
    private static final int SCHEMA_VERSION = 1;
    private static final int MAXIMUM_ITEMS = 10_000;
    private static final int MAXIMUM_PROMPT_LENGTH = 100_000;
    private static final int MAXIMUM_CONTENT_LENGTH = 1_000_000;

    private AppBackup() {
    }

    static String create(SpeedyWatchSettings settings, SavedSummaryStore store) throws JSONException {
        JSONObject preferences = new JSONObject()
                .put("modelId", settings.getModelId())
                .put("summaryOnePrompt", settings.getSummaryOnePrompt())
                .put("summaryTwoPrompt", settings.getSummaryTwoPrompt())
                .put("quizPrompt", settings.getQuizPrompt())
                .put("watchPathPrompt", settings.getWatchPathPrompt())
                .put("defaultPlaybackSpeed", settings.getDefaultPlaybackSpeed())
                .put("defaultMp3Quality", settings.getDefaultMp3Quality())
                .put("lockIconEnabled", settings.isLockIconEnabled())
                .put("playbackProfile", settings.getPlaybackProfile())
                .put("adaptiveSpeedEnabled", settings.isAdaptiveSpeedEnabled())
                .put("adaptiveSpeedBoost", settings.getAdaptiveSpeedBoost())
                .put("sponsorBlockEnabled", settings.isSponsorBlockEnabled())
                .put("sponsorCategoryEnabled", settings.skipsSponsorSegments())
                .put("selfPromotionCategoryEnabled", settings.skipsSelfPromotionSegments())
                .put("interactionCategoryEnabled", settings.skipsInteractionSegments());
        JSONArray items = new JSONArray();
        for (SavedSummaryStore.Entry entry : store.loadAll()) {
            items.put(new JSONObject()
                    .put("videoTitle", entry.videoTitle)
                    .put("contentLabel", entry.summaryLabel)
                    .put("content", entry.summaryText)
                    .put("sourceURL", entry.sourceUrl)
                    .put("channelName", entry.channelName)
                    .put("createdAt", entry.createdAt));
        }
        return new JSONObject()
                .put("schemaVersion", SCHEMA_VERSION)
                .put("exportedAt", System.currentTimeMillis())
                .put("containsSecrets", false)
                .put("settings", preferences)
                .put("savedItems", items)
                .toString(2);
    }

    static void restore(String json, SpeedyWatchSettings settings, SavedSummaryStore store)
            throws JSONException {
        if (json == null || json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAXIMUM_BYTES) {
            throw new JSONException("Backup file is too large");
        }
        JSONObject root = new JSONObject(json);
        if (root.optInt("schemaVersion", -1) != SCHEMA_VERSION) {
            throw new JSONException("Unsupported SpeedyWatch backup version");
        }
        JSONObject preferences = root.optJSONObject("settings");
        JSONArray items = root.optJSONArray("savedItems");
        if (preferences == null || items == null || items.length() > MAXIMUM_ITEMS) {
            throw new JSONException("Backup data is incomplete or too large");
        }

        String modelId = boundedString(preferences, "modelId", 300, true);
        String summaryOne = boundedString(preferences, "summaryOnePrompt", MAXIMUM_PROMPT_LENGTH, false);
        String summaryTwo = boundedString(preferences, "summaryTwoPrompt", MAXIMUM_PROMPT_LENGTH, false);
        String quiz = boundedString(preferences, "quizPrompt", MAXIMUM_PROMPT_LENGTH, false);
        String watchPath = preferences.has("watchPathPrompt")
                ? boundedString(preferences, "watchPathPrompt", MAXIMUM_PROMPT_LENGTH, false)
                : settings.getWatchPathPrompt();
        double speed = preferences.optDouble("defaultPlaybackSpeed", Double.NaN);
        String mp3Quality = preferences.optString(
                "defaultMp3Quality",
                SpeedyWatchSettings.MP3_QUALITY_STANDARD
        );
        boolean lockEnabled = preferences.optBoolean("lockIconEnabled", true);
        String playbackProfile = preferences.optString(
                "playbackProfile", SpeedyWatchSettings.PROFILE_NORMAL);
        boolean adaptiveEnabled = preferences.optBoolean("adaptiveSpeedEnabled", false);
        double adaptiveBoost = preferences.optDouble("adaptiveSpeedBoost", 0.5);
        boolean sponsorBlockEnabled = preferences.optBoolean("sponsorBlockEnabled", false);
        boolean sponsorCategoryEnabled = preferences.optBoolean("sponsorCategoryEnabled", true);
        boolean selfPromotionCategoryEnabled =
                preferences.optBoolean("selfPromotionCategoryEnabled", true);
        boolean interactionCategoryEnabled =
                preferences.optBoolean("interactionCategoryEnabled", false);
        if (!Double.isFinite(speed) || speed < 0.25 || speed > 4) {
            throw new JSONException("Backup playback speed is invalid");
        }

        List<SavedSummaryStore.Entry> restored = new ArrayList<>(items.length());
        for (int index = 0; index < items.length(); index++) {
            JSONObject item = items.optJSONObject(index);
            if (item == null) {
                throw new JSONException("Backup saved item is invalid");
            }
            String title = boundedString(item, "videoTitle", 2_000, false);
            String label = boundedString(item, "contentLabel", 500, false);
            String content = boundedString(item, "content", MAXIMUM_CONTENT_LENGTH, false);
            String sourceURL = boundedString(item, "sourceURL", 4_000, false);
            String channelName = item.has("channelName")
                    ? boundedString(item, "channelName", 300, true)
                    : "";
            long createdAt = item.optLong("createdAt", -1);
            if (!SavedSummaryStore.isSupportedSourceUrl(sourceURL) || createdAt <= 0) {
                throw new JSONException("Backup saved item source is invalid");
            }
            restored.add(new SavedSummaryStore.Entry(
                    0,
                    title,
                    label,
                    content,
                    sourceURL,
                    channelName,
                    createdAt
            ));
        }

        List<SavedSummaryStore.Entry> previous = store.loadAll();
        store.replaceAll(restored);
        if (!settings.restoreBackup(
                modelId,
                summaryOne,
                summaryTwo,
                quiz,
                watchPath,
                speed,
                lockEnabled,
                playbackProfile,
                adaptiveEnabled,
                adaptiveBoost,
                mp3Quality
        )) {
            try {
                store.replaceAll(previous);
            } catch (RuntimeException ignored) {
                // Preserve the original settings error below.
            }
            throw new JSONException("Settings could not be restored");
        }
        settings.setSponsorBlockPreferences(
                sponsorBlockEnabled,
                sponsorCategoryEnabled,
                selfPromotionCategoryEnabled,
                interactionCategoryEnabled
        );
    }

    private static String boundedString(
            JSONObject object,
            String key,
            int maximumLength,
            boolean allowEmpty
    ) throws JSONException {
        Object raw = object.opt(key);
        if (!(raw instanceof String value) || value.length() > maximumLength
                || (!allowEmpty && value.trim().isEmpty())) {
            throw new JSONException("Backup field " + key + " is invalid");
        }
        return value;
    }
}
