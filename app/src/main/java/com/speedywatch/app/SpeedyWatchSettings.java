package com.speedywatch.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;

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
            String playbackProfile,
            boolean adaptiveEnabled,
            double adaptiveBoost,
            String mp3Quality
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
                || !isMp3Quality(mp3Quality)) {
            return false;
        }
        return preferences.edit()
                .putString(MODEL_ID, normalizedModel)
                .putString(SUMMARY_ONE, summaryOne)
                .putString(SUMMARY_TWO, summaryTwo)
                .putString(QUIZ, quiz)
                .putString(WATCH_PATH, watchPath)
                .putLong(DEFAULT_PLAYBACK_SPEED, Double.doubleToRawLongBits(defaultSpeed))
                .putBoolean(LOCK_ICON_ENABLED, lockEnabled)
                .putString(PLAYBACK_PROFILE, playbackProfile)
                .putBoolean(ADAPTIVE_SPEED_ENABLED, adaptiveEnabled)
                .putLong(ADAPTIVE_SPEED_BOOST, Double.doubleToRawLongBits(adaptiveBoost))
                .putString(DEFAULT_MP3_QUALITY, mp3Quality)
                .commit();
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
