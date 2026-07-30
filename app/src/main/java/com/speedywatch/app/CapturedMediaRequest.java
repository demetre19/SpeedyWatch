package com.speedywatch.app;

import java.net.URI;
import java.util.Locale;
import java.util.Map;

/** A bounded, same-service media request observed from the authenticated WebView session. */
final class CapturedMediaRequest {
    private static final int MAX_HEADER_LENGTH = 32 * 1024;
    private static final int MANIFEST_PRIORITY = 2;
    private static final int VIDEO_PRIORITY = 1;

    final String pageUrl;
    final String mediaUrl;
    final String cookieHeader;
    final String userAgent;
    final String referer;
    final String origin;
    final String authorization;
    private final int priority;

    private CapturedMediaRequest(
            String pageUrl,
            String mediaUrl,
            String cookieHeader,
            String userAgent,
            String referer,
            String origin,
            String authorization,
            int priority
    ) {
        this.pageUrl = pageUrl;
        this.mediaUrl = mediaUrl;
        this.cookieHeader = cookieHeader;
        this.userAgent = userAgent;
        this.referer = referer;
        this.origin = origin;
        this.authorization = authorization;
        this.priority = priority;
    }

    static CapturedMediaRequest observe(
            String pageUrl,
            String requestUrl,
            String method,
            Map<String, String> headers,
            CapturedMediaRequest previous
    ) {
        String validPageUrl = SupportedSite.validatedHttpsUrl(pageUrl);
        String validMediaUrl = SupportedSite.validatedHttpsUrl(requestUrl);
        if (!"GET".equalsIgnoreCase(method)
                || !SupportedSite.isSupportedDownloadUrl(validPageUrl)
                || !isAllowedResource(validPageUrl, validMediaUrl)) {
            return previous;
        }
        int priority = mediaPriority(validMediaUrl);
        if (priority == 0) {
            return previous;
        }
        if (previous != null
                && previous.matches(validPageUrl)
                && previous.priority >= priority) {
            return previous;
        }
        return new CapturedMediaRequest(
                validPageUrl,
                validMediaUrl,
                header(headers, "cookie", MAX_HEADER_LENGTH),
                header(headers, "user-agent", 512),
                validContextUrl(validPageUrl, header(headers, "referer", 8_192)),
                validContextUrl(validPageUrl, header(headers, "origin", 8_192)),
                header(headers, "authorization", 8_192),
                priority
        );
    }

    boolean matches(String sourceUrl) {
        String validSourceUrl = SupportedSite.validatedHttpsUrl(sourceUrl);
        return validSourceUrl != null && pageUrl.equals(validSourceUrl);
    }

    static boolean isAllowedResource(String sourceUrl, String resourceUrl) {
        String validSourceUrl = SupportedSite.validatedHttpsUrl(sourceUrl);
        String validResourceUrl = SupportedSite.validatedHttpsUrl(resourceUrl);
        if (validSourceUrl == null || validResourceUrl == null) {
            return false;
        }
        SupportedSite sourceSite = SupportedSite.forUrl(validSourceUrl);
        SupportedSite resourceSite = SupportedSite.forUrl(validResourceUrl);
        if (sourceSite == null || resourceSite == null) {
            return false;
        }
        if (sourceSite == resourceSite) {
            return true;
        }
        String resourceHost = URI.create(validResourceUrl).getHost().toLowerCase(Locale.US);
        return sourceSite == SupportedSite.INSTAGRAM
                && resourceSite == SupportedSite.FACEBOOK
                && (resourceHost.equals("fbcdn.net") || resourceHost.endsWith(".fbcdn.net"));
    }

    static String validContextUrl(String sourceUrl, String contextUrl) {
        String validContextUrl = SupportedSite.validatedHttpsUrl(contextUrl);
        if (validContextUrl == null) {
            return null;
        }
        SupportedSite sourceSite = SupportedSite.forUrl(sourceUrl);
        SupportedSite contextSite = SupportedSite.forUrl(validContextUrl);
        return sourceSite != null && sourceSite == contextSite ? validContextUrl : null;
    }

    private static int mediaPriority(String value) {
        URI uri = URI.create(value);
        String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase(Locale.US);
        String query = uri.getRawQuery() == null ? "" : uri.getRawQuery().toLowerCase(Locale.US);
        if (path.endsWith(".ts")
                || path.endsWith(".aac")
                || path.endsWith(".m4s")
                || path.endsWith(".fmp4")
                || path.endsWith("/init.mp4")
                || path.contains("/seg-")
                || path.contains("/segment")
                || path.contains("/chunk")) {
            return 0;
        }
        if (path.endsWith(".m3u8")
                || path.endsWith(".m3u")
                || path.endsWith(".mpd")
                || query.contains("format=m3u8")
                || query.contains("type=m3u8")) {
            return MANIFEST_PRIORITY;
        }
        if (path.endsWith(".mp4")
                || path.endsWith(".webm")
                || path.endsWith(".mkv")
                || path.endsWith(".avi")
                || path.endsWith(".mov")
                || path.endsWith(".flv")
                || path.contains("/mp4/")
                || path.contains("/video/mp4")) {
            return VIDEO_PRIORITY;
        }
        return 0;
    }

    private static String header(Map<String, String> headers, String name, int maxLength) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                String value = entry.getValue();
                if (value == null
                        || value.isEmpty()
                        || value.length() > maxLength
                        || value.indexOf('\r') >= 0
                        || value.indexOf('\n') >= 0
                        || value.indexOf('\0') >= 0) {
                    return null;
                }
                return value;
            }
        }
        return null;
    }
}
