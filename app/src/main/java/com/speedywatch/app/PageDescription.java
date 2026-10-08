package com.speedywatch.app;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Cookie-free bounded page fetch that extracts the description meta tags
 * (og:description, twitter:description, description). X posts embed the tweet
 * text in og:description, so this gives background regeneration a content
 * source without opening the page in the WebView.
 */
final class PageDescription {
    private static final int MAX_BYTES = 512 * 1024;
    private static final int MAX_DESCRIPTION = 8_000;

    private PageDescription() {
    }

    static String fetch(android.content.Context context, String url) {
        // Logged-out first: sites serve crawler-optimized HTML (with og:description)
        // to anonymous requests, and an authenticated fetch often returns a JS shell
        // without meta tags. Retry with the WebView's cookies (same host only) for
        // sites that gate their metadata behind login.
        String withoutCookies = fetchOnce(context, url, false);
        if (withoutCookies != null && !withoutCookies.trim().isEmpty()) {
            return withoutCookies;
        }
        String withCookies = fetchOnce(context, url, true);
        if (withCookies != null && !withCookies.trim().isEmpty()) {
            return withCookies;
        }
        return null;
    }

    private static String fetchOnce(android.content.Context context, String url, boolean withCookies) {
        String validated = SupportedSite.validatedHttpsUrl(url);
        if (validated == null) {
            return null;
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(validated).openConnection();
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(true);
            connection.setRequestProperty(
                    "User-Agent",
                    android.webkit.WebSettings.getDefaultUserAgent(context)
            );
            if (withCookies) {
                String cookies = android.webkit.CookieManager.getInstance()
                        .getCookie(validated);
                if (cookies != null && !cookies.isEmpty()) {
                    connection.setRequestProperty("Cookie", cookies);
                }
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                return null;
            }
            InputStream stream = connection.getInputStream();
            String html = readBounded(stream);
            String description = extract(html, "property", "og:description");
            if (description == null) {
                description = extract(html, "name", "og:description");
            }
            if (description == null) {
                description = extract(html, "property", "twitter:description");
            }
            if (description == null) {
                description = extract(html, "name", "twitter:description");
            }
            if (description == null) {
                description = extract(html, "name", "description");
            }
            return description;
        } catch (IOException | RuntimeException error) {
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String readBounded(InputStream stream) throws IOException {
        try (InputStream input = stream;
                java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_BYTES) {
                    break;
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private static String extract(String html, String attribute, String key) {
        String needle = attribute + "=\"" + key + "\"";
        int at = html.indexOf(needle);
        if (at < 0) {
            needle = attribute + "='" + key + "'";
            at = html.indexOf(needle);
        }
        if (at < 0) {
            return null;
        }
        int tagEnd = html.indexOf('>', at);
        if (tagEnd < 0) {
            return null;
        }
        String tag = html.substring(at, tagEnd);
        int contentAt = tag.indexOf("content=\"");
        if (contentAt < 0) {
            contentAt = tag.indexOf("content='");
            if (contentAt < 0) {
                return null;
            }
            int valueStart = contentAt + "content='".length();
            int valueEnd = tag.indexOf('\'', valueStart);
            if (valueEnd < 0) {
                return null;
            }
            return unescape(tag.substring(valueStart, valueEnd));
        }
        int valueStart = contentAt + "content=\"".length();
        int valueEnd = tag.indexOf('"', valueStart);
        if (valueEnd < 0) {
            return null;
        }
        return unescape(tag.substring(valueStart, valueEnd));
    }

    private static String unescape(String value) {
        String decoded = value
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&#x27;", "'")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("\\u003C", "<")
                .replace("\\n", "\n");
        decoded = decoded.replaceAll("<[^>]{1,80}>", " ").replaceAll(" {2,}", " ");
        return decoded.trim().substring(0, Math.min(MAX_DESCRIPTION, decoded.trim().length()));
    }
}
