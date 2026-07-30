package com.speedywatch.app;

import java.io.UnsupportedEncodingException;
import java.net.IDN;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLEncoder;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** HTTPS media sites exposed by the native site switcher. */
enum SupportedSite {
    YOUTUBE(
            R.drawable.ic_site_youtube,
            "YouTube",
            "https://www.youtube.com/",
            "https://www.youtube.com/results?search_query="
    ),
    BILIBILI(
            R.drawable.ic_site_bilibili,
            "BiliBili",
            "https://www.bilibili.tv/en",
            "https://www.bilibili.tv/en/search-result?q="
    ),
    INSTAGRAM(
            R.drawable.ic_site_instagram,
            "Instagram",
            "https://www.instagram.com/",
            "https://www.instagram.com/explore/search/keyword/?q="
    ),
    VIMEO(
            R.drawable.ic_site_vimeo,
            "Vimeo",
            "https://vimeo.com/",
            "https://vimeo.com/search?q="
    ),
    X(
            R.drawable.ic_site_x,
            "X",
            "https://x.com/",
            "https://x.com/search?src=typed_query&q="
    ),
    FACEBOOK(
            R.drawable.ic_site_facebook,
            "Facebook",
            "https://www.facebook.com/",
            "https://www.facebook.com/search/videos/?q="
    ),
    MEGA(
            R.drawable.ic_site_mega,
            "MEGA",
            "https://mega.nz/",
            null
    ),
    LOOM(0, "Loom", null, null);

    private static final int MAX_URL_LENGTH = 8_192;
    private static final Pattern HTTPS_URL = Pattern.compile(
            "https://[^\\s<>\\\"']+",
            Pattern.CASE_INSENSITIVE
    );
    private static final SupportedSite[] BROWSABLE_SITES = {
            YOUTUBE,
            BILIBILI,
            INSTAGRAM,
            VIMEO,
            X,
            FACEBOOK,
            MEGA
    };
    private static final Pattern IPV4 = Pattern.compile("(?:\\d{1,3}\\.){3}\\d{1,3}");
    private static final Pattern BILIBILI_VIDEO_PATH = Pattern.compile(
            "^/(?:video/(?:BV[A-Za-z0-9]+|av\\d+)|bangumi/play/(?:ep|ss)\\d+"
                    + "|en/(?:video|play)/\\d+)/?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern INSTAGRAM_VIDEO_PATH = Pattern.compile(
            "^/(?:p|reel|reels|tv)/[A-Za-z0-9_-]+/?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern VIMEO_VIDEO_PATH = Pattern.compile(
            "^/(?:.*?/)?\\d+/?$"
    );
    private static final Pattern X_STATUS_PATH = Pattern.compile(
            "^/[^/]+/status/\\d+/?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern FACEBOOK_VIDEO_PATH = Pattern.compile(
            "^/(?:reel/\\d+|share/(?:v|r)/[A-Za-z0-9_-]+|.*?/videos/\\d+)/?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern LOOM_SHARE_PATH = Pattern.compile(
            "^/(?:share|embed)/[a-f0-9]{32}/?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MEGA_PUBLIC_PATH = Pattern.compile(
            "^/(folder|file)/[A-Za-z0-9_-]{8}/?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MEGA_FOLDER_FRAGMENT = Pattern.compile(
            "^[A-Za-z0-9_-]{22}(?:/(?:folder|file)/[A-Za-z0-9_-]{8})?$",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern MEGA_FILE_FRAGMENT = Pattern.compile(
            "^[A-Za-z0-9_-]{43}$"
    );

    final int iconResource;
    final String label;
    final String homeUrl;
    private final String searchPrefix;

    SupportedSite(
            int iconResource,
            String label,
            String homeUrl,
            String searchPrefix
    ) {
        this.iconResource = iconResource;
        this.label = label;
        this.homeUrl = homeUrl;
        this.searchPrefix = searchPrefix;
    }

    boolean supportsKeywordSearch() {
        return searchPrefix != null;
    }

    String searchUrl(String query) {
        if (!supportsKeywordSearch() || query == null || query.trim().isEmpty()) {
            return null;
        }
        try {
            return searchPrefix + URLEncoder.encode(query.trim(), "UTF-8");
        } catch (UnsupportedEncodingException impossible) {
            throw new AssertionError(impossible);
        }
    }

    static SupportedSite[] browsableValues() {
        return BROWSABLE_SITES.clone();
    }
    static String cookieDomainForUrl(String value) {
        String valid = validatedHttpsUrl(value);
        SupportedSite site = forUrl(valid);
        if (site == null) {
            return "";
        }
        String host = URI.create(valid).getHost().toLowerCase(Locale.US);
        switch (site) {
            case YOUTUBE:
                return cookieDomainForHost(
                        host,
                        "youtube.com",
                        "youtube-nocookie.com",
                        "youtu.be",
                        "googlevideo.com",
                        "ytimg.com"
                );
            case BILIBILI:
                return cookieDomainForHost(
                        host,
                        "bilibili.com",
                        "bilibili.tv",
                        "bilivideo.com",
                        "b23.tv"
                );
            case INSTAGRAM:
                return cookieDomainForHost(host, "instagram.com", "cdninstagram.com");
            case VIMEO:
                String vimeoDomain = cookieDomainForHost(host, "vimeo.com", "vimeocdn.com");
                return "vimeo.com".equals(vimeoDomain) ? ".vimeo.com" : vimeoDomain;
            case X:
                return cookieDomainForHost(host, "x.com", "twitter.com", "twimg.com");
            case FACEBOOK:
                return cookieDomainForHost(host, "facebook.com", "fb.watch", "fbcdn.net");
            case MEGA:
                return cookieDomainForHost(host, "mega.nz");
            case LOOM:
                return cookieDomainForHost(host, "loom.com");
            default:
                return "";
        }
    }



    boolean ownsHost(String host) {
        if (host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.US);
        switch (this) {
            case YOUTUBE:
                return domainMatches(normalized, "youtube.com")
                        || "youtu.be".equals(normalized)
                        || domainMatches(normalized, "youtube-nocookie.com")
                        || domainMatches(normalized, "googlevideo.com")
                        || domainMatches(normalized, "ytimg.com");
            case BILIBILI:
                return domainMatches(normalized, "bilibili.com")
                        || domainMatches(normalized, "bilibili.tv")
                        || domainMatches(normalized, "bilivideo.com")
                        || domainMatches(normalized, "bstarstatic.com")
                        || domainMatches(normalized, "b23.tv");
            case INSTAGRAM:
                return domainMatches(normalized, "instagram.com")
                        || domainMatches(normalized, "cdninstagram.com");
            case VIMEO:
                return domainMatches(normalized, "vimeo.com")
                        || domainMatches(normalized, "vimeocdn.com");
            case X:
                return domainMatches(normalized, "x.com")
                        || domainMatches(normalized, "twitter.com")
                        || domainMatches(normalized, "twimg.com");
            case FACEBOOK:
                return domainMatches(normalized, "facebook.com")
                        || domainMatches(normalized, "fb.watch")
                        || domainMatches(normalized, "fbcdn.net");
            case MEGA:
                return "mega.nz".equals(normalized);
            case LOOM:
                return domainMatches(normalized, "loom.com");
            default:
                return false;
        }
    }

    static SupportedSite forUrl(String value) {
        String valid = validatedHttpsUrl(value);
        if (valid == null) {
            return null;
        }
        String host = URI.create(valid).getHost();
        for (SupportedSite site : values()) {
            if (site.ownsHost(host)) {
                return site;
            }
        }
        return null;
    }
    static boolean isInAppNavigationUrl(String value) {
        String valid = validatedHttpsUrl(value);
        SupportedSite site = forUrl(valid);
        if (site == null) {
            return false;
        }
        URI uri = URI.create(valid);
        String host = uri.getHost().toLowerCase(Locale.US);
        switch (site) {
            case YOUTUBE:
                return domainMatches(host, "youtube.com")
                        || "youtu.be".equals(host)
                        || domainMatches(host, "youtube-nocookie.com");
            case BILIBILI:
                return domainMatches(host, "bilibili.com")
                        || domainMatches(host, "bilibili.tv")
                        || "b23.tv".equals(host);
            case INSTAGRAM:
                return domainMatches(host, "instagram.com");
            case VIMEO:
                return domainMatches(host, "vimeo.com");
            case X:
                return domainMatches(host, "x.com") || domainMatches(host, "twitter.com");
            case FACEBOOK:
                return domainMatches(host, "facebook.com") || "fb.watch".equals(host);
            case MEGA:
                return isMegaPublicLink(valid);
            case LOOM:
            default:
                return false;
        }
    }

    static boolean isSupportedDownloadUrl(String value) {
        if (YouTubeUrls.canonicalVideoUrl(value) != null) {
            return true;
        }
        String valid = validatedHttpsUrl(value);
        SupportedSite site = forUrl(valid);
        if (site == null) {
            return false;
        }
        URI uri = URI.create(valid);
        String host = uri.getHost().toLowerCase(Locale.US);
        String path = uri.getPath() == null ? "" : uri.getPath();
        switch (site) {
            case BILIBILI:
                return ("b23.tv".equals(host) && path.matches("^/[A-Za-z0-9]+/?$"))
                        || ((domainMatches(host, "bilibili.com")
                        || domainMatches(host, "bilibili.tv"))
                        && BILIBILI_VIDEO_PATH.matcher(path).matches());
            case INSTAGRAM:
                return domainMatches(host, "instagram.com")
                        && INSTAGRAM_VIDEO_PATH.matcher(path).matches();
            case VIMEO:
                return domainMatches(host, "vimeo.com")
                        && VIMEO_VIDEO_PATH.matcher(path).matches();
            case X:
                return (domainMatches(host, "x.com") || domainMatches(host, "twitter.com"))
                        && X_STATUS_PATH.matcher(path).matches();
            case FACEBOOK:
                if ("fb.watch".equals(host)) {
                    return path.matches("^/[A-Za-z0-9_-]+/?$");
                }
                if (!domainMatches(host, "facebook.com")) {
                    return false;
                }
                String query = uri.getRawQuery();
                return FACEBOOK_VIDEO_PATH.matcher(path).matches()
                        || ("/watch/".equals(path)
                        && query != null
                        && query.matches("(?:^|.*&)v=\\d+(?:&.*|$)"));
            case LOOM:
                return domainMatches(host, "loom.com")
                        && LOOM_SHARE_PATH.matcher(path).matches();
            case YOUTUBE:
            default:
                return false;
        }
    }


    static String urlFromText(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String trimmed = value.trim();
        String direct = validatedHttpsUrl(trimmed);
        if (direct != null) {
            return direct;
        }
        Matcher matcher = HTTPS_URL.matcher(value);
        while (matcher.find()) {
            String candidate = trimTrailingPunctuation(matcher.group());
            String valid = validatedHttpsUrl(candidate);
            if (valid != null) {
                return valid;
            }
        }
        return null;
    }

    static String supportedUrlFromText(String value) {
        String valid = urlFromText(value);
        if (!isInAppNavigationUrl(valid)) {
            return null;
        }
        return forUrl(valid) != MEGA || isMegaPublicLink(valid) ? valid : null;
    }

    static String downloadUrlFromText(String value) {
        String valid = urlFromText(value);
        return isSupportedDownloadUrl(valid) ? valid : null;
    }



    static String validatedHttpsUrl(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim().replace("&amp;", "&");
        if (trimmed.isEmpty() || trimmed.length() > MAX_URL_LENGTH || containsControlCharacter(trimmed)) {
            return null;
        }
        String canonicalYouTube = YouTubeUrls.canonicalVideoUrl(trimmed);
        if (canonicalYouTube != null) {
            return canonicalYouTube;
        }
        try {
            URI uri = URI.create(trimmed);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getUserInfo() != null) {
                return null;
            }
            String host = IDN.toASCII(uri.getHost()).toLowerCase(Locale.US);
            if (isLocalOrPrivateHost(host)) {
                return null;
            }
            String fragment = null;
            if ("mega.nz".equals(host)) {
                String path = uri.getPath() == null ? "" : uri.getPath();
                Matcher publicLink = MEGA_PUBLIC_PATH.matcher(path);
                if (publicLink.matches()) {
                    String rawFragment = uri.getRawFragment();
                    boolean validFragment = rawFragment != null
                            && ("folder".equalsIgnoreCase(publicLink.group(1))
                            ? MEGA_FOLDER_FRAGMENT.matcher(rawFragment).matches()
                            : MEGA_FILE_FRAGMENT.matcher(rawFragment).matches());
                    if (!validFragment) {
                        return null;
                    }
                    fragment = rawFragment;
                } else if (uri.getRawFragment() != null) {
                    return null;
                }
            }
            URI normalized = new URI(
                    "https",
                    null,
                    host,
                    uri.getPort(),
                    uri.getRawPath(),
                    uri.getRawQuery(),
                    fragment
            );
            return normalized.toASCIIString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isMegaPublicLink(String value) {
        String valid = validatedHttpsUrl(value);
        if (valid == null) {
            return false;
        }
        URI uri = URI.create(valid);
        String path = uri.getPath() == null ? "" : uri.getPath();
        return "mega.nz".equalsIgnoreCase(uri.getHost())
                && uri.getRawFragment() != null
                && MEGA_PUBLIC_PATH.matcher(path).matches();
    }

    private static String cookieDomainForHost(String host, String... allowedDomains) {
        for (String domain : allowedDomains) {
            if (domain.equals(host)) {
                return domain;
            }
            if (host.endsWith("." + domain)) {
                return "." + domain;
            }
        }
        return "";
    }

    private static boolean domainMatches(String host, String domain) {
        return domain.equals(host) || host.endsWith("." + domain);
    }

    private static boolean containsControlCharacter(String value) {
        for (int i = 0; i < value.length(); i++) {
            if (Character.isISOControl(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isLocalOrPrivateHost(String host) {
        if ("localhost".equals(host)
                || host.endsWith(".localhost")
                || host.endsWith(".local")
                || host.endsWith(".internal")) {
            return true;
        }
        if (!IPV4.matcher(host).matches() && host.indexOf(':') < 0) {
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            return address.isAnyLocalAddress()
                    || address.isLoopbackAddress()
                    || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress()
                    || address.isMulticastAddress();
        } catch (Exception ignored) {
            return true;
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
