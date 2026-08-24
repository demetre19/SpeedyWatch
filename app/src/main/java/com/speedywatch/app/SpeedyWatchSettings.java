package com.speedywatch.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.EnumMap;
import java.util.Map;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class SpeedyWatchSettings {
    static final String PREFERRED_MODEL_ID = "inception/mercury-2";

    private static final String PREFERENCES = "speedywatch_settings";
    private static final String KEY_ALIAS = "speedywatch_openrouter_key";
    private static final String API_KEY_CIPHERTEXT = "openrouter_key_ciphertext";
    private static final String API_KEY_IV = "openrouter_key_iv";
    private static final String MODEL_ID = "openrouter_model_id";
    private static final String SUMMARY_ONE = "summary_one_prompt";
    private static final String SUMMARY_TWO = "summary_two_prompt";
    private static final String QUIZ = "quiz_prompt";
    private static final String WATCH_PATH = "watch_path_prompt";
    private static final String DEFAULT_PLAYBACK_SPEED = "default_playback_speed";
    private static final String DEFAULT_MP3_QUALITY = "default_mp3_quality";
    private static final String LOCK_ICON_ENABLED = "lock_icon_enabled";
    private static final String PLAYBACK_PROFILE = "playback_profile";
    private static final String ADAPTIVE_SPEED_ENABLED = "adaptive_speed_enabled";
    private static final String ADAPTIVE_SPEED_BOOST = "adaptive_speed_boost";
    private static final String SPONSORBLOCK_ENABLED = "sponsorblock_enabled";
    private static final String SPONSORBLOCK_SPONSOR = "sponsorblock_sponsor";
    private static final String SPONSORBLOCK_SELF_PROMOTION = "sponsorblock_self_promotion";
    private static final String SPONSORBLOCK_INTERACTION = "sponsorblock_interaction";
    private static final String SPEED_CONTROLS_COLLAPSED = "speed_controls_collapsed";
    private static final String LOCK_POSITION_X = "lock_position_x";
    private static final String LOCK_POSITION_Y = "lock_position_y";
    private static final String PIP_POSITION_X = "pip_position_x";
    private static final String PIP_POSITION_Y = "pip_position_y";
    private static final String PICTURE_IN_PICTURE_CONTROL = "picture_in_picture_control";
    private static final String OMNI_BUTTON_ENABLED = "omni_button_enabled";
    private static final String OMNI_POSITION_X = "omni_position_x";
    private static final String OMNI_POSITION_Y = "omni_position_y";
    private static final String OMNI_ACTION_PREFIX = "omni_action_";
    private static final String OMNI_AMOUNT_PREFIX = "omni_amount_";
    private static final String OMNI_WEB_ACTION_PREFIX = "omni_web_action_";
    private static final String OMNI_WEB_AMOUNT_PREFIX = "omni_web_amount_";
    private static final String OMNI_X_ACTION_PREFIX = "omni_x_action_";
    private static final String OMNI_X_AMOUNT_PREFIX = "omni_x_amount_";
    private static final String SAVED_THUMBNAILS_ENABLED = "saved_thumbnails_enabled";
    private static final String SCRAPED_LINKS_BACKUP_ENABLED = "scraped_links_backup_enabled";
    private static final String AUTO_SCRAPE_X_LINKS = "auto_scrape_x_links";
    static final String PIP_CONTROL_BUTTON = "button";
    static final String PIP_CONTROL_PINCH = "pinch";
    static final String PROFILE_NORMAL = "normal";
    static final String PROFILE_CAREFUL = "careful";
    static final String PROFILE_LECTURE = "lecture";
    static final String PROFILE_PODCAST = "podcast";
    static final String MP3_QUALITY_HIGH = "high";
    static final String MP3_QUALITY_STANDARD = "standard";
    static final String MP3_QUALITY_COMPACT = "compact";
    private static final String LEGACY_SUMMARY_ONE_PROMPT =
            "You are a concise video content summariser. Provide a clear, well-structured summary of the following YouTube video transcript. Include:\n"
                    + "- A brief overview of the video topic (2-3 sentences)\n"
                    + "- Key points as bullet points\n"
                    + "- Any notable conclusions or takeaways\n\n"
                    + "Keep the summary factual and focused. Do not add opinions or information not present in the transcript.";

    private final Context context;
    private final SharedPreferences preferences;

    SpeedyWatchSettings(Context context) {
        this.context = context.getApplicationContext();
        preferences = this.context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        String summaryOne = preferences.getString(SUMMARY_ONE, "");
        if (LEGACY_SUMMARY_ONE_PROMPT.equals(summaryOne)) {
            preferences.edit()
                    .putString(SUMMARY_ONE, this.context.getString(R.string.summary_one_prompt_default))
                    .apply();
        }
    }

    double getDefaultPlaybackSpeed() {
        return Math.max(0.25, Math.min(4.0,
                Double.longBitsToDouble(preferences.getLong(
                        DEFAULT_PLAYBACK_SPEED,
                        Double.doubleToRawLongBits(1.0)
                ))));
    }

    void setDefaultPlaybackSpeed(double speed) {
        double bounded = Math.max(0.25, Math.min(4.0, speed));
        preferences.edit()
                .putLong(DEFAULT_PLAYBACK_SPEED, Double.doubleToRawLongBits(bounded))
                .apply();
    }

    String getDefaultMp3Quality() {
        String quality = preferences.getString(DEFAULT_MP3_QUALITY, MP3_QUALITY_STANDARD);
        return isMp3Quality(quality) ? quality : MP3_QUALITY_STANDARD;
    }

    void setDefaultMp3Quality(String quality) {
        preferences.edit()
                .putString(
                        DEFAULT_MP3_QUALITY,
                        isMp3Quality(quality) ? quality : MP3_QUALITY_STANDARD
                )
                .apply();
    }

    static String mp3QualityLabel(String quality) {
        if (MP3_QUALITY_HIGH.equals(quality)) {
            return "High (192 kbps)";
        }
        if (MP3_QUALITY_COMPACT.equals(quality)) {
            return "Compact (64 kbps)";
        }
        return "Standard (128 kbps)";
    }

    static String mp3BitrateForQuality(String quality) {
        if (MP3_QUALITY_HIGH.equals(quality)) {
            return "192K";
        }
        if (MP3_QUALITY_COMPACT.equals(quality)) {
            return "64K";
        }
        return "128K";
    }

    static String nextMp3Quality(String quality) {
        if (MP3_QUALITY_STANDARD.equals(quality)) {
            return MP3_QUALITY_HIGH;
        }
        if (MP3_QUALITY_HIGH.equals(quality)) {
            return MP3_QUALITY_COMPACT;
        }
        return MP3_QUALITY_STANDARD;
    }
    String getPlaybackProfile() {
        String profile = preferences.getString(PLAYBACK_PROFILE, PROFILE_NORMAL);
        return isPlaybackProfile(profile) ? profile : PROFILE_NORMAL;
    }

    void setPlaybackPreferences(String profile, boolean adaptiveEnabled, double adaptiveBoost) {
        String boundedProfile = isPlaybackProfile(profile) ? profile : PROFILE_NORMAL;
        double boundedBoost = Math.max(0.1, Math.min(1.5, adaptiveBoost));
        preferences.edit()
                .putString(PLAYBACK_PROFILE, boundedProfile)
                .putBoolean(ADAPTIVE_SPEED_ENABLED, adaptiveEnabled)
                .putLong(ADAPTIVE_SPEED_BOOST, Double.doubleToRawLongBits(boundedBoost))
                .apply();
    }

    boolean isAdaptiveSpeedEnabled() {
        return preferences.getBoolean(ADAPTIVE_SPEED_ENABLED, false);
    }

    double getAdaptiveSpeedBoost() {
        return Math.max(0.1, Math.min(1.5, Double.longBitsToDouble(preferences.getLong(
                ADAPTIVE_SPEED_BOOST,
                Double.doubleToRawLongBits(0.5)
        ))));
    }

    static double speedForProfile(String profile) {
        if (PROFILE_CAREFUL.equals(profile)) {
            return 0.8;
        }
        if (PROFILE_LECTURE.equals(profile)) {
            return 1.5;
        }
        if (PROFILE_PODCAST.equals(profile)) {
            return 2.0;
        }
        return 1.0;
    }

    static String profileLabel(String profile) {
        if (PROFILE_CAREFUL.equals(profile)) {
            return "Careful · 0.8x";
        }
        if (PROFILE_LECTURE.equals(profile)) {
            return "Lecture · 1.5x";
        }
        if (PROFILE_PODCAST.equals(profile)) {
            return "Podcast · 2x";
        }
        return "Normal · 1x";
    }

    private static boolean isPlaybackProfile(String profile) {
        return PROFILE_NORMAL.equals(profile)
                || PROFILE_CAREFUL.equals(profile)
                || PROFILE_LECTURE.equals(profile)
                || PROFILE_PODCAST.equals(profile);
    }

    static boolean isMp3Quality(String quality) {
        return MP3_QUALITY_HIGH.equals(quality)
                || MP3_QUALITY_STANDARD.equals(quality)
                || MP3_QUALITY_COMPACT.equals(quality);
    }
    boolean isSponsorBlockEnabled() {
        return preferences.getBoolean(SPONSORBLOCK_ENABLED, false);
    }

    boolean skipsSponsorSegments() {
        return preferences.getBoolean(SPONSORBLOCK_SPONSOR, true);
    }

    boolean skipsSelfPromotionSegments() {
        return preferences.getBoolean(SPONSORBLOCK_SELF_PROMOTION, true);
    }

    boolean skipsInteractionSegments() {
        return preferences.getBoolean(SPONSORBLOCK_INTERACTION, false);
    }

    void setSponsorBlockPreferences(
            boolean enabled,
            boolean sponsor,
            boolean selfPromotion,
            boolean interaction
    ) {
        preferences.edit()
                .putBoolean(SPONSORBLOCK_ENABLED, enabled)
                .putBoolean(SPONSORBLOCK_SPONSOR, sponsor)
                .putBoolean(SPONSORBLOCK_SELF_PROMOTION, selfPromotion)
                .putBoolean(SPONSORBLOCK_INTERACTION, interaction)
                .apply();
    }

    boolean isLockIconEnabled() {
        return preferences.getBoolean(LOCK_ICON_ENABLED, true);
    }

    void setLockIconEnabled(boolean enabled) {
        preferences.edit().putBoolean(LOCK_ICON_ENABLED, enabled).apply();
    }

    String getPictureInPictureControl() {
        String control = preferences.getString(
                PICTURE_IN_PICTURE_CONTROL,
                PIP_CONTROL_BUTTON
        );
        return isPictureInPictureControl(control) ? control : PIP_CONTROL_BUTTON;
    }

    void setPictureInPictureControl(String control) {
        preferences.edit()
                .putString(
                        PICTURE_IN_PICTURE_CONTROL,
                        isPictureInPictureControl(control) ? control : PIP_CONTROL_BUTTON
                )
                .apply();
    }

    static boolean isPictureInPictureControl(String control) {
        return PIP_CONTROL_BUTTON.equals(control) || PIP_CONTROL_PINCH.equals(control);
    }

    boolean isOmniButtonEnabled() {
        return preferences.getBoolean(OMNI_BUTTON_ENABLED, false);
    }

    void setOmniButtonEnabled(boolean enabled) {
        preferences.edit().putBoolean(OMNI_BUTTON_ENABLED, enabled).apply();
    }

    OmniButtonAction getOmniButtonAction(OmniButtonGesture.Direction direction) {
        if (direction == null || direction == OmniButtonGesture.Direction.NONE) {
            return OmniButtonAction.NONE;
        }
        OmniButtonAction fallback = OmniButtonAction.defaultFor(direction);
        OmniButtonAction saved = OmniButtonAction.fromId(
                preferences.getString(omniActionKey(direction), fallback.id)
        );
        return saved == null ? fallback : saved;
    }

    double getOmniButtonAmount(OmniButtonGesture.Direction direction) {
        OmniButtonAction action = getOmniButtonAction(direction);
        double fallback = OmniButtonAction.defaultAmount(action);
        if (!action.usesAmount()) {
            return Double.NaN;
        }
        double saved = Double.longBitsToDouble(preferences.getLong(
                omniAmountKey(direction),
                Double.doubleToRawLongBits(fallback)
        ));
        return action.acceptsAmount(saved) ? saved : fallback;
    }

    boolean setOmniButtonBindings(
            Map<OmniButtonGesture.Direction, OmniButtonAction> actions,
            Map<OmniButtonGesture.Direction, Double> amounts
    ) {
        if (!validOmniButtonBindings(actions, amounts)) {
            return false;
        }
        SharedPreferences.Editor editor = preferences.edit();
        putOmniButtonBindings(editor, actions, amounts);
        editor.apply();
        return true;
    }

    /** Browser-first defaults used on regular Web pages and non-YouTube/X services. */
    static EnumMap<OmniButtonGesture.Direction, OmniButtonAction> defaultOmniWebButtonActions() {
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> actions =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            switch (direction) {
                case UP:
                    actions.put(direction, OmniButtonAction.SUMMARY_ONE);
                    break;
                case UP_RIGHT:
                    actions.put(direction, OmniButtonAction.SPEED_UP);
                    break;
                case RIGHT:
                    actions.put(direction, OmniButtonAction.BROWSER_FORWARD);
                    break;
                case DOWN_RIGHT:
                    actions.put(direction, OmniButtonAction.SHARE);
                    break;
                case DOWN:
                    actions.put(direction, OmniButtonAction.SAVED);
                    break;
                case DOWN_LEFT:
                    actions.put(direction, OmniButtonAction.RELOAD);
                    break;
                case LEFT:
                    actions.put(direction, OmniButtonAction.BROWSER_BACK);
                    break;
                case UP_LEFT:
                default:
                    actions.put(direction, OmniButtonAction.SPEED_DOWN);
                    break;
            }
        }
        return actions;
    }

    static EnumMap<OmniButtonGesture.Direction, Double> defaultOmniWebButtonAmounts() {
        EnumMap<OmniButtonGesture.Direction, Double> amounts =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        Map<OmniButtonGesture.Direction, OmniButtonAction> actions =
                defaultOmniWebButtonActions();
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            amounts.put(direction, OmniButtonAction.defaultAmount(actions.get(direction)));
        }
        return amounts;
    }

    OmniButtonAction getOmniWebButtonAction(OmniButtonGesture.Direction direction) {
        if (direction == null || direction == OmniButtonGesture.Direction.NONE) {
            return OmniButtonAction.NONE;
        }
        OmniButtonAction fallback = defaultOmniWebButtonActions().get(direction);
        OmniButtonAction saved = OmniButtonAction.fromId(
                preferences.getString(omniWebActionKey(direction), fallback.id)
        );
        return saved == null ? fallback : saved;
    }

    double getOmniWebButtonAmount(OmniButtonGesture.Direction direction) {
        OmniButtonAction action = getOmniWebButtonAction(direction);
        if (!action.usesAmount()) {
            return Double.NaN;
        }
        double fallback = OmniButtonAction.defaultAmount(action);
        double saved = Double.longBitsToDouble(preferences.getLong(
                omniWebAmountKey(direction),
                Double.doubleToRawLongBits(fallback)
        ));
        return action.acceptsAmount(saved) ? saved : fallback;
    }

    boolean setOmniWebButtonBindings(
            Map<OmniButtonGesture.Direction, OmniButtonAction> actions,
            Map<OmniButtonGesture.Direction, Double> amounts
    ) {
        if (!validOmniButtonBindings(actions, amounts)) {
            return false;
        }
        SharedPreferences.Editor editor = preferences.edit();
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            OmniButtonAction action = actions.get(direction);
            editor.putString(omniWebActionKey(direction), action.id);
            if (action.usesAmount()) {
                editor.putLong(
                        omniWebAmountKey(direction),
                        Double.doubleToRawLongBits(amounts.get(direction))
                );
            } else {
                editor.remove(omniWebAmountKey(direction));
            }
        }
        editor.apply();
        return true;
    }

    /** Harvest-focused defaults used on X until the user remaps the X accordion. */
    static EnumMap<OmniButtonGesture.Direction, OmniButtonAction> defaultOmniXButtonActions() {
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> actions =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            switch (direction) {
                case UP:
                    actions.put(direction, OmniButtonAction.SAVE_X_LINKS);
                    break;
                case UP_RIGHT:
                    actions.put(direction, OmniButtonAction.DOWNLOAD);
                    break;
                case RIGHT:
                    actions.put(direction, OmniButtonAction.BROWSER_FORWARD);
                    break;
                case DOWN_RIGHT:
                    actions.put(direction, OmniButtonAction.SHARE);
                    break;
                case DOWN:
                    actions.put(direction, OmniButtonAction.SAVED);
                    break;
                case DOWN_LEFT:
                    actions.put(direction, OmniButtonAction.RELOAD);
                    break;
                case LEFT:
                    actions.put(direction, OmniButtonAction.BROWSER_BACK);
                    break;
                case UP_LEFT:
                default:
                    actions.put(direction, OmniButtonAction.PLAY_PAUSE);
                    break;
            }
        }
        return actions;
    }

    OmniButtonAction getOmniXButtonAction(OmniButtonGesture.Direction direction) {
        if (direction == null || direction == OmniButtonGesture.Direction.NONE) {
            return OmniButtonAction.NONE;
        }
        OmniButtonAction fallback = defaultOmniXButtonActions().get(direction);
        OmniButtonAction saved = OmniButtonAction.fromId(
                preferences.getString(omniXActionKey(direction), fallback.id)
        );
        return saved == null ? fallback : saved;
    }

    double getOmniXButtonAmount(OmniButtonGesture.Direction direction) {
        OmniButtonAction action = getOmniXButtonAction(direction);
        if (!action.usesAmount()) {
            return Double.NaN;
        }
        double fallback = OmniButtonAction.defaultAmount(action);
        double saved = Double.longBitsToDouble(preferences.getLong(
                omniXAmountKey(direction),
                Double.doubleToRawLongBits(fallback)
        ));
        return action.acceptsAmount(saved) ? saved : fallback;
    }

    boolean setOmniXButtonBindings(
            Map<OmniButtonGesture.Direction, OmniButtonAction> actions,
            Map<OmniButtonGesture.Direction, Double> amounts
    ) {
        if (!validOmniButtonBindings(actions, amounts)) {
            return false;
        }
        SharedPreferences.Editor editor = preferences.edit();
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            editor.putString(omniXActionKey(direction), actions.get(direction).id);
            double amount = amounts.get(direction) == null
                    ? Double.NaN : amounts.get(direction);
            editor.putLong(
                    omniXAmountKey(direction),
                    Double.doubleToRawLongBits(amount)
            );
        }
        editor.apply();
        return true;
    }


    static boolean validOmniButtonBindings(
            Map<OmniButtonGesture.Direction, OmniButtonAction> actions,
            Map<OmniButtonGesture.Direction, Double> amounts
    ) {
        if (actions == null || amounts == null) {
            return false;
        }
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            OmniButtonAction action = actions.get(direction);
            if (action == null) {
                return false;
            }
            if (action.usesAmount()) {
                Double amount = amounts.get(direction);
                if (amount == null || !action.acceptsAmount(amount)) {
                    return false;
                }
            }
        }
        return true;
    }

    static EnumMap<OmniButtonGesture.Direction, OmniButtonAction> defaultOmniButtonActions() {
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> actions =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            actions.put(direction, OmniButtonAction.defaultFor(direction));
        }
        return actions;
    }

    static EnumMap<OmniButtonGesture.Direction, Double> defaultOmniButtonAmounts() {
        EnumMap<OmniButtonGesture.Direction, Double> amounts =
                new EnumMap<>(OmniButtonGesture.Direction.class);
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            amounts.put(
                    direction,
                    OmniButtonAction.defaultAmount(OmniButtonAction.defaultFor(direction))
            );
        }
        return amounts;
    }

    boolean areSavedThumbnailsEnabled() {
        return preferences.getBoolean(SAVED_THUMBNAILS_ENABLED, true);
    }

    void setSavedThumbnailsEnabled(boolean enabled) {
        preferences.edit().putBoolean(SAVED_THUMBNAILS_ENABLED, enabled).apply();
    }

    boolean isScrapedLinksBackupEnabled() {
        return preferences.getBoolean(SCRAPED_LINKS_BACKUP_ENABLED, false);
    }
    void setScrapedLinksBackupEnabled(boolean enabled) {
        preferences.edit().putBoolean(SCRAPED_LINKS_BACKUP_ENABLED, enabled).apply();
    }

    boolean isAutoScrapeXLinksEnabled() {
        return preferences.getBoolean(AUTO_SCRAPE_X_LINKS, false);
    }

    void setAutoScrapeXLinksEnabled(boolean enabled) {
        preferences.edit().putBoolean(AUTO_SCRAPE_X_LINKS, enabled).apply();
    }

    boolean areSpeedControlsCollapsed() {
        return preferences.getBoolean(SPEED_CONTROLS_COLLAPSED, false);
    }

    void setSpeedControlsCollapsed(boolean collapsed) {
        preferences.edit().putBoolean(SPEED_CONTROLS_COLLAPSED, collapsed).apply();
    }

    float getLockPositionX() {
        return savedFraction(LOCK_POSITION_X);
    }

    float getLockPositionY() {
        return savedFraction(LOCK_POSITION_Y);
    }

    void setLockPosition(float x, float y) {
        savePosition(LOCK_POSITION_X, LOCK_POSITION_Y, x, y);
    }

    float getPictureInPicturePositionX() {
        return savedFraction(PIP_POSITION_X);
    }

    float getPictureInPicturePositionY() {
        return savedFraction(PIP_POSITION_Y);
    }

    void setPictureInPicturePosition(float x, float y) {
        savePosition(PIP_POSITION_X, PIP_POSITION_Y, x, y);
    }

    float getOmniButtonPositionX() {
        return savedFraction(OMNI_POSITION_X);
    }

    float getOmniButtonPositionY() {
        return savedFraction(OMNI_POSITION_Y);
    }

    void setOmniButtonPosition(float x, float y) {
        savePosition(OMNI_POSITION_X, OMNI_POSITION_Y, x, y);
    }

    static boolean isSavedPosition(float value) {
        return value == -1f || (Float.isFinite(value) && value >= 0f && value <= 1f);
    }

    private static String omniActionKey(OmniButtonGesture.Direction direction) {
        return OMNI_ACTION_PREFIX + direction.id;
    }

    private static String omniAmountKey(OmniButtonGesture.Direction direction) {
        return OMNI_AMOUNT_PREFIX + direction.id;
    }

    private static String omniWebActionKey(OmniButtonGesture.Direction direction) {
        return OMNI_WEB_ACTION_PREFIX + direction.id;
    }

    private static String omniWebAmountKey(OmniButtonGesture.Direction direction) {
        return OMNI_WEB_AMOUNT_PREFIX + direction.id;
    }

    private static String omniXActionKey(OmniButtonGesture.Direction direction) {
        return OMNI_X_ACTION_PREFIX + direction.id;
    }

    private static String omniXAmountKey(OmniButtonGesture.Direction direction) {
        return OMNI_X_AMOUNT_PREFIX + direction.id;
    }

    private static void putOmniButtonBindings(
            SharedPreferences.Editor editor,
            Map<OmniButtonGesture.Direction, OmniButtonAction> actions,
            Map<OmniButtonGesture.Direction, Double> amounts
    ) {
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            OmniButtonAction action = actions.get(direction);
            editor.putString(omniActionKey(direction), action.id);
            if (action.usesAmount()) {
                editor.putLong(
                        omniAmountKey(direction),
                        Double.doubleToRawLongBits(amounts.get(direction))
                );
            } else {
                editor.remove(omniAmountKey(direction));
            }
        }
    }


    private float savedFraction(String key) {
        float value = preferences.getFloat(key, -1f);
        return Float.isFinite(value) && value >= 0f && value <= 1f ? value : -1f;
    }

    private void savePosition(String xKey, String yKey, float x, float y) {
        float boundedX = Math.max(0f, Math.min(1f, x));
        float boundedY = Math.max(0f, Math.min(1f, y));
        preferences.edit()
                .putFloat(xKey, boundedX)
                .putFloat(yKey, boundedY)
                .apply();
    }


    synchronized String getApiKey() throws GeneralSecurityException {
        String encodedCiphertext = preferences.getString(API_KEY_CIPHERTEXT, "");
        String encodedIv = preferences.getString(API_KEY_IV, "");
        if (encodedCiphertext == null || encodedCiphertext.isEmpty()
                || encodedIv == null || encodedIv.isEmpty()) {
            return "";
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        byte[] iv = Base64.decode(encodedIv, Base64.NO_WRAP);
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), new GCMParameterSpec(128, iv));
        byte[] plaintext = cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP));
        return new String(plaintext, StandardCharsets.UTF_8);
    }

    synchronized void setApiKey(String apiKey) throws GeneralSecurityException {
        String normalized = apiKey == null ? "" : apiKey.trim();
        if (normalized.isEmpty()) {
            preferences.edit().remove(API_KEY_CIPHERTEXT).remove(API_KEY_IV).apply();
            return;
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
        byte[] ciphertext = cipher.doFinal(normalized.getBytes(StandardCharsets.UTF_8));
        preferences.edit()
                .putString(API_KEY_CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                .putString(API_KEY_IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                .apply();
    }

    String getModelId() {
        String model = preferences.getString(MODEL_ID, "");
        return model == null ? "" : model.trim();
    }

    void setModelId(String modelId) {
        preferences.edit().putString(MODEL_ID, modelId == null ? "" : modelId.trim()).apply();
    }

    String getSummaryOnePrompt() {
        String prompt = preferences.getString(SUMMARY_ONE, "");
        return prompt == null || prompt.trim().isEmpty()
                ? context.getString(R.string.summary_one_prompt_default)
                : prompt;
    }

    String getSummaryTwoPrompt() {
        String prompt = preferences.getString(SUMMARY_TWO, "");
        return prompt == null || prompt.trim().isEmpty()
                ? context.getString(R.string.summary_two_prompt_default)
                : prompt;
    }

    String getQuizPrompt() {
        String prompt = preferences.getString(QUIZ, "");
        return prompt == null || prompt.trim().isEmpty()
                ? context.getString(R.string.quiz_prompt_default)
                : prompt;
    }
    String getWatchPathPrompt() {
        String prompt = preferences.getString(WATCH_PATH, "");
        return prompt == null || prompt.trim().isEmpty()
                ? context.getString(R.string.watch_path_prompt_default)
                : prompt;
    }



    void setPrompts(String summaryOne, String summaryTwo, String quiz, String watchPath) {
        preferences.edit()
                .putString(SUMMARY_ONE, summaryOne == null ? "" : summaryOne)
                .putString(SUMMARY_TWO, summaryTwo == null ? "" : summaryTwo)
                .putString(QUIZ, quiz == null ? "" : quiz)
                .putString(WATCH_PATH, watchPath == null ? "" : watchPath)
                .apply();
    }

    boolean restoreBackup(
            String modelId,
            String summaryOne,
            String summaryTwo,
            String quiz,
            String watchPath,
            double defaultSpeed,
            boolean lockEnabled,
            String pictureInPictureControl,
            String playbackProfile,
            boolean adaptiveEnabled,
            double adaptiveBoost,
            String mp3Quality,
            boolean speedControlsCollapsed,
            float lockPositionX,
            float lockPositionY,
            float pictureInPicturePositionX,
            float pictureInPicturePositionY,
            boolean omniButtonEnabled,
            float omniButtonPositionX,
            float omniButtonPositionY,
            Map<OmniButtonGesture.Direction, OmniButtonAction> omniActions,
            Map<OmniButtonGesture.Direction, Double> omniAmounts,
            boolean savedThumbnailsEnabled
    ) {
        String normalizedModel = modelId == null ? "" : modelId.trim();
        if (normalizedModel.length() > 300
                || summaryOne == null || summaryOne.trim().isEmpty()
                || summaryTwo == null || summaryTwo.trim().isEmpty()
                || quiz == null || quiz.trim().isEmpty()
                || watchPath == null || watchPath.trim().isEmpty()
                || !Double.isFinite(defaultSpeed)
                || defaultSpeed < 0.25 || defaultSpeed > 4
                || !isPlaybackProfile(playbackProfile)
                || !Double.isFinite(adaptiveBoost)
                || adaptiveBoost < 0.1 || adaptiveBoost > 1.5
                || !isMp3Quality(mp3Quality)
                || !isPictureInPictureControl(pictureInPictureControl)
                || !isSavedPosition(lockPositionX)
                || !isSavedPosition(lockPositionY)
                || !isSavedPosition(pictureInPicturePositionX)
                || !isSavedPosition(pictureInPicturePositionY)
                || !isSavedPosition(omniButtonPositionX)
                || !isSavedPosition(omniButtonPositionY)
                || !validOmniButtonBindings(omniActions, omniAmounts)) {
            return false;
        }
        SharedPreferences.Editor editor = preferences.edit()
                .putString(MODEL_ID, normalizedModel)
                .putString(SUMMARY_ONE, summaryOne)
                .putString(SUMMARY_TWO, summaryTwo)
                .putString(QUIZ, quiz)
                .putString(WATCH_PATH, watchPath)
                .putLong(DEFAULT_PLAYBACK_SPEED, Double.doubleToRawLongBits(defaultSpeed))
                .putBoolean(LOCK_ICON_ENABLED, lockEnabled)
                .putString(PICTURE_IN_PICTURE_CONTROL, pictureInPictureControl)
                .putString(PLAYBACK_PROFILE, playbackProfile)
                .putBoolean(ADAPTIVE_SPEED_ENABLED, adaptiveEnabled)
                .putLong(ADAPTIVE_SPEED_BOOST, Double.doubleToRawLongBits(adaptiveBoost))
                .putString(DEFAULT_MP3_QUALITY, mp3Quality)
                .putBoolean(SPEED_CONTROLS_COLLAPSED, speedControlsCollapsed)
                .putBoolean(OMNI_BUTTON_ENABLED, omniButtonEnabled)
                .putBoolean(SAVED_THUMBNAILS_ENABLED, savedThumbnailsEnabled);
        restorePosition(editor, LOCK_POSITION_X, lockPositionX);
        restorePosition(editor, LOCK_POSITION_Y, lockPositionY);
        restorePosition(editor, PIP_POSITION_X, pictureInPicturePositionX);
        restorePosition(editor, PIP_POSITION_Y, pictureInPicturePositionY);
        restorePosition(editor, OMNI_POSITION_X, omniButtonPositionX);
        restorePosition(editor, OMNI_POSITION_Y, omniButtonPositionY);
        putOmniButtonBindings(editor, omniActions, omniAmounts);
        return editor.commit();
    }

    private static void restorePosition(
            SharedPreferences.Editor editor,
            String key,
            float value
    ) {
        if (value < 0f) {
            editor.remove(key);
        } else {
            editor.putFloat(key, value);
        }
    }

    private SecretKey getOrCreateSecretKey() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        try {
            keyStore.load(null);
        } catch (java.io.IOException error) {
            throw new GeneralSecurityException("Could not load Android Keystore", error);
        }
        KeyStore.Entry existing = keyStore.getEntry(KEY_ALIAS, null);
        if (existing instanceof KeyStore.SecretKeyEntry secretKeyEntry) {
            return secretKeyEntry.getSecretKey();
        }

        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
