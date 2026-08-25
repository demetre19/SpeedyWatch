package com.speedywatch.app;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Fetches a cookie-free, bounded og:image preview for a saved public link. */
final class OpenGraphPreview {
    private static final int MAX_HTML_BYTES = 512 * 1024;
    private static final int MAX_REDIRECTS = 3;
    private static final Pattern META_TAG = Pattern.compile(
            "<meta\\b[^>]*>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern PROPERTY = Pattern.compile(
            "(?:property|name)\\s*=\\s*(['\\\"])(og:image|twitter:image)\\s*\\1",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern CONTENT = Pattern.compile(
            "content\\s*=\\s*(['\\\"])(.*?)\\1",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private OpenGraphPreview() {
    }

    static byte[] fetch(String sourceUrl) throws IOException {
        String current = SupportedSite.validatedHttpsUrl(sourceUrl);
        if (current == null || SupportedSite.forUrl(current) == SupportedSite.MEGA) {
            return null;
        }
        for (int redirect = 0; redirect <= MAX_REDIRECTS; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            try {
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(8_000);
                connection.setReadTimeout(12_000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "text/html");
                connection.setRequestProperty("User-Agent", "SpeedyWatch link preview");
                int responseCode = connection.getResponseCode();
                if (responseCode >= 300 && responseCode < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        return null;
                    }
                    current = validatedRedirect(current, location);
                    if (current == null) {
                        return null;
                    }
                    continue;
                }
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return null;
                }
                String contentType = connection.getContentType();
                if (contentType == null
                        || !contentType.toLowerCase(java.util.Locale.US).startsWith("text/html")) {
                    return null;
                }
                int contentLength = connection.getContentLength();
                if (contentLength > MAX_HTML_BYTES) {
                    return null;
                }
                String html;
                try (InputStream input = connection.getInputStream()) {
                    html = new String(readBounded(input), StandardCharsets.UTF_8);
                }
                String imageUrl = imageUrlFromHtml(current, html);
                return imageUrl == null ? null : SavedThumbnail.fetchImageUrl(imageUrl);
            } finally {
                connection.disconnect();
            }
        }
        return null;
    }

    static String imageUrlFromHtml(String pageUrl, String html) {
        String image = imageUrlFromHtml(pageUrl, html, "og:image");
        return image != null ? image : imageUrlFromHtml(pageUrl, html, "twitter:image");
    }

    private static String imageUrlFromHtml(String pageUrl, String html, String metadataName) {
        String page = SupportedSite.validatedHttpsUrl(pageUrl);
        if (page == null || SupportedSite.forUrl(page) == SupportedSite.MEGA || html == null) {
            return null;
        }
        Matcher tags = META_TAG.matcher(html);
        while (tags.find()) {
            String tag = tags.group();
            Matcher property = PROPERTY.matcher(tag);
            if (!property.find() || !metadataName.equalsIgnoreCase(property.group(2))) {
                continue;
            }
            Matcher content = CONTENT.matcher(tag);
            if (!content.find()) {
                continue;
            }
            String raw = decodeHtmlEntities(content.group(2).trim());
            if (raw.isEmpty()) {
                continue;
            }
            try {
                String candidate = SupportedSite.validatedHttpsUrl(
                        URI.create(page).resolve(raw).toString()
                );
                if (candidate != null && SupportedSite.forUrl(candidate) != SupportedSite.MEGA) {
                    return candidate;
                }
            } catch (RuntimeException ignored) {
                // Try the next metadata tag.
            }
        }
        return null;
    }

    private static String validatedRedirect(String current, String location) {
        try {
            URI resolved = URI.create(current).resolve(location);
            String valid = SupportedSite.validatedHttpsUrl(resolved.toString());
            return valid == null || SupportedSite.forUrl(valid) == SupportedSite.MEGA ? null : valid;
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(64 * 1024);
        byte[] buffer = new byte[8 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_HTML_BYTES) {
                throw new IOException("Open Graph page is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static String decodeHtmlEntities(String value) {
        return value.replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">");
    }
}
