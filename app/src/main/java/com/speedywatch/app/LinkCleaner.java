package com.speedywatch.app;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Removes tracking query parameters from shared or copied links while keeping
 * parameters a page actually needs to resolve. Pure Java (no android.*) so
 * unit tests cover the exact shipped rule table.
 */
final class LinkCleaner {

    private LinkCleaner() {
    }

    /**
     * Query keys removed on every host. Names are compared decoded and
     * case-insensitively; {@code utm_} is matched as a prefix.
     */
    private static final Set<String> TRACKER_KEYS = new HashSet<>(Arrays.asList(
            "fbclid", "gclid", "dclid", "wbraid", "gbraid", "msclkid", "ttclid",
            "mc_cid", "mc_eid", "igshid", "igsh", "s", "si",
            "feature", "kw", "ref", "ref_src", "ref_url", "src", "spm",
            "_branch_match_id", "cmpid", "zanpid", "twclid", "li_fat_id",
            "wickedid", "vero_id", "pk_campaign", "pk_kwd", "pk_source",
            "pk_medium", "pk_content", "sc_source", "from", "context",
            "mibextid", "rdid", "share_app_id", "share_from", "share_plat",
            "share_source", "share_tag", "share_medium", "share_id",
            "timestamp", "unique_timestamp", "spmid", "vd_source", "wtsid",
            "ocid", "cvid", "share_token", "sharetype", "x-sec_token",
            "xsec_token", "xsec_source", "sessionid", "ref_"
    ));

    /**
     * Hosts whose post/video links never need query parameters, so every
     * parameter is stripped. Matches the host and every subdomain.
     */
    private static final Set<String> STRIP_ALL_HOSTS = new HashSet<>(Arrays.asList(
            "x.com", "twitter.com",
            "instagram.com", "threads.net",
            "tiktok.com",
            "bsky.app",
            "reddit.com"
    ));

    /**
     * Hosts that keep a small functional allowlist and lose everything else.
     * YouTube keeps playback/link params; Bilibili keeps episode position;
     * SoundCloud keeps playlist context.
     */
    private static final Set<String> YOUTUBE_KEEP = new HashSet<>(Arrays.asList(
            "v", "list", "index", "t", "start", "end", "start_radio"
    ));

    private static final Set<String> BILIBILI_KEEP = new HashSet<>(Arrays.asList(
            "bvid", "p", "t"
    ));

    private static final Set<String> SOUNDCLOUD_KEEP = new HashSet<>(Arrays.asList(
            "in"
    ));

    /** Returns {@code clean(url)} when the setting is on, else the input. */
    static String cleanIf(boolean enabled, String url) {
        return enabled ? clean(url) : url;
    }

    /**
     * Returns {@code url} with tracking parameters removed. Only http/https
     * URLs are touched; every other value returns unchanged.
     */
    static String clean(String url) {
        if (url == null || url.isEmpty()) {
            return url;
        }
        String trimmed = url.trim();
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (Exception error) {
            return url;
        }
        String scheme = uri.getScheme();
        if (scheme == null
                || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
            return url;
        }
        String host = uri.getHost();
        if (host == null) {
            return url;
        }
        host = host.toLowerCase(Locale.US);

        // Operate on the raw string so kept parameters keep their encoding.
        int queryStart = trimmed.indexOf('?');
        if (queryStart < 0) {
            return url;
        }
        int fragmentStart = trimmed.indexOf('#', queryStart);
        int queryEnd = fragmentStart >= 0 ? fragmentStart : trimmed.length();
        String query = trimmed.substring(queryStart + 1, queryEnd);
        if (query.isEmpty()) {
            return url;
        }

        boolean stripAll = matchesAny(host, STRIP_ALL_HOSTS);
        Set<String> keep = null;
        if (!stripAll) {
            if (matchesAny(host, "youtube.com") || host.equals("youtu.be")) {
                keep = YOUTUBE_KEEP;
            } else if (matchesAny(host, "bilibili.com")) {
                keep = BILIBILI_KEEP;
            } else if (matchesAny(host, "soundcloud.com")) {
                keep = SOUNDCLOUD_KEEP;
            }
        }

        StringBuilder keptQuery = new StringBuilder();
        for (String pair : query.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String rawName = eq >= 0 ? pair.substring(0, eq) : pair;
            String name = decodeName(rawName).toLowerCase(Locale.US);
            if (stripAll || isTracker(name)) {
                continue;
            }
            if (keep != null && !keep.contains(name)) {
                continue;
            }
            if (keptQuery.length() > 0) {
                keptQuery.append('&');
            }
            keptQuery.append(pair);
        }

        StringBuilder cleaned = new StringBuilder(trimmed.length());
        cleaned.append(trimmed, 0, queryStart);
        if (keptQuery.length() > 0) {
            cleaned.append('?').append(keptQuery);
        }
        if (fragmentStart >= 0) {
            cleaned.append(trimmed, fragmentStart, trimmed.length());
        }
        return cleaned.toString();
    }

    private static boolean isTracker(String name) {
        return name.startsWith("utm_") || TRACKER_KEYS.contains(name);
    }

    private static boolean matchesAny(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private static boolean matchesAny(String host, Set<String> domains) {
        for (String domain : domains) {
            if (matchesAny(host, domain)) {
                return true;
            }
        }
        return false;
    }

    /** Minimal percent/plus decoder for parameter names. */
    private static String decodeName(String raw) {
        if (raw.indexOf('%') < 0 && raw.indexOf('+') < 0) {
            return raw;
        }
        StringBuilder out = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == '+') {
                out.append(' ');
            } else if (c == '%' && i + 2 < raw.length()) {
                try {
                    out.append((char) Integer.parseInt(
                            raw.substring(i + 1, i + 3), 16));
                    i += 2;
                } catch (NumberFormatException error) {
                    out.append(c);
                }
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }
}
