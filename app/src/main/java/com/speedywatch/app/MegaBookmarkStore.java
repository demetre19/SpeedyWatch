package com.speedywatch.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

final class MegaBookmarkStore {
    private static final String PREFERENCES = "speedywatch_mega_bookmarks";
    private static final String KEY_ALIAS = "speedywatch_mega_bookmarks_key";
    private static final String CIPHERTEXT = "bookmarks_ciphertext";
    private static final String IV = "bookmarks_iv";
    private static final int STORAGE_VERSION = 4;
    private static final int MAX_BOOKMARKS = 100;
    private static final int MAX_NAME_LENGTH = 120;
    private static final double MAX_POSITION_SECONDS = 604_800;

    static final class Entry {
        final String name;
        final String folderPath;
        final String completeUrl;
        final String selection;
        final double positionSeconds;
        final long updatedAt;

        Entry(
                String name,
                String folderPath,
                String completeUrl,
                String selection,
                double positionSeconds,
                long updatedAt
        ) {
            this.name = name;
            this.folderPath = folderPath;
            this.completeUrl = completeUrl;
            this.selection = selection;
            this.positionSeconds = positionSeconds;
            this.updatedAt = updatedAt;
        }
    }

    private final SharedPreferences preferences;

    MegaBookmarkStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(
                PREFERENCES,
                Context.MODE_PRIVATE
        );
    }

    synchronized List<Entry> list() throws GeneralSecurityException {
        List<Entry> entries = readEntries();
        entries.sort(Comparator.comparingLong((Entry entry) -> entry.updatedAt).reversed());
        return entries;
    }

    synchronized Entry save(
            String name,
            String url,
            double positionSeconds
    ) throws GeneralSecurityException {
        String validUrl = validMegaUrl(url);
        String folderPath = accesslessFolderIdentity(validUrl);
        if (validUrl == null || folderPath == null) {
            throw new GeneralSecurityException("Invalid MEGA bookmark URL");
        }
        Entry saved = new Entry(
                normalizeName(name),
                folderPath,
                SupportedSite.megaBookmarkIdentity(validUrl),
                selectionSuffix(validUrl),
                boundedPosition(positionSeconds),
                System.currentTimeMillis()
        );
        List<Entry> entries = readEntries();
        entries.removeIf(entry -> folderPath.equals(entry.folderPath));
        entries.add(0, saved);
        if (entries.size() > MAX_BOOKMARKS) {
            entries = new ArrayList<>(entries.subList(0, MAX_BOOKMARKS));
        }
        writeEntries(entries);
        return saved;
    }

    synchronized boolean updateResumePosition(
            String url,
            double positionSeconds
    ) throws GeneralSecurityException {
        String validUrl = validMegaUrl(url);
        String folderPath = accesslessFolderIdentity(validUrl);
        if (validUrl == null || folderPath == null
                || !Double.isFinite(positionSeconds) || positionSeconds < 0.25) {
            return false;
        }
        List<Entry> entries = readEntries();
        boolean updated = false;
        for (int index = 0; index < entries.size(); index++) {
            Entry entry = entries.get(index);
            if (folderPath.equals(entry.folderPath)) {
                entries.set(index, new Entry(
                        entry.name,
                        entry.folderPath,
                        SupportedSite.megaBookmarkIdentity(validUrl),
                        selectionSuffix(validUrl),
                        boundedPosition(positionSeconds),
                        System.currentTimeMillis()
                ));
                updated = true;
                break;
            }
        }
        if (updated) {
            writeEntries(entries);
        }
        return updated;
    }

    synchronized void delete(String folderPath) throws GeneralSecurityException {
        if (folderPath == null || folderPath.isEmpty()) {
            return;
        }
        List<Entry> entries = readEntries();
        if (entries.removeIf(entry -> folderPath.equals(entry.folderPath))) {
            writeEntries(entries);
        }
    }

    static String resolveResumeUrl(Entry entry, String candidateUrl) {
        if (entry == null) {
            return null;
        }
        String completeUrl = completeUrlForFolder(entry.completeUrl, entry.folderPath);
        if (completeUrl == null) {
            completeUrl = completeUrlForFolder(candidateUrl, entry.folderPath);
        }
        return completeUrl == null ? null : validMegaUrl(completeUrl + entry.selection);
    }

    synchronized boolean rememberCompleteUrl(
            Entry entry,
            String candidateUrl
    ) throws GeneralSecurityException {
        if (entry == null) {
            return false;
        }
        String completeUrl = completeUrlForFolder(candidateUrl, entry.folderPath);
        if (completeUrl == null) {
            return false;
        }
        List<Entry> entries = readEntries();
        for (int index = 0; index < entries.size(); index++) {
            Entry saved = entries.get(index);
            if (!entry.folderPath.equals(saved.folderPath)) {
                continue;
            }
            if (!completeUrl.equals(saved.completeUrl)) {
                entries.set(index, new Entry(
                        saved.name,
                        saved.folderPath,
                        completeUrl,
                        saved.selection,
                        saved.positionSeconds,
                        saved.updatedAt
                ));
                writeEntries(entries);
            }
            return true;
        }
        return false;
    }

    static String suggestedName(String pageTitle) {
        String title = pageTitle == null ? "" : pageTitle.trim();
        title = title.replaceFirst("(?i)\\s+(?:[-|·])\\s+MEGA$", "");
        if (title.equalsIgnoreCase("MEGA")) {
            title = "";
        }
        return normalizeName(title);
    }

    static String normalizeName(String value) {
        String normalized = value == null ? "" : value
                .replaceAll("[\\p{Cntrl}\\s]+", " ")
                .trim();
        if (normalized.isEmpty()) {
            normalized = "MEGA folder";
        }
        if (normalized.length() > MAX_NAME_LENGTH) {
            normalized = normalized.substring(0, MAX_NAME_LENGTH).trim();
        }
        return normalized;
    }

    static String resumeLabel(double positionSeconds) {
        long totalSeconds = Math.max(0, Math.round(boundedPosition(positionSeconds)));
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "Continue at %d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(Locale.US, "Continue at %d:%02d", minutes, seconds);
    }

    static String accesslessFolderIdentity(String value) {
        String valid = validMegaUrl(value);
        if (valid == null) {
            return null;
        }
        try {
            java.net.URI uri = java.net.URI.create(valid);
            return new java.net.URI(
                    "https",
                    null,
                    uri.getHost(),
                    uri.getPort(),
                    uri.getRawPath(),
                    null,
                    null
            ).toASCIIString();
        } catch (Exception ignored) {
            return null;
        }
    }

    static String selectionSuffix(String value) {
        String valid = validMegaUrl(value);
        if (valid == null) {
            return "";
        }
        String fragment = java.net.URI.create(valid).getRawFragment();
        int selectionStart = fragment == null ? -1 : fragment.indexOf('/');
        return selectionStart < 0 ? "" : fragment.substring(selectionStart);
    }

    private List<Entry> readEntries() throws GeneralSecurityException {
        String encodedCiphertext = preferences.getString(CIPHERTEXT, "");
        String encodedIv = preferences.getString(IV, "");
        if ((encodedCiphertext == null || encodedCiphertext.isEmpty())
                && (encodedIv == null || encodedIv.isEmpty())) {
            return new ArrayList<>();
        }
        if (encodedCiphertext == null || encodedCiphertext.isEmpty()
                || encodedIv == null || encodedIv.isEmpty()) {
            throw new GeneralSecurityException("Incomplete MEGA bookmark storage");
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            byte[] iv = Base64.decode(encodedIv, Base64.NO_WRAP);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateSecretKey(), new GCMParameterSpec(128, iv));
            byte[] plaintext = cipher.doFinal(Base64.decode(encodedCiphertext, Base64.NO_WRAP));
            JSONObject root = new JSONObject(new String(plaintext, StandardCharsets.UTF_8));
            int storageVersion = root.optInt("version", 0);
            if (storageVersion != STORAGE_VERSION && storageVersion != 3) {
                clearStoredBookmarks();
                return new ArrayList<>();
            }
            JSONArray values = root.optJSONArray("entries");
            List<Entry> entries = new ArrayList<>();
            if (values == null) {
                return entries;
            }
            for (int index = 0; index < values.length() && entries.size() < MAX_BOOKMARKS; index++) {
                JSONObject value = values.optJSONObject(index);
                if (value == null) {
                    continue;
                }
                String folderPath = value.optString("folderPath", "");
                String selection = value.optString("selection", "");
                if (!isSafeFolderPath(folderPath) || !isSafeSelection(selection)) {
                    continue;
                }
                entries.add(new Entry(
                        normalizeName(value.optString("name", "")),
                        folderPath,
                        storageVersion >= STORAGE_VERSION
                                ? completeUrlForFolder(value.optString("completeUrl", ""), folderPath)
                                : null,
                        selection,
                        boundedPosition(value.optDouble("positionSeconds", 0)),
                        Math.max(0, value.optLong("updatedAt", 0))
                ));
            }
            return entries;
        } catch (GeneralSecurityException error) {
            throw error;
        } catch (Exception error) {
            clearStoredBookmarks();
            return new ArrayList<>();
        }
    }

    private void writeEntries(List<Entry> entries) throws GeneralSecurityException {
        try {
            JSONArray values = new JSONArray();
            for (Entry entry : entries) {
                values.put(new JSONObject()
                        .put("name", entry.name)
                        .put("folderPath", entry.folderPath)
                        .put("completeUrl", entry.completeUrl == null ? "" : entry.completeUrl)
                        .put("selection", entry.selection)
                        .put("positionSeconds", entry.positionSeconds)
                        .put("updatedAt", entry.updatedAt));
            }
            JSONObject root = new JSONObject()
                    .put("version", STORAGE_VERSION)
                    .put("entries", values);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey());
            byte[] ciphertext = cipher.doFinal(root.toString().getBytes(StandardCharsets.UTF_8));
            preferences.edit()
                    .putString(CIPHERTEXT, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
                    .putString(IV, Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP))
                    .apply();
        } catch (JSONException error) {
            throw new GeneralSecurityException("Could not encode MEGA bookmarks", error);
        }
    }

    private SecretKey getOrCreateSecretKey() throws GeneralSecurityException {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        try {
            keyStore.load(null);
        } catch (Exception error) {
            throw new GeneralSecurityException("Could not load Android Keystore", error);
        }
        java.security.Key key = keyStore.getKey(KEY_ALIAS, null);
        if (key instanceof SecretKey secretKey) {
            return secretKey;
        }
        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore"
        );
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT
        ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return generator.generateKey();
    }


    private static String completeUrlForFolder(String value, String folderPath) {
        String valid = validMegaUrl(value);
        String completeUrl = valid == null ? null : SupportedSite.megaBookmarkIdentity(valid);
        return completeUrl != null && folderPath.equals(accesslessFolderIdentity(completeUrl))
                ? completeUrl : null;
    }

    private static boolean isSafeFolderPath(String value) {
        if (value == null || value.length() > 96 || value.indexOf('#') >= 0) {
            return false;
        }
        Uri uri = Uri.parse(value);
        return "https".equals(uri.getScheme())
                && "mega.nz".equals(uri.getHost())
                && uri.getQuery() == null
                && uri.getFragment() == null
                && (uri.getPath().matches("/folder/[A-Za-z0-9_-]{8}")
                || uri.getPath().matches("/file/[A-Za-z0-9_-]{8}"));
    }


    private static boolean isSafeSelection(String value) {
        return value != null && (value.isEmpty()
                || value.matches("/(?:folder|file)/[A-Za-z0-9_-]{8}"));
    }

    private void clearStoredBookmarks() {
        preferences.edit().clear().apply();
    }

    private static String validMegaUrl(String value) {
        String valid = SupportedSite.supportedUrlFromText(value);
        return valid != null && SupportedSite.forUrl(valid) == SupportedSite.MEGA
                ? valid : null;
    }

    private static double boundedPosition(double seconds) {
        if (!Double.isFinite(seconds)) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_POSITION_SECONDS, seconds));
    }
}
