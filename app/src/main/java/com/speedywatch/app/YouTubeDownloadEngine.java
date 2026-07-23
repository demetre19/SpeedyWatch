package com.speedywatch.app;

import android.content.Context;
import android.net.Uri;

import com.yausername.ffmpeg.FFmpeg;
import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;
import com.yausername.youtubedl_android.YoutubeDLResponse;
import com.yausername.youtubedl_android.YoutubeDLException;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;

final class YouTubeDownloadEngine {
    private static final Object INIT_LOCK = new Object();
    private static final String BUNDLED_YTDLP_VERSION = "2026.07.04";
    private static final String DOWNLOAD_PREFS = "download_engine";
    private static final String YTDLP_VERSION_KEY = "bundled_ytdlp_version";
    private static volatile boolean initialized;

    private YouTubeDownloadEngine() {
    }

    static void initialize(Context context) throws YoutubeDLException {
        if (initialized) {
            return;
        }
        synchronized (INIT_LOCK) {
            if (initialized) {
                return;
            }
            Context appContext = context.getApplicationContext();
            installBundledYtDlp(appContext);
            YoutubeDL.getInstance().init(appContext);
            FFmpeg.getInstance().init(appContext);
            initialized = true;
        }
    }
    private static void installBundledYtDlp(Context context) throws YoutubeDLException {
        File target = new File(
                new File(
                        new File(context.getNoBackupFilesDir(), YoutubeDL.baseName),
                        YoutubeDL.ytdlpDirName
                ),
                YoutubeDL.ytdlpBin
        );
        String installedVersion = context
                .getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE)
                .getString(YTDLP_VERSION_KEY, "");
        if (BUNDLED_YTDLP_VERSION.equals(installedVersion)
                && target.isFile()
                && target.length() > 0) {
            return;
        }

        File parent = target.getParentFile();
        if (parent == null || (!parent.mkdirs() && !parent.isDirectory())) {
            throw new YoutubeDLException("Could not prepare video downloads");
        }
        File temporary = new File(parent, YoutubeDL.ytdlpBin + ".tmp");
        try {
            try (InputStream input = context.getResources().openRawResource(R.raw.ytdlp);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[64 * 1024];
                int read;
                while ((read = input.read(buffer)) != -1) {
                    output.write(buffer, 0, read);
                }
                output.getFD().sync();
            }
            try {
                Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING
                );
            } catch (AtomicMoveNotSupportedException ignored) {
                Files.move(
                        temporary.toPath(),
                        target.toPath(),
                        StandardCopyOption.REPLACE_EXISTING
                );
            }
            boolean saved = context
                    .getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(YTDLP_VERSION_KEY, BUNDLED_YTDLP_VERSION)
                    .commit();
            if (!saved) {
                throw new IOException("Could not record downloader version");
            }
        } catch (IOException error) {
            throw new YoutubeDLException("Could not prepare video downloads", error);
        } finally {
            if (temporary.exists()) {
                temporary.delete();
            }
        }
    }

    static Metadata loadMetadata(Context context, String url) throws Exception {
        if (!isSupportedYouTubeUrl(url)) {
            throw new IllegalArgumentException("Open a YouTube video first");
        }
        initialize(context);
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--dump-single-json");
        request.addOption("--skip-download");
        request.addOption("--no-playlist");
        request.addOption("--no-warnings");
        YoutubeDLResponse response = YoutubeDL.getInstance().execute(request, null, false, null);
        JSONObject root = new JSONObject(response.getOut().trim());
        TreeSet<Integer> heights = new TreeSet<>();
        JSONArray formats = root.optJSONArray("formats");
        if (formats != null) {
            for (int index = 0; index < formats.length(); index++) {
                JSONObject format = formats.optJSONObject(index);
                if (format == null || "none".equals(format.optString("vcodec", "none"))) {
                    continue;
                }
                int height = format.optInt("height", 0);
                if (height >= 144 && height <= 4320) {
                    heights.add(height);
                }
            }
        }
        if (heights.isEmpty()) {
            throw new IllegalStateException("No downloadable video resolutions were found");
        }
        List<Integer> descending = new ArrayList<>(heights);
        Collections.reverse(descending);
        String title = root.optString("title", "YouTube Video").trim();
        if (title.isEmpty()) {
            title = "YouTube Video";
        }
        return new Metadata(title, root.optString("id", ""), descending);
    }

    static boolean isSupportedYouTubeUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        Uri uri = Uri.parse(value);
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            return false;
        }
        String host = uri.getHost().toLowerCase(Locale.US);
        String videoId = null;
        if ("youtu.be".equals(host)) {
            List<String> segments = uri.getPathSegments();
            if (!segments.isEmpty()) {
                videoId = segments.get(0);
            }
        } else if ("youtube.com".equals(host) || host.endsWith(".youtube.com")) {
            if ("/watch".equals(uri.getPath())) {
                videoId = uri.getQueryParameter("v");
            } else {
                List<String> segments = uri.getPathSegments();
                if (segments.size() >= 2
                        && ("shorts".equals(segments.get(0)) || "live".equals(segments.get(0)))) {
                    videoId = segments.get(1);
                }
            }
        }
        return videoId != null && videoId.matches("[A-Za-z0-9_-]{11}");
    }

    static String safeDisplayName(String title) {
        String cleaned = title == null ? "YouTube Video" : title
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) {
            cleaned = "YouTube Video";
        }
        return cleaned.length() > 80 ? cleaned.substring(0, 80).trim() : cleaned;
    }

    static final class Metadata {
        final String title;
        final String videoId;
        final List<Integer> resolutions;

        Metadata(String title, String videoId, List<Integer> resolutions) {
            this.title = title;
            this.videoId = videoId;
            this.resolutions = Collections.unmodifiableList(new ArrayList<>(resolutions));
        }
    }
}
