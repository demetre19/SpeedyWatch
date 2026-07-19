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
    static final String SUMMARY_ONE_DEFAULT =
            "You are a concise video content summariser. Provide a clear, well-structured summary of the following YouTube video transcript. Include:\n"
                    + "- A brief overview of the video topic (2-3 sentences)\n"
                    + "- Key points as bullet points\n"
                    + "- Any notable conclusions or takeaways\n\n"
                    + "Keep the summary factual and focused. Do not add opinions or information not present in the transcript.";
    static final String SUMMARY_TWO_DEFAULT =
            "You are a video content summariser specialising in timestamped summaries. The transcript includes timestamps. Create a structured summary that:\n"
                    + "- Provides a brief overview of the video (2-3 sentences)\n"
                    + "- Lists key moments with their timestamps so viewers can jump to specific sections\n"
                    + "- Highlights any notable conclusions or takeaways\n\n"
                    + "Format timestamps as clickable moments (e.g. \"14:32 - Speaker introduces the framework\"). Keep the summary factual and focused.";
    static final String QUIZ_DEFAULT =
            "You are a study tutor preparing a reader before they study a source. "
                    + "Use only facts, terms, and concepts present in the source. "
                    + "Create a short pre-watch guide with learning questions and a concise description under each question. "
                    + "Choose the requested number of questions from the most important concepts in the source.";

    private static final String PREFERENCES = "speedywatch_settings";
    private static final String KEY_ALIAS = "speedywatch_openrouter_key";
    private static final String API_KEY_CIPHERTEXT = "openrouter_key_ciphertext";
    private static final String API_KEY_IV = "openrouter_key_iv";
    private static final String MODEL_ID = "openrouter_model_id";
    private static final String SUMMARY_ONE = "summary_one_prompt";
    private static final String SUMMARY_TWO = "summary_two_prompt";
    private static final String QUIZ = "quiz_prompt";

    private final SharedPreferences preferences;

    SpeedyWatchSettings(Context context) {
        preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
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
        String prompt = preferences.getString(SUMMARY_ONE, SUMMARY_ONE_DEFAULT);
        return prompt == null || prompt.trim().isEmpty() ? SUMMARY_ONE_DEFAULT : prompt;
    }

    String getSummaryTwoPrompt() {
        String prompt = preferences.getString(SUMMARY_TWO, SUMMARY_TWO_DEFAULT);
        return prompt == null || prompt.trim().isEmpty() ? SUMMARY_TWO_DEFAULT : prompt;
    }
    String getQuizPrompt() {
        String prompt = preferences.getString(QUIZ, QUIZ_DEFAULT);
        return prompt == null || prompt.trim().isEmpty() ? QUIZ_DEFAULT : prompt;
    }


    void setPrompts(String summaryOne, String summaryTwo, String quiz) {
        String one = summaryOne == null || summaryOne.trim().isEmpty()
                ? SUMMARY_ONE_DEFAULT : summaryOne.trim();
        String two = summaryTwo == null || summaryTwo.trim().isEmpty()
                ? SUMMARY_TWO_DEFAULT : summaryTwo.trim();
        String quizPrompt = quiz == null || quiz.trim().isEmpty()
                ? QUIZ_DEFAULT : quiz.trim();
        preferences.edit()
                .putString(SUMMARY_ONE, one)
                .putString(SUMMARY_TWO, two)
                .putString(QUIZ, quizPrompt)
                .apply();
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
