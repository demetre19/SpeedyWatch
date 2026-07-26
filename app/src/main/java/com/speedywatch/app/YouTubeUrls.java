package com.speedywatch.app;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class YouTubeUrls {
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");
    private static final Pattern HTTPS_URL = Pattern.compile(
            "https://[^\\s<>\\\"']+",
            Pattern.CASE_INSENSITIVE
    );

    private YouTubeUrls() {
    }

    static String canonicalVideoUrlFromText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String direct = canonicalVideoUrl(value.trim());
        if (direct != null) {
            return direct;
        }

        Matcher matcher = HTTPS_URL.matcher(value);
        while (matcher.find()) {
            String candidate = trimTrailingPunctuation(matcher.group());
            String canonical = canonicalVideoUrl(candidate);
            if (canonical != null) {
                return canonical;
            }
        }
        return null;
    }
    static String searchOrVideoUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        String trimmed = value.trim();
        String videoUrl = canonicalVideoUrlFromText(trimmed);
        if (videoUrl != null) {
            return videoUrl;
        }
        if (trimmed.contains("://") || HTTPS_URL.matcher(trimmed).find()) {
            return null;
        }

        try {
            return "https://www.youtube.com/results?search_query="
                    + URLEncoder.encode(trimmed, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }


    static String canonicalVideoUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }

        try {
            URI uri = URI.create(value.trim().replace("&amp;", "&"));
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
                return null;
            }

            String host = uri.getHost().toLowerCase(Locale.US);
            String videoId = null;
            if ("youtu.be".equals(host)) {
                videoId = pathSegment(uri.getPath(), 0);
            } else if (isYouTubeHost(host)) {
                String first = pathSegment(uri.getPath(), 0);
                if ("watch".equals(first)) {
                    videoId = queryParameter(uri.getRawQuery(), "v");
                } else if (isVideoPath(first)) {
                    videoId = pathSegment(uri.getPath(), 1);
                } else if ("attribution_link".equals(first)) {
                    String nestedPath = queryParameter(uri.getRawQuery(), "u");
                    if (nestedPath != null && nestedPath.startsWith("/")) {
                        return canonicalVideoUrl("https://www.youtube.com" + nestedPath);
                    }
                }
            } else if (isYouTubeNoCookieHost(host)
                    && "embed".equals(pathSegment(uri.getPath(), 0))) {
                videoId = pathSegment(uri.getPath(), 1);
            }

            return isVideoId(videoId)
                    ? "https://www.youtube.com/watch?v=" + videoId
                    : null;
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static boolean isYouTubeHost(String host) {
        return "youtube.com".equals(host) || host.endsWith(".youtube.com");
    }

    private static boolean isYouTubeNoCookieHost(String host) {
        return "youtube-nocookie.com".equals(host)
                || host.endsWith(".youtube-nocookie.com");
    }

    private static boolean isVideoPath(String value) {
        return "shorts".equals(value)
                || "live".equals(value)
                || "embed".equals(value)
                || "v".equals(value)
                || "e".equals(value);
    }

    private static boolean isVideoId(String value) {
        return value != null && VIDEO_ID.matcher(value).matches();
    }

    private static String pathSegment(String path, int index) {
        if (path == null) {
            return null;
        }
        int found = 0;
        for (String segment : path.split("/")) {
            if (segment.isEmpty()) {
                continue;
            }
            if (found == index) {
                return decode(segment);
            }
            found++;
        }
        return null;
    }

    private static String queryParameter(String rawQuery, String name) {
        if (rawQuery == null) {
            return null;
        }
        for (String pair : rawQuery.split("&")) {
            int separator = pair.indexOf('=');
            String rawName = separator >= 0 ? pair.substring(0, separator) : pair;
            if (name.equals(decode(rawName))) {
                return decode(separator >= 0 ? pair.substring(separator + 1) : "");
            }
        }
        return null;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    private static String trimTrailingPunctuation(String value) {
        int end = value.length();
        while (end > 0 && ".,;:!?)]}".indexOf(value.charAt(end - 1)) >= 0) {
            end--;
        }
        return value.substring(0, end);
    }
}
