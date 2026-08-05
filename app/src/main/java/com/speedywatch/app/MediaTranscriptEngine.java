package com.speedywatch.app;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Extracts public caption tracks advertised by yt-dlp for supported non-YouTube media pages. */
final class MediaTranscriptEngine {
    private static final int MAX_CAPTION_BYTES = 8 * 1024 * 1024;
    private static final int MAX_TRACKS = 100;
    private static final Pattern CUE_TIMING = Pattern.compile(
            "(?m)^(?:[ \\t]*\\d+[ \\t]*\\n)?[ \\t]*"
                    + "((?:\\d{1,2}:)?\\d{1,2}:\\d{2}[.,]\\d{3})[ \\t]*-->[ \\t]*"
                    + "((?:\\d{1,2}:)?\\d{1,2}:\\d{2}[.,]\\d{3})(?:[ \\t]+[^\\n]*)?[ \\t]*$"
    );
    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    private MediaTranscriptEngine() {
    }

    static List<YouTubeSubsDialog.CaptionOption> loadOptions(
            Context context,
            String pageUrl,
            String cookieHeader,
            String userAgent
    ) throws Exception {
        JSONObject info = MediaDownloadEngine.loadInfo(
                context,
                pageUrl,
                cookieHeader,
                userAgent,
                pageUrl
        );
        List<Track> tracks = tracks(info);
        List<YouTubeSubsDialog.CaptionOption> options = new ArrayList<>();
        for (Track track : tracks) {
            if (options.size() >= MAX_TRACKS) {
                break;
            }
            options.add(new YouTubeSubsDialog.CaptionOption(track.key, track.label));
        }
        return options;
    }

    static Result loadTranscript(
            Context context,
            String pageUrl,
            String selectionKey,
            String cookieHeader,
            String userAgent
    ) throws Exception {
        JSONObject info = MediaDownloadEngine.loadInfo(
                context,
                pageUrl,
                cookieHeader,
                userAgent,
                pageUrl
        );
        List<Track> tracks = tracks(info);
        Track selected = selectTrack(tracks, selectionKey);
        if (selected == null) {
            throw new IOException("No captions are available for this video");
        }
        String body = selected.data != null
                ? selected.data
                : downloadCaption(
                        selected.url,
                        pageUrl,
                        cookieHeader,
                        userAgent
                );
        List<TranscriptEntry> entries = parse(body, selected.extension);
        if (entries.isEmpty()) {
            throw new IOException("This caption track contained no readable text");
        }
        String title = MediaDownloadEngine.safeDisplayName(info.optString("title", "Video"));
        return new Result(entries, title, pageUrl, publisherName(info));
    }

    static String publisherName(JSONObject info) {
        if (info == null) {
            return "";
        }
        String publisher = info.optString("channel", "").trim();
        if (publisher.isEmpty()) {
            publisher = info.optString("uploader", "").trim();
        }
        if (publisher.isEmpty()) {
            publisher = info.optString("creator", "").trim();
        }
        publisher = publisher.replaceAll("\\p{Cntrl}", " ")
                .replaceAll("\\s+", " ")
                .trim();
        return publisher.length() > 300 ? publisher.substring(0, 300).trim() : publisher;
    }

    private static List<Track> tracks(JSONObject info) {
        List<Track> tracks = new ArrayList<>();
        collectTracks(info.optJSONObject("subtitles"), false, tracks);
        collectTracks(info.optJSONObject("automatic_captions"), true, tracks);
        return tracks;
    }

    private static void collectTracks(JSONObject groups, boolean automatic, List<Track> output) {
        if (groups == null || output.size() >= MAX_TRACKS) {
            return;
        }
        List<String> languages = new ArrayList<>();
        groups.keys().forEachRemaining(languages::add);
        Collections.sort(languages);
        for (String language : languages) {
            JSONArray formats = groups.optJSONArray(language);
            Track track = bestTrack(language, formats, automatic);
            if (track != null) {
                output.add(track);
                if (output.size() >= MAX_TRACKS) {
                    return;
                }
            }
        }
    }

    private static Track bestTrack(String language, JSONArray formats, boolean automatic) {
        if (formats == null) {
            return null;
        }
        Track best = null;
        int bestRank = Integer.MAX_VALUE;
        for (int index = 0; index < formats.length(); index++) {
            JSONObject format = formats.optJSONObject(index);
            if (format == null) {
                continue;
            }
            String url = SupportedSite.validatedHttpsUrl(format.optString("url", ""));
            String data = boundedCaptionData(format.optString("data", null));
            String extension = format.optString("ext", "").toLowerCase(Locale.US);
            int rank = extensionRank(extension);
            if (rank == Integer.MAX_VALUE || (url == null && data == null)) {
                continue;
            }
            if (data == null && format.optString("protocol", "").toLowerCase(Locale.US).contains("m3u8")) {
                rank += 10;
            }
            if (rank >= bestRank) {
                continue;
            }
            String name = format.optString("name", language).trim();
            if (name.isEmpty()) {
                name = language;
            }
            String key = (automatic ? "a:" : "m:") + language;
            String label = name + (automatic ? " (auto-generated)" : " (manual)");
            best = new Track(key, label, language, extension, url, data, automatic);
            bestRank = rank;
        }
        return best;
    }

    private static int extensionRank(String extension) {
        if ("json3".equals(extension)) return 0;
        if ("vtt".equals(extension)) return 1;
        if ("srt".equals(extension)) return 2;
        if ("json".equals(extension)) return 3;
        return Integer.MAX_VALUE;
    }

    private static String boundedCaptionData(String data) {
        if (data == null || data.isEmpty()) {
            return null;
        }
        return data.getBytes(StandardCharsets.UTF_8).length <= MAX_CAPTION_BYTES ? data : null;
    }

    private static Track selectTrack(List<Track> tracks, String selectionKey) {
        if (selectionKey != null && !selectionKey.isEmpty()) {
            for (Track track : tracks) {
                if (track.key.equals(selectionKey)) {
                    return track;
                }
            }
            return null;
        }
        Track firstManual = null;
        Track firstAutomatic = null;
        for (Track track : tracks) {
            if (!track.automatic && firstManual == null) firstManual = track;
            if (track.automatic && firstAutomatic == null) firstAutomatic = track;
            if (track.language.toLowerCase(Locale.US).startsWith("en")) {
                if (!track.automatic) return track;
                if (firstAutomatic == null) firstAutomatic = track;
            }
        }
        return firstManual != null ? firstManual : firstAutomatic;
    }

    private static String downloadCaption(
            String captionUrl,
            String pageUrl,
            String cookieHeader,
            String userAgent
    ) throws Exception {
        String currentUrl = captionUrl;
        SupportedSite sourceSite = SupportedSite.forUrl(pageUrl);
        String sourceCookieDomain = SupportedSite.cookieDomainForUrl(pageUrl);
        if (sourceSite == null) {
            throw new IOException("Caption source was not trusted");
        }
        for (int redirect = 0; redirect <= 3; redirect++) {
            String valid = SupportedSite.validatedHttpsUrl(currentUrl);
            String captionHost = valid == null ? null : java.net.URI.create(valid).getHost();
            if (captionHost == null || !sourceSite.ownsHost(captionHost)) {
                throw new IOException("Caption URL was not trusted");
            }
            HttpURLConnection connection = (HttpURLConnection) new URL(valid).openConnection();
            try {
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(15_000);
                connection.setReadTimeout(30_000);
                connection.setUseCaches(false);
                connection.setRequestProperty("Accept", "text/vtt,application/json,text/plain,*/*");
                connection.setRequestProperty("Referer", pageUrl);
                if (SupportedSite.forUrl(pageUrl) != SupportedSite.BILIBILI
                        && safeHeader(userAgent, 512) != null) {
                    connection.setRequestProperty("User-Agent", userAgent.trim());
                }
                String captionCookieDomain = SupportedSite.cookieDomainForUrl(valid);
                if (!sourceCookieDomain.isEmpty()
                        && sourceCookieDomain.equals(captionCookieDomain)
                        && safeHeader(cookieHeader, 32 * 1024) != null) {
                    connection.setRequestProperty("Cookie", cookieHeader);
                }
                int status = connection.getResponseCode();
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        throw new IOException("Caption redirect was incomplete");
                    }
                    currentUrl = new URL(new URL(valid), location).toString();
                    continue;
                }
                if (status < 200 || status >= 300) {
                    throw new IOException("Caption request failed");
                }
                try (InputStream input = connection.getInputStream();
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > MAX_CAPTION_BYTES) {
                            throw new IOException("Caption response was too large");
                        }
                        output.write(buffer, 0, count);
                    }
                    return new String(output.toByteArray(), StandardCharsets.UTF_8);
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new IOException("Caption request redirected too many times");
    }

    private static String safeHeader(String value, int maxLength) {
        if (value == null || value.trim().isEmpty() || value.length() > maxLength) {
            return null;
        }
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 ? null : value;
    }

    private static List<TranscriptEntry> parse(String body, String extension) throws Exception {
        if ("json3".equals(extension)) {
            return parseJson3(body);
        }
        if ("json".equals(extension)) {
            List<TranscriptEntry> bilibili = parseBilibiliJson(body);
            if (!bilibili.isEmpty()) {
                return bilibili;
            }
            return parseJson3(body);
        }
        return parseTimedText(body);
    }

    private static List<TranscriptEntry> parseJson3(String body) throws Exception {
        JSONArray events = new JSONObject(body).optJSONArray("events");
        if (events == null) {
            return Collections.emptyList();
        }
        List<TranscriptEntry> entries = new ArrayList<>();
        for (int eventIndex = 0; eventIndex < events.length(); eventIndex++) {
            JSONObject event = events.optJSONObject(eventIndex);
            JSONArray segments = event == null ? null : event.optJSONArray("segs");
            if (segments == null) continue;
            StringBuilder text = new StringBuilder();
            for (int index = 0; index < segments.length(); index++) {
                JSONObject segment = segments.optJSONObject(index);
                if (segment != null) text.append(segment.optString("utf8", ""));
            }
            addEntry(
                    entries,
                    event.optDouble("tStartMs", 0) / 1000.0,
                    event.optDouble("dDurationMs", 0) / 1000.0,
                    text.toString()
            );
        }
        return entries;
    }

    private static List<TranscriptEntry> parseBilibiliJson(String body) throws Exception {
        JSONArray cues = new JSONObject(body).optJSONArray("body");
        if (cues == null) {
            return Collections.emptyList();
        }
        List<TranscriptEntry> entries = new ArrayList<>();
        for (int index = 0; index < cues.length(); index++) {
            JSONObject cue = cues.optJSONObject(index);
            if (cue == null) continue;
            double start = cue.optDouble("from", 0);
            double end = cue.optDouble("to", start);
            addEntry(entries, start, Math.max(0, end - start), cue.optString("content", ""));
        }
        return entries;
    }

    static List<TranscriptEntry> parseTimedText(String body) {
        String normalizedBody = body.replace("\r\n", "\n").replace('\r', '\n');
        Matcher matcher = CUE_TIMING.matcher(normalizedBody);
        List<TranscriptEntry> entries = new ArrayList<>();
        if (!matcher.find()) {
            return entries;
        }
        while (true) {
            double start = parseTimestamp(matcher.group(1));
            double end = parseTimestamp(matcher.group(2));
            int textStart = matcher.end();
            boolean hasNextCue = matcher.find();
            int textEnd = hasNextCue ? matcher.start() : normalizedBody.length();
            addEntry(
                    entries,
                    start,
                    Math.max(0, end - start),
                    normalizedBody.substring(textStart, textEnd)
            );
            if (!hasNextCue) {
                break;
            }
        }
        entries.sort(Comparator.comparingDouble(entry -> entry.startSeconds));
        return deduplicate(entries);
    }

    private static double parseTimestamp(String value) {
        String[] parts = value.replace(',', '.').split(":");
        double seconds = 0;
        for (String part : parts) {
            seconds = seconds * 60 + Double.parseDouble(part);
        }
        return seconds;
    }

    private static void addEntry(
            List<TranscriptEntry> entries,
            double start,
            double duration,
            String rawText
    ) {
        String text = TAG.matcher(rawText)
                .replaceAll(" ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
        if (!text.isEmpty() && Double.isFinite(start) && start >= 0) {
            entries.add(new TranscriptEntry(start, Math.max(0, duration), text));
        }
    }

    private static List<TranscriptEntry> deduplicate(List<TranscriptEntry> entries) {
        List<TranscriptEntry> unique = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (TranscriptEntry entry : entries) {
            String key = Math.round(entry.startSeconds * 1000) + "|" + entry.text;
            if (seen.add(key)) unique.add(entry);
        }
        return unique;
    }

    static final class Result {
        final List<TranscriptEntry> entries;
        final String title;
        final String pageUrl;
        final String channelName;

        Result(
                List<TranscriptEntry> entries,
                String title,
                String pageUrl,
                String channelName
        ) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.title = title;
            this.pageUrl = pageUrl;
            this.channelName = channelName;
        }
    }

    private static final class Track {
        final String key;
        final String label;
        final String language;
        final String extension;
        final String url;
        final String data;
        final boolean automatic;

        Track(
                String key,
                String label,
                String language,
                String extension,
                String url,
                String data,
                boolean automatic
        ) {
            this.key = key;
            this.label = label;
            this.language = language;
            this.extension = extension;
            this.url = url;
            this.data = data;
            this.automatic = automatic;
        }
    }
}
