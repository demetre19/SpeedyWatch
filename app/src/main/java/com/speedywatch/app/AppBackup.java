package com.speedywatch.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.EnumMap;
import java.util.Map;
import java.util.Base64;

final class AppBackup {
    static final int MAXIMUM_BYTES = 16 * 1024 * 1024;
    private static final int SCHEMA_VERSION = 1;
    private static final int MAXIMUM_ITEMS = 10_000;
    private static final int MAXIMUM_PROMPT_LENGTH = 100_000;
    private static final int MAXIMUM_CONTENT_LENGTH = 1_000_000;

    private AppBackup() {
    }

    static String create(
            SpeedyWatchSettings settings,
            SavedSummaryStore store,
            ScrapedLinkStore scrapedLinkStore
    ) throws JSONException {
        JSONObject preferences = new JSONObject()
                .put("modelId", settings.getModelId())
                .put("summaryOnePrompt", settings.getSummaryOnePrompt())
                .put("summaryTwoPrompt", settings.getSummaryTwoPrompt())
                .put("quizPrompt", settings.getQuizPrompt())
                .put("watchPathPrompt", settings.getWatchPathPrompt())
                .put("defaultPlaybackSpeed", settings.getDefaultPlaybackSpeed())
                .put("defaultMp3Quality", settings.getDefaultMp3Quality())
                .put("lockIconEnabled", settings.isLockIconEnabled())
                .put("pictureInPictureControl", settings.getPictureInPictureControl())
                .put("playbackProfile", settings.getPlaybackProfile())
                .put("adaptiveSpeedEnabled", settings.isAdaptiveSpeedEnabled())
                .put("adaptiveSpeedBoost", settings.getAdaptiveSpeedBoost())
                .put("speedControlsCollapsed", settings.areSpeedControlsCollapsed())
                .put("lockPositionX", settings.getLockPositionX())
                .put("lockPositionY", settings.getLockPositionY())
                .put("pictureInPicturePositionX", settings.getPictureInPicturePositionX())
                .put("pictureInPicturePositionY", settings.getPictureInPicturePositionY())
                .put("omniButtonEnabled", settings.isOmniButtonEnabled())
                .put("omniButtonPositionX", settings.getOmniButtonPositionX())
                .put("omniButtonPositionY", settings.getOmniButtonPositionY())
                .put("savedThumbnailsEnabled", settings.areSavedThumbnailsEnabled())
                .put("scrapedLinksBackupEnabled", settings.isScrapedLinksBackupEnabled())
                .put("autoScrapeXLinks", settings.isAutoScrapeXLinksEnabled())
                .put("sponsorBlockEnabled", settings.isSponsorBlockEnabled())
                .put("sponsorCategoryEnabled", settings.skipsSponsorSegments())
                .put("selfPromotionCategoryEnabled", settings.skipsSelfPromotionSegments())
                .put("interactionCategoryEnabled", settings.skipsInteractionSegments())
                .put("startPage", settings.getStartPage());
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> omniActions =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        EnumMap<OmniButtonGesture.Direction, Double> omniAmounts =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            omniActions.put(direction, settings.getOmniButtonAction(direction));
            omniAmounts.put(direction, settings.getOmniButtonAmount(direction));
        }
        preferences.put(
                "omniButtonGestures",
                encodeOmniButtonBindings(omniActions, omniAmounts)
        );
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> omniWebActions =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        EnumMap<OmniButtonGesture.Direction, Double> omniWebAmounts =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            omniWebActions.put(direction, settings.getOmniWebButtonAction(direction));
            omniWebAmounts.put(direction, settings.getOmniWebButtonAmount(direction));
        }
        preferences.put(
                "omniButtonGesturesWeb",
                encodeOmniButtonBindings(omniWebActions, omniWebAmounts)
        );
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> omniXActions =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        EnumMap<OmniButtonGesture.Direction, Double> omniXAmounts =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            omniXActions.put(direction, settings.getOmniXButtonAction(direction));
            omniXAmounts.put(direction, settings.getOmniXButtonAmount(direction));
        }
        preferences.put(
                "omniButtonGesturesX",
                encodeOmniButtonBindings(omniXActions, omniXAmounts)
        );
        JSONArray items = new JSONArray();
        for (SavedSummaryStore.Entry entry : store.loadAll()) {
            JSONObject item = new JSONObject()
                    .put("videoTitle", entry.videoTitle)
                    .put("contentLabel", entry.summaryLabel)
                    .put("content", entry.summaryText)
                    .put("sourceURL", entry.sourceUrl)
                    .put("channelName", entry.channelName)
                    .put("createdAt", entry.createdAt);
            if (entry.thumbnail != null) {
                item.put("thumbnail", Base64.getEncoder().encodeToString(entry.thumbnail));
            }
            items.put(item);
        }
        JSONArray scrapedLinks = null;
        if (settings.isScrapedLinksBackupEnabled()) {
            scrapedLinks = new JSONArray();
            for (ScrapedLinkStore.Entry link : scrapedLinkStore.snapshotForBackup()) {
                JSONObject item = new JSONObject()
                        .put("url", link.url)
                        .put("displayText", link.displayText)
                        .put("posterName", link.posterName)
                        .put("sourceUrl", link.sourceUrl)
                        .put("firstSeenAt", link.firstSeenAt)
                        .put("lastSeenAt", link.lastSeenAt);
                if (link.postedAt != null) {
                    item.put("postedAt", link.postedAt);
                }
                if (link.preview != null) {
                    item.put(
                            "preview",
                            Base64.getEncoder().encodeToString(link.preview)
                    );
                }
                scrapedLinks.put(item);
            }
        }
        JSONObject root = new JSONObject()
                .put("schemaVersion", SCHEMA_VERSION)
                .put("exportedAt", System.currentTimeMillis())
                .put("containsSecrets", false)
                .put("settings", preferences)
                .put("savedItems", items);
        if (scrapedLinks != null) {
            root.put("scrapedLinks", scrapedLinks);
        }
        String backup = root.toString(2);
        if (backup.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > MAXIMUM_BYTES) {
            throw new JSONException("Backup is too large");
        }
        return backup;
    }

    static void restore(
            String json,
            SpeedyWatchSettings settings,
            SavedSummaryStore store,
            ScrapedLinkStore scrapedLinkStore
    ) throws JSONException {
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
        String pictureInPictureControl = preferences.optString(
                "pictureInPictureControl",
                settings.getPictureInPictureControl()
        );
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
        boolean speedControlsCollapsed = preferences.optBoolean(
                "speedControlsCollapsed",
                settings.areSpeedControlsCollapsed()
        );
        float lockPositionX = optionalSavedPosition(
                preferences, "lockPositionX", settings.getLockPositionX());
        float lockPositionY = optionalSavedPosition(
                preferences, "lockPositionY", settings.getLockPositionY());
        float pictureInPicturePositionX = optionalSavedPosition(
                preferences,
                "pictureInPicturePositionX",
                settings.getPictureInPicturePositionX()
        );
        float pictureInPicturePositionY = optionalSavedPosition(
                preferences,
                "pictureInPicturePositionY",
                settings.getPictureInPicturePositionY()
        );
        boolean omniButtonEnabled = preferences.optBoolean(
                "omniButtonEnabled",
                settings.isOmniButtonEnabled()
        );
        float omniButtonPositionX = optionalSavedPosition(
                preferences,
                "omniButtonPositionX",
                settings.getOmniButtonPositionX()
        );
        float omniButtonPositionY = optionalSavedPosition(
                preferences,
                "omniButtonPositionY",
                settings.getOmniButtonPositionY()
        );
        boolean savedThumbnailsEnabled = preferences.optBoolean(
                "savedThumbnailsEnabled",
                settings.areSavedThumbnailsEnabled()
        );
        boolean scrapedLinksBackupEnabled = preferences.optBoolean(
                "scrapedLinksBackupEnabled",
                settings.isScrapedLinksBackupEnabled()
        );
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> omniActions =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        EnumMap<OmniButtonGesture.Direction, Double> omniAmounts =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            omniActions.put(direction, settings.getOmniButtonAction(direction));
            omniAmounts.put(direction, settings.getOmniButtonAmount(direction));
        }
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> omniWebActions =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        EnumMap<OmniButtonGesture.Direction, Double> omniWebAmounts =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            omniWebActions.put(direction, settings.getOmniWebButtonAction(direction));
            omniWebAmounts.put(direction, settings.getOmniWebButtonAmount(direction));
        }
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> omniXActions =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        EnumMap<OmniButtonGesture.Direction, Double> omniXAmounts =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            omniXActions.put(direction, settings.getOmniXButtonAction(direction));
            omniXAmounts.put(direction, settings.getOmniXButtonAmount(direction));
        }
        if (preferences.has("omniButtonGestures")) {
            Object rawBindings = preferences.opt("omniButtonGestures");
            if (!(rawBindings instanceof JSONObject bindings)) {
                throw new JSONException("Backup Omnibutton gestures are invalid");
            }
            decodeOmniButtonBindings(bindings, omniActions, omniAmounts);
        }
        if (preferences.has("omniButtonGesturesWeb")) {
            Object rawWebBindings = preferences.opt("omniButtonGesturesWeb");
            if (!(rawWebBindings instanceof JSONObject webBindings)) {
                throw new JSONException("Backup Omnibutton Web gestures are invalid");
            }
            decodeOmniButtonBindings(webBindings, omniWebActions, omniWebAmounts);
        }
        if (preferences.has("omniButtonGesturesX")) {
            Object rawXBindings = preferences.opt("omniButtonGesturesX");
            if (!(rawXBindings instanceof JSONObject xBindings)) {
                throw new JSONException("Backup Omnibutton X gestures are invalid");
            }
            decodeOmniButtonBindings(xBindings, omniXActions, omniXAmounts);
        }
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
            byte[] thumbnail = item.has("thumbnail")
                    ? decodeThumbnail(item)
                    : null;
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
                    thumbnail,
                    createdAt
            ));
        }

        List<ScrapedLinkStore.Entry> scrapedLinkRestores = new ArrayList<>();
        if (root.has("scrapedLinks")) {
            JSONArray linkItems = root.optJSONArray("scrapedLinks");
            if (linkItems == null
                    || linkItems.length() > ScrapedLinkStore.MAXIMUM_STORED_ROWS) {
                throw new JSONException("Backup X links are invalid");
            }
            for (int index = 0; index < linkItems.length(); index++) {
                JSONObject link = linkItems.optJSONObject(index);
                if (link == null) {
                    throw new JSONException("Backup X link is invalid");
                }
                String url = boundedString(link, "url", 4_000, false);
                String validUrl = SupportedSite.validatedHttpsUrl(url);
                String key = ScrapedLinkStore.canonicalUrlKey(validUrl);
                if (validUrl == null
                        || SupportedSite.forUrl(validUrl) == SupportedSite.MEGA
                        || key == null || key.isEmpty()) {
                    throw new JSONException("Backup link is invalid");
                }
                long firstSeenAt = link.optLong("firstSeenAt", -1);
                long lastSeenAt = link.optLong("lastSeenAt", -1);
                Long postedAt = null;
                if (link.has("postedAt")) {
                    long value = link.optLong("postedAt", -1);
                    postedAt = value > 0 ? value : null;
                }
                byte[] preview = link.has("preview")
                        ? decodePreview(link)
                        : null;
                if (firstSeenAt <= 0 || lastSeenAt <= 0) {
                    throw new JSONException("Backup link is invalid");
                }
                scrapedLinkRestores.add(new ScrapedLinkStore.Entry(
                        0,
                        key,
                        validUrl,
                        link.has("displayText")
                                ? boundedString(link, "displayText", 500, true) : "",
                        link.has("posterName")
                                ? boundedString(link, "posterName", 120, true) : "",
                        link.has("sourceUrl")
                                ? boundedString(link, "sourceUrl", 2_000, true) : "",
                        postedAt,
                        firstSeenAt,
                        lastSeenAt,
                        preview
                ));
            }
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
                pictureInPictureControl,
                playbackProfile,
                adaptiveEnabled,
                adaptiveBoost,
                mp3Quality,
                speedControlsCollapsed,
                lockPositionX,
                lockPositionY,
                pictureInPicturePositionX,
                pictureInPicturePositionY,
                omniButtonEnabled,
                omniButtonPositionX,
                omniButtonPositionY,
                omniActions,
                omniAmounts,
                savedThumbnailsEnabled
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
        settings.setScrapedLinksBackupEnabled(scrapedLinksBackupEnabled);
        settings.setAutoScrapeXLinksEnabled(preferences.optBoolean(
                "autoScrapeXLinks",
                settings.isAutoScrapeXLinksEnabled()
        ));
        settings.setStartPage(preferences.optString(
                "startPage",
                settings.getStartPage()
        ));
        if (preferences.has("omniButtonGesturesWeb")) {
            settings.setOmniWebButtonBindings(omniWebActions, omniWebAmounts);
        }
        if (preferences.has("omniButtonGesturesX")) {
            settings.setOmniXButtonBindings(omniXActions, omniXAmounts);
        }
        if (root.has("scrapedLinks")) {
            scrapedLinkStore.replaceAll(scrapedLinkRestores);
        }
    }

    static JSONObject encodeOmniButtonBindings(
            Map<OmniButtonGesture.Direction, OmniButtonAction> actions,
            Map<OmniButtonGesture.Direction, Double> amounts
    ) throws JSONException {
        if (!SpeedyWatchSettings.validOmniButtonBindings(actions, amounts)) {
            throw new JSONException("Omnibutton gestures are invalid");
        }
        JSONObject encoded = new JSONObject();
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            OmniButtonAction action = actions.get(direction);
            JSONObject binding = new JSONObject().put("action", action.id);
            if (action.usesAmount()) {
                binding.put("amount", amounts.get(direction));
            }
            encoded.put(direction.id, binding);
        }
        return encoded;
    }

    static void decodeOmniButtonBindings(
            JSONObject encoded,
            Map<OmniButtonGesture.Direction, OmniButtonAction> actions,
            Map<OmniButtonGesture.Direction, Double> amounts
    ) throws JSONException {
        if (encoded == null
                || encoded.length() != OmniButtonGesture.Direction.configurableValues().length) {
            throw new JSONException("Backup Omnibutton gestures are incomplete");
        }
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> decodedActions =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        EnumMap<OmniButtonGesture.Direction, Double> decodedAmounts =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            Object rawBinding = encoded.opt(direction.id);
            if (!(rawBinding instanceof JSONObject binding)) {
                throw new JSONException("Backup Omnibutton direction is invalid");
            }
            Object rawAction = binding.opt("action");
            OmniButtonAction action = rawAction instanceof String
                    ? OmniButtonAction.fromId((String) rawAction)
                    : null;
            int expectedFields = action != null && action.usesAmount() ? 2 : 1;
            if (action == null || binding.length() != expectedFields) {
                throw new JSONException("Backup Omnibutton action is invalid");
            }
            decodedActions.put(direction, action);
            if (action.usesAmount()) {
                Object rawAmount = binding.opt("amount");
                if (!(rawAmount instanceof Number number)
                        || !action.acceptsAmount(number.doubleValue())) {
                    throw new JSONException("Backup Omnibutton amount is invalid");
                }
                decodedAmounts.put(direction, number.doubleValue());
            } else {
                decodedAmounts.put(direction, Double.NaN);
            }
        }
        if (!SpeedyWatchSettings.validOmniButtonBindings(decodedActions, decodedAmounts)) {
            throw new JSONException("Backup Omnibutton gestures are invalid");
        }
        actions.clear();
        actions.putAll(decodedActions);
        amounts.clear();
        amounts.putAll(decodedAmounts);
    }

    private static byte[] decodeThumbnail(JSONObject item) throws JSONException {
        String encoded = boundedString(
                item,
                "thumbnail",
                ((SavedThumbnail.MAX_BYTES + 2) / 3) * 4,
                false
        );
        try {
            byte[] thumbnail = Base64.getDecoder().decode(encoded);
            if (thumbnail.length == 0 || thumbnail.length > SavedThumbnail.MAX_BYTES) {
                throw new JSONException("Backup thumbnail is invalid");
            }
            return thumbnail;
        } catch (IllegalArgumentException error) {
            throw new JSONException("Backup thumbnail is invalid");
        }
    }
    private static byte[] decodePreview(JSONObject item) throws JSONException {
        String encoded = boundedString(
                item,
                "preview",
                ((SavedThumbnail.MAX_BYTES + 2) / 3) * 4,
                false
        );
        try {
            byte[] preview = Base64.getDecoder().decode(encoded);
            if (preview.length == 0 || preview.length > SavedThumbnail.MAX_BYTES) {
                throw new JSONException("Backup link preview is invalid");
            }
            return preview;
        } catch (IllegalArgumentException error) {
            throw new JSONException("Backup link preview is invalid");
        }
    }

    private static float optionalSavedPosition(
            JSONObject object,
            String key,
            float fallback
    ) throws JSONException {
        if (!object.has(key)) {
            return fallback;
        }
        Object raw = object.opt(key);
        if (!(raw instanceof Number number)) {
            throw new JSONException("Backup field " + key + " is invalid");
        }
        float value = number.floatValue();
        if (!SpeedyWatchSettings.isSavedPosition(value)) {
            throw new JSONException("Backup field " + key + " is invalid");
        }
        return value;
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
