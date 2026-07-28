package com.speedywatch.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class GitHubUpdateChecker {
    static final String LATEST_RELEASE_URL =
            "https://api.github.com/repos/demetre19/SpeedyWatch/releases/latest";
    static final String ASSET_NAME = "SpeedyWatch.apk";
    static final long AUTO_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L;
    static final String UPDATE_PREFERENCES = "speedywatch_updates";
    static final String UPDATE_LAST_CHECK = "last_check_ms";
    static final String UPDATE_LAST_STATUS = "last_status";

    private static final long MAX_APK_BYTES = 256L * 1024L * 1024L;
    private static final int MAX_RELEASE_BYTES = 1024 * 1024;
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 20_000;
    private static final Pattern RELEASE_TAG = Pattern.compile("^v([0-9]+(?:\\.[0-9]+){1,3})$");
    private static final Pattern SHA256 = Pattern.compile("^sha256:[0-9a-f]{64}$");
    private static final String APK_MIME = "application/vnd.android.package-archive";
    private static final Pattern SHA256_VALUE = Pattern.compile("^[0-9a-f]{64}$");
    private static final String DOWNLOAD_PREFERENCES = "speedywatch_update_download";
    private static final String DOWNLOAD_ID = "download_id";
    private static final String DOWNLOAD_SIZE = "download_size";
    private static final String DOWNLOAD_SHA256 = "download_sha256";
    private static final String DOWNLOAD_VERSION = "download_version";
    private static final String READY_URI = "ready_uri";
    private static final String READY_VERSION = "ready_version";
    private static final String UPDATE_CHANNEL_ID = "speedywatch_updates";
    private static final int UPDATE_NOTIFICATION_ID = 4_208;
    private static volatile WeakReference<Activity> resumedActivity =
            new WeakReference<>(null);

    private GitHubUpdateChecker() {
    }
    static void registerResumedActivity(Activity activity) {
        resumedActivity = new WeakReference<>(activity);
    }

    static void unregisterResumedActivity(Activity activity) {
        WeakReference<Activity> current = resumedActivity;
        if (current.get() == activity) {
            resumedActivity = new WeakReference<>(null);
        }
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
            validateAssetSize(size);
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
        long downloadId;
        try {
            downloadId = manager.enqueue(request);
        } catch (RuntimeException error) {
            throw new UpdateException("Could not start the Android download", error);
        }

        boolean saved = context.getSharedPreferences(DOWNLOAD_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putLong(DOWNLOAD_ID, downloadId)
                .putLong(DOWNLOAD_SIZE, release.assetSize)
                .putString(DOWNLOAD_SHA256, release.sha256)
                .putString(DOWNLOAD_VERSION, release.versionName)
                .commit();
        if (!saved) {
            manager.remove(downloadId);
            throw new UpdateException("Could not track the update download");
        }
        return downloadId;
    }

    static void handleDownloadComplete(Context context, long downloadId) {
        SharedPreferences downloadPreferences = context.getSharedPreferences(
                DOWNLOAD_PREFERENCES,
                Context.MODE_PRIVATE
        );
        if (downloadPreferences.getLong(DOWNLOAD_ID, -1L) != downloadId) {
            return;
        }

        long expectedSize = downloadPreferences.getLong(DOWNLOAD_SIZE, -1L);
        String expectedSha256 = downloadPreferences.getString(DOWNLOAD_SHA256, "");
        String versionName = downloadPreferences.getString(DOWNLOAD_VERSION, "");
        DownloadManager manager = context.getSystemService(DownloadManager.class);
        if (manager == null
                || expectedSize <= 0
                || !SHA256_VALUE.matcher(expectedSha256).matches()
                || versionName.isEmpty()) {
            clearPendingDownload(downloadPreferences);
            saveUpdateStatus(context, "Downloaded update could not be verified");
            return;
        }

        try {
            if (!isSuccessfulDownload(manager, downloadId, expectedSize)) {
                clearPendingDownload(downloadPreferences);
                saveUpdateStatus(context, "Update download did not complete");
                return;
            }
            Uri apkUri = manager.getUriForDownloadedFile(downloadId);
            if (apkUri == null || !"content".equalsIgnoreCase(apkUri.getScheme())) {
                throw new IOException("Download Manager did not provide a secure content URI");
            }
            try (InputStream input = context.getContentResolver().openInputStream(apkUri)) {
                if (input == null || !verifyDownloadedApk(input, expectedSize, expectedSha256)) {
                    throw new IOException("Downloaded APK does not match the GitHub release");
                }
            }

            boolean saved = downloadPreferences.edit()
                    .remove(DOWNLOAD_ID)
                    .remove(DOWNLOAD_SIZE)
                    .remove(DOWNLOAD_SHA256)
                    .remove(DOWNLOAD_VERSION)
                    .putString(READY_URI, apkUri.toString())
                    .putString(READY_VERSION, versionName)
                    .commit();
            if (!saved) {
                throw new IOException("Could not persist the verified update");
            }
            saveUpdateStatus(
                    context,
                    "SpeedyWatch v" + versionName + " verified and ready to install"
            );
            openInstallerOrNotify(context, apkUri, versionName);
        } catch (IOException | RuntimeException error) {
            clearPendingDownload(downloadPreferences);
            saveUpdateStatus(context, "Downloaded update failed verification");
            showUpdateNotification(
                    context,
                    "SpeedyWatch update failed verification",
                    "Download the update again from Settings",
                    null
            );
        }
    }

    static boolean verifyDownloadedApk(
            InputStream input,
            long expectedSize,
            String expectedSha256
    ) throws IOException {
        if (expectedSize <= 0 || !SHA256_VALUE.matcher(expectedSha256).matches()) {
            return false;
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
        byte[] buffer = new byte[8192];
        long total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > expectedSize) {
                return false;
            }
            digest.update(buffer, 0, read);
        }
        return total == expectedSize
                && MessageDigest.isEqual(digest.digest(), decodeHex(expectedSha256));
    }
    static void resumePendingInstaller(Context context) {
        SharedPreferences preferences = context.getSharedPreferences(
                DOWNLOAD_PREFERENCES,
                Context.MODE_PRIVATE
        );
        String uriValue = preferences.getString(READY_URI, "");
        String versionName = preferences.getString(READY_VERSION, "");
        if (uriValue.isEmpty() || versionName.isEmpty()) {
            return;
        }
        Uri apkUri = Uri.parse(uriValue);
        if (!"content".equalsIgnoreCase(apkUri.getScheme())) {
            clearReadyDownload(preferences);
            return;
        }
        if (!context.getPackageManager().canRequestPackageInstalls()) {
            showInstallPermissionPrompt(context, versionName);
            return;
        }
        try {
            context.startActivity(createInstallIntent(apkUri));
            clearReadyDownload(preferences);
        } catch (RuntimeException error) {
            showUpdateNotification(
                    context,
                    "SpeedyWatch v" + versionName + " is ready",
                    "Tap to open Android's installer",
                    createInstallIntent(apkUri)
            );
        }
    }

    private static void showInstallPermissionPrompt(Context context, String versionName) {
        if (!(context instanceof Activity) || ((Activity) context).isFinishing()) {
            showUpdateNotification(
                    context,
                    "Allow SpeedyWatch to install updates",
                    "Enable this source, then return to SpeedyWatch",
                    createInstallPermissionIntent(context)
            );
            return;
        }
        new AlertDialog.Builder(context)
                .setTitle("SpeedyWatch v" + versionName + " is ready")
                .setMessage(
                        "Allow SpeedyWatch as an install source, then return to open "
                                + "Android's installer."
                )
                .setNegativeButton("Later", null)
                .setPositiveButton("Open settings", (dialog, which) -> {
                    try {
                        context.startActivity(createInstallPermissionIntent(context));
                    } catch (RuntimeException error) {
                        Toast.makeText(
                                context,
                                "Could not open install-source settings",
                                Toast.LENGTH_LONG
                        ).show();
                    }
                })
                .show();
    }


    private static boolean isSuccessfulDownload(
            DownloadManager manager,
            long downloadId,
            long expectedSize
    ) {
        DownloadManager.Query query = new DownloadManager.Query().setFilterById(downloadId);
        try (Cursor cursor = manager.query(query)) {
            if (cursor == null || !cursor.moveToFirst()) {
                return false;
            }
            int status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
            long size = cursor.getLong(
                    cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            );
            return status == DownloadManager.STATUS_SUCCESSFUL && size == expectedSize;
        }
    }

    private static void openInstallerOrNotify(
            Context context,
            Uri apkUri,
            String versionName
    ) {
        Intent installIntent = createInstallIntent(apkUri);
        Activity activity = getResumedActivity();
        if (activity != null) {
            activity.runOnUiThread(() -> {
                if (getResumedActivity() != activity) {
                    showInstallerNotification(context, installIntent, versionName);
                    return;
                }
                if (!activity.getPackageManager().canRequestPackageInstalls()) {
                    try {
                        activity.startActivity(createInstallPermissionIntent(activity));
                        return;
                    } catch (RuntimeException ignored) {
                        showInstallerNotification(context, installIntent, versionName);
                        return;
                    }
                }
                try {
                    activity.startActivity(installIntent);
                    clearReadyDownload(context.getSharedPreferences(
                            DOWNLOAD_PREFERENCES,
                            Context.MODE_PRIVATE
                    ));
                } catch (RuntimeException ignored) {
                    showInstallerNotification(context, installIntent, versionName);
                }
            });
            return;
        }
        showInstallerNotification(context, installIntent, versionName);
    }

    private static void showInstallerNotification(
            Context context,
            Intent installIntent,
            String versionName
    ) {
        boolean canInstall = context.getPackageManager().canRequestPackageInstalls();
        showUpdateNotification(
                context,
                canInstall
                        ? "SpeedyWatch v" + versionName + " is ready"
                        : "Allow SpeedyWatch to install updates",
                canInstall
                        ? "Tap to open Android's installer"
                        : "Enable this source, then return to SpeedyWatch",
                canInstall ? installIntent : createInstallPermissionIntent(context)
        );
    }

    private static Intent createInstallIntent(Uri apkUri) {
        return new Intent(Intent.ACTION_VIEW)
                .setDataAndType(apkUri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static Intent createInstallPermissionIntent(Context context) {
        return new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:" + context.getPackageName()))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    }

    private static Activity getResumedActivity() {
        Activity activity = resumedActivity.get();
        if (activity == null || activity.isFinishing() || activity.isDestroyed()) {
            return null;
        }
        return activity;
    }


    private static void showUpdateNotification(
            Context context,
            String title,
            String message,
            Intent intent
    ) {
        NotificationManager manager = context.getSystemService(NotificationManager.class);
        if (manager == null) {
            return;
        }
        manager.createNotificationChannel(new NotificationChannel(
                UPDATE_CHANNEL_ID,
                "App updates",
                NotificationManager.IMPORTANCE_HIGH
        ));
        Notification.Builder builder = new Notification.Builder(context, UPDATE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true);
        if (intent != null) {
            PendingIntent pendingIntent = PendingIntent.getActivity(
                    context,
                    UPDATE_NOTIFICATION_ID,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            builder.setContentIntent(pendingIntent);
        }
        manager.notify(UPDATE_NOTIFICATION_ID, builder.build());
    }

    private static void clearPendingDownload(SharedPreferences preferences) {
        preferences.edit()
                .remove(DOWNLOAD_ID)
                .remove(DOWNLOAD_SIZE)
                .remove(DOWNLOAD_SHA256)
                .remove(DOWNLOAD_VERSION)
                .apply();
    }

    private static void clearReadyDownload(SharedPreferences preferences) {
        preferences.edit()
                .remove(READY_URI)
                .remove(READY_VERSION)
                .apply();
    }

    private static void saveUpdateStatus(Context context, String message) {
        context.getSharedPreferences(UPDATE_PREFERENCES, Context.MODE_PRIVATE)
                .edit()
                .putLong(UPDATE_LAST_CHECK, System.currentTimeMillis())
                .putString(UPDATE_LAST_STATUS, message)
                .apply();
    }

    private static byte[] decodeHex(String value) {
        byte[] decoded = new byte[value.length() / 2];
        for (int index = 0; index < value.length(); index += 2) {
            decoded[index / 2] = (byte) Integer.parseInt(value.substring(index, index + 2), 16);
        }
        return decoded;
    }

    static void validateAssetSize(long size) throws UpdateException {
        if (size <= 0 || size > MAX_APK_BYTES) {
            throw new UpdateException("Invalid update size");
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
