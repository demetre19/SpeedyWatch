package com.speedywatch.app;

import android.content.Context;

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
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.InputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.charset.StandardCharsets;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.regex.Pattern;

final class MediaDownloadEngine {
    private static final Object INIT_LOCK = new Object();
    private static final String BUNDLED_YTDLP_VERSION = "2026.07.04";
    private static final String DOWNLOAD_PREFS = "download_engine";
    private static final String YTDLP_VERSION_KEY = "bundled_ytdlp_version";
    private static final long MAX_INFO_JSON_BYTES = 2L * 1024L * 1024L;
    private static final long MAX_METADATA_JSON_BYTES = 8L * 1024L * 1024L;
    private static final int MAX_COOKIE_HEADER_BYTES = 32 * 1024;
    private static final Pattern COOKIE_NAME = Pattern.compile(
            "[!#$%&'*+.^_`|~0-9A-Za-z-]{1,256}"
    );
    private static volatile boolean initialized;

    private MediaDownloadEngine() {
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

    static Metadata loadMetadata(
            Context context,
            String url,
            String cookieHeader,
            String userAgent,
            String referer,
            CapturedMediaRequest capturedRequest,
            String capturedCookieHeader
    ) throws Exception {
        JSONObject root = loadInfo(
                context,
                url,
                cookieHeader,
                userAgent,
                referer,
                capturedRequest,
                capturedCookieHeader
        );
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
        String title = root.optString("title", "Video").trim();
        if (title.isEmpty()) {
            title = "Video";
        }
        return new Metadata(title, root.optString("id", ""), descending);
    }

    static boolean isSupportedDownloadUrl(String value) {
        return SupportedSite.isSupportedDownloadUrl(value);
    }
    static JSONObject loadInfo(
            Context context,
            String url,
            String cookieHeader,
            String userAgent,
            String referer
    ) throws Exception {
        return loadInfo(
                context,
                url,
                cookieHeader,
                userAgent,
                referer,
                null,
                null
        );
    }

    static JSONObject loadInfo(
            Context context,
            String url,
            String cookieHeader,
            String userAgent,
            String referer,
            CapturedMediaRequest capturedRequest,
            String capturedCookieHeader
    ) throws Exception {
        if (!isSupportedDownloadUrl(url)) {
            throw new IllegalArgumentException("Open a supported video first");
        }
        initialize(context);
        File sessionDir = new File(
                context.getCacheDir(),
                "media-info-" + UUID.randomUUID()
        );
        if (!sessionDir.mkdirs() && !sessionDir.isDirectory()) {
            throw new IOException("Could not prepare media information");
        }
        try {
            File cookieFile = capturedRequest == null
                    ? writeSessionCookies(sessionDir, url, cookieHeader)
                    : writeSessionCookies(
                            sessionDir,
                            url,
                            cookieHeader,
                            capturedRequest.mediaUrl,
                            capturedCookieHeader
                    );
            YoutubeDLRequest request = new YoutubeDLRequest(url);
            request.addOption("--dump-single-json");
            request.addOption("--skip-download");
            request.addOption("--no-playlist");
            request.addOption("--no-warnings");
            String requestUserAgent = capturedRequest != null
                    && capturedRequest.userAgent != null
                    ? capturedRequest.userAgent
                    : userAgent;
            String requestReferer = capturedRequest != null
                    && capturedRequest.referer != null
                    ? capturedRequest.referer
                    : referer;
            addSessionOptions(
                    request,
                    url,
                    requestUserAgent,
                    requestReferer,
                    cookieFile
            );
            if (capturedRequest != null && capturedRequest.origin != null) {
                request.addOption("--add-header", "Origin:" + capturedRequest.origin);
            }
            YoutubeDLResponse response = YoutubeDL.getInstance().execute(
                    request,
                    null,
                    false,
                    null
            );
            String output = response.getOut().trim();
            if (output.getBytes(StandardCharsets.UTF_8).length > MAX_METADATA_JSON_BYTES) {
                throw new IOException("Media information was too large");
            }
            return new JSONObject(output);
        } finally {
            deleteRecursively(sessionDir);
        }
    }

    static File writeSessionCookies(File directory, String url, String cookieHeader)
            throws IOException {
        return writeSessionCookies(directory, url, cookieHeader, null, null);
    }

    static File writeSessionCookies(
            File directory,
            String url,
            String cookieHeader,
            String contextUrl,
            String contextCookieHeader
    ) throws IOException {
        SupportedSite site = SupportedSite.forUrl(url);
        String domain = SupportedSite.cookieDomainForUrl(url);
        String contextDomain = site != null && site == SupportedSite.forUrl(contextUrl)
                ? SupportedSite.cookieDomainForUrl(contextUrl)
                : "";
        if (!isUsableCookieHeader(domain, cookieHeader)
                && !isUsableCookieHeader(contextDomain, contextCookieHeader)) {
            return null;
        }
        File cookieFile = new File(directory, "session-cookies.txt");
        int written;
        try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(
                new FileOutputStream(cookieFile),
                StandardCharsets.UTF_8
        ))) {
            writer.write("# Netscape HTTP Cookie File");
            writer.newLine();
            written = writeCookieHeader(writer, domain, cookieHeader, 200);
            written += writeCookieHeader(
                    writer,
                    contextDomain,
                    contextCookieHeader,
                    200 - written
            );
        }
        if (written == 0) {
            cookieFile.delete();
            return null;
        }
        cookieFile.setReadable(false, false);
        cookieFile.setWritable(false, false);
        cookieFile.setReadable(true, true);
        cookieFile.setWritable(true, true);
        return cookieFile;
    }

    private static boolean isUsableCookieHeader(String domain, String cookieHeader) {
        if (domain.isEmpty() || cookieHeader == null) {
            return false;
        }
        int byteCount = cookieHeader.getBytes(StandardCharsets.UTF_8).length;
        return byteCount > 0 && byteCount <= MAX_COOKIE_HEADER_BYTES;
    }

    private static int writeCookieHeader(
            BufferedWriter writer,
            String domain,
            String cookieHeader,
            int limit
    ) throws IOException {
        if (limit <= 0 || !isUsableCookieHeader(domain, cookieHeader)) {
            return 0;
        }
        int written = 0;
        String[] cookies = cookieHeader.split(";", limit + 1);
        for (String cookie : cookies) {
            if (written >= limit) {
                break;
            }
            int separator = cookie.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = cookie.substring(0, separator).trim();
            String value = cookie.substring(separator + 1).trim();
            if (!COOKIE_NAME.matcher(name).matches()
                    || value.indexOf('\r') >= 0
                    || value.indexOf('\n') >= 0
                    || value.indexOf('\t') >= 0) {
                continue;
            }
            writer.write(domain);
            writer.write(domain.startsWith(".") ? "\tTRUE\t/\tTRUE\t0\t" : "\tFALSE\t/\tTRUE\t0\t");
            writer.write(name);
            writer.write('\t');
            writer.write(value);
            writer.newLine();
            written++;
        }
        return written;
    }

    static void addSessionOptions(
            YoutubeDLRequest request,
            String url,
            String userAgent,
            String referer,
            File cookieFile
    ) {
        SupportedSite site = SupportedSite.forUrl(url);
        if (site == null) {
            return;
        }
        String boundedUserAgent = boundedHeaderValue(userAgent, 512);
        if (site != SupportedSite.BILIBILI && !boundedUserAgent.isEmpty()) {
            request.addOption("--user-agent", boundedUserAgent);
        }
        String validReferer = SupportedSite.validatedHttpsUrl(referer);
        if (validReferer != null && site == SupportedSite.forUrl(validReferer)) {
            request.addOption("--referer", validReferer);
        }
        if (cookieFile != null && cookieFile.isFile()) {
            request.addOption("--cookies", cookieFile.getAbsolutePath());
        }
    }

    private static String boundedHeaderValue(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength
                || trimmed.indexOf('\r') >= 0
                || trimmed.indexOf('\n') >= 0
                || trimmed.indexOf('\0') >= 0) {
            return "";
        }
        return trimmed;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }


    static String safeDisplayName(String title) {
        String cleaned = title == null ? "Video" : title
                .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isEmpty()) {
            cleaned = "Video";
        }
        return cleaned.length() > 80 ? cleaned.substring(0, 80).trim() : cleaned;
    }

    static String downloadedTitle(File jobDir, String fallbackTitle) {
        return downloadedMedia(jobDir, fallbackTitle).title;
    }

    static DownloadedMedia downloadedMedia(File jobDir, String fallbackTitle) {
        String fallback = safeDisplayName(fallbackTitle);
        File info = new File(jobDir, "source.info.json");
        if (!info.isFile() || info.length() <= 0 || info.length() > MAX_INFO_JSON_BYTES) {
            return new DownloadedMedia(fallback, null);
        }
        try {
            JSONObject root = new JSONObject(
                    new String(Files.readAllBytes(info.toPath()), StandardCharsets.UTF_8)
            );
            String publisher = root.optString("channel", "").trim();
            if (publisher.isEmpty()) {
                publisher = root.optString("uploader", "").trim();
            }
            if (publisher.isEmpty()) {
                publisher = root.optString("creator", "").trim();
            }
            if (!publisher.isEmpty()) {
                publisher = publisher
                        .replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", " ")
                        .replaceAll("\\s+", " ")
                        .trim();
                if (publisher.length() > 80) {
                    publisher = publisher.substring(0, 80).trim();
                }
                if (".".equals(publisher) || "..".equals(publisher)) {
                    publisher = "";
                }
            }
            return new DownloadedMedia(
                    safeDisplayName(root.optString("title", fallbackTitle)),
                    publisher.isEmpty() ? null : publisher
            );
        } catch (Exception ignored) {
            return new DownloadedMedia(fallback, null);
        }
    }

    static final class DownloadedMedia {
        final String title;
        final String publisher;

        DownloadedMedia(String title, String publisher) {
            this.title = title;
            this.publisher = publisher;
        }
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
