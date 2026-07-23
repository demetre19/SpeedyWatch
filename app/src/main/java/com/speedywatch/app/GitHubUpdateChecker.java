package com.speedywatch.app;

import android.app.DownloadManager;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GitHubUpdateChecker {
    static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/demetre19/SpeedyWatch/releases/latest";
    static final String ASSET_NAME = "SpeedyWatch.apk";
    static final long AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private static final long MAX_APK_BYTES = 128L * 1024L * 1024L;
    private static final int MAX_RELEASE_BYTES = 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final Pattern RELEASE_TAG = Pattern.compile("^v([0-9]+(?:\\.[0-9]+){1,3})$");
    private static final Pattern SHA256 = Pattern.compile("^sha256:[0-9a-f]{64}$");
    private static final String APK_MIME = "application/vnd.android.package-archive";

    private GitHubUpdateChecker() {
    }

    static final class Release {
        final String tag;
        final String versionName;
        final String changelog;
        final String releaseUrl;
        final String downloadUrl;
        final long assetSize;
        final String sha256;

        Release(
                String tag,
                String versionName,
                String changelog,
                String releaseUrl,
                String downloadUrl,
                long assetSize,
                String sha256
        ) {
            this.tag = tag;
            this.versionName = versionName;
            this.changelog = changelog;
            this.releaseUrl = releaseUrl;
            this.downloadUrl = downloadUrl;
            this.assetSize = assetSize;
            this.sha256 = sha256;
        }

        int compareToInstalled(String installedVersion) throws UpdateException {
            return compareVersions(versionName, installedVersion);
        }
    }

    static final class UpdateException extends Exception {
        UpdateException(String message) {
            super(message);
        }

        UpdateException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    static Release fetchLatest() throws IOException, UpdateException {
        HttpURLConnection connection = (HttpURLConnection) new URL(LATEST_RELEASE_URL).openConnection();
        connection.setConnectTimeout(CONNECT_TIMEOUT_MS);
        connection.setReadTimeout(READ_TIMEOUT_MS);
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("Accept", "application/vnd.github+json");
        connection.setRequestProperty("User-Agent", "SpeedyWatch-Android-Updater");
        connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28");
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                throw new UpdateException("GitHub returned HTTP " + connection.getResponseCode());
            }
            return parseRelease(new String(
                    readBounded(connection.getInputStream(), MAX_RELEASE_BYTES),
                    StandardCharsets.UTF_8
            ));
        } finally {
            connection.disconnect();
        }
    }

    static Release parseRelease(String json) throws UpdateException {
        try {
            JSONObject root = new JSONObject(json);
            if (root.optBoolean("draft", false) || root.optBoolean("prerelease", false)) {
                throw new UpdateException("Latest release is not a stable public release");
            }

            String tag = requiredString(root, "tag_name");
            Matcher tagMatch = RELEASE_TAG.matcher(tag);
            if (!tagMatch.matches()) {
                throw new UpdateException("Invalid release tag");
            }
            String versionName = tagMatch.group(1);
            parseVersion(versionName);

            String releaseUrl = requiredString(root, "html_url");
            validateOfficialReleaseUrl(releaseUrl, tag);
            String changelog = root.optString("body", "");
            if (changelog.length() > 200_000) {
                throw new UpdateException("Release notes are too large");
            }

            JSONArray assets = root.getJSONArray("assets");
            JSONObject selected = null;
            for (int index = 0; index < assets.length(); index++) {
                JSONObject asset = assets.getJSONObject(index);
                if (!ASSET_NAME.equals(asset.optString("name", ""))) {
                    continue;
                }
                if (selected != null) {
                    throw new UpdateException("Duplicate update asset");
                }
                selected = asset;
            }
            if (selected == null) {
                throw new UpdateException("Release does not contain " + ASSET_NAME);
            }
            if (!APK_MIME.equals(selected.optString("content_type", ""))) {
                throw new UpdateException("Release asset is not an Android APK");
            }

            long size = selected.getLong("size");
            if (size <= 0 || size > MAX_APK_BYTES) {
                throw new UpdateException("Invalid update size");
            }
            String downloadUrl = requiredString(selected, "browser_download_url");
            validateOfficialDownloadUrl(downloadUrl, tag);
            String digest = requiredString(selected, "digest");
            if (!SHA256.matcher(digest).matches()) {
                throw new UpdateException("Invalid update digest");
            }
            return new Release(
                    tag,
                    versionName,
                    changelog,
                    releaseUrl,
                    downloadUrl,
                    size,
                    digest.substring("sha256:".length())
            );
        } catch (JSONException error) {
            throw new UpdateException("Invalid GitHub release response", error);
        }
    }

    static long enqueueDownload(Context context, Release release) throws UpdateException {
        DownloadManager manager = context.getSystemService(DownloadManager.class);
        if (manager == null) {
            throw new UpdateException("Android Download Manager is unavailable");
        }
        DownloadManager.Request request = new DownloadManager.Request(Uri.parse(release.downloadUrl))
                .setTitle("SpeedyWatch " + release.versionName)
                .setDescription("Official SpeedyWatch update from GitHub")
                .setMimeType(APK_MIME)
                .setNotificationVisibility(
                        DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                )
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(false)
                .setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        "SpeedyWatch-v" + release.versionName + ".apk"
                );
        try {
            return manager.enqueue(request);
        } catch (RuntimeException error) {
            throw new UpdateException("Could not start the Android download", error);
        }
    }

    static int compareVersions(String left, String right) throws UpdateException {
        int[] leftParts = parseVersion(left);
        int[] rightParts = parseVersion(right);
        int length = Math.max(leftParts.length, rightParts.length);
        for (int index = 0; index < length; index++) {
            int leftValue = index < leftParts.length ? leftParts[index] : 0;
            int rightValue = index < rightParts.length ? rightParts[index] : 0;
            if (leftValue != rightValue) {
                return Integer.compare(leftValue, rightValue);
            }
        }
        return 0;
    }

    private static int[] parseVersion(String value) throws UpdateException {
        if (value == null || !value.matches("[0-9]+(?:\\.[0-9]+){1,3}")) {
            throw new UpdateException("Invalid version number");
        }
        String[] values = value.split("\\.");
        int[] parts = new int[values.length];
        for (int index = 0; index < values.length; index++) {
            try {
                parts[index] = Integer.parseInt(values[index]);
            } catch (NumberFormatException error) {
                throw new UpdateException("Invalid version number", error);
            }
            if (parts[index] > 999_999) {
                throw new UpdateException("Invalid version number");
            }
        }
        return parts;
    }

    private static String requiredString(JSONObject object, String key)
            throws JSONException, UpdateException {
        String value = object.getString(key);
        if (value.isEmpty() || value.length() > 4096) {
            throw new UpdateException("Invalid " + key);
        }
        return value;
    }

    private static void validateOfficialReleaseUrl(String value, String tag)
            throws UpdateException {
        validateOfficialUrl(
                value,
                "/demetre19/SpeedyWatch/releases/tag/" + tag,
                "Invalid release page"
        );
    }

    private static void validateOfficialDownloadUrl(String value, String tag)
            throws UpdateException {
        validateOfficialUrl(
                value,
                "/demetre19/SpeedyWatch/releases/download/" + tag + "/" + ASSET_NAME,
                "Invalid update download"
        );
    }

    private static void validateOfficialUrl(String value, String path, String message)
            throws UpdateException {
        try {
            URI uri = new URI(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || !"github.com".equalsIgnoreCase(uri.getHost())
                    || !path.equals(uri.getRawPath())
                    || uri.getRawUserInfo() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443)
                    || uri.getRawQuery() != null
                    || uri.getRawFragment() != null) {
                throw new UpdateException(message);
            }
        } catch (URISyntaxException error) {
            throw new UpdateException(message, error);
        }
    }

    private static byte[] readBounded(InputStream input, int maximum)
            throws IOException, UpdateException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > maximum) {
                throw new UpdateException("GitHub release response is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }
}
