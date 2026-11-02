package com.speedywatch.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class LinkCleanerTest {

    @Test
    public void clean_stripsAllParamsFromSocialPostHosts() {
        assertEquals(
                "https://x.com/user/status/1234567890",
                LinkCleaner.clean("https://x.com/user/status/1234567890?s=20&t=abc123&utm_source=share")
        );
        assertEquals(
                "https://twitter.com/user/status/99",
                LinkCleaner.clean("https://twitter.com/user/status/99?s=48&fbclid=IwAR123")
        );
        assertEquals(
                "https://www.instagram.com/p/AbC123/",
                LinkCleaner.clean("https://www.instagram.com/p/AbC123/?igsh=MTEx&utm_source=ig_embed")
        );
        assertEquals(
                "https://www.tiktok.com/@user/video/7123456789",
                LinkCleaner.clean("https://www.tiktok.com/@user/video/7123456789?share_app_id=123&share_link_id=abc")
        );
        assertEquals(
                "https://www.reddit.com/r/videos/comments/abc123/title/",
                LinkCleaner.clean("https://www.reddit.com/r/videos/comments/abc123/title/?share_id=xyz&utm_medium=android_app")
        );
    }

    @Test
    public void clean_keepsFunctionalParamsOnVideoHosts() {
        assertEquals(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=123",
                LinkCleaner.clean(
                        "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=123&si=tracker&feature=share&utm_source=x"
                )
        );
        assertEquals(
                "https://youtu.be/dQw4w9WgXcQ?t=42",
                LinkCleaner.clean("https://youtu.be/dQw4w9WgXcQ?si=abc&t=42")
        );
        assertEquals(
                "https://www.bilibili.com/video/BV1abc?p=2",
                LinkCleaner.clean("https://www.bilibili.com/video/BV1abc?p=2&spm_id_from=444.4&vd_source=tracker")
        );
        assertEquals(
                "https://soundcloud.com/artist/track",
                LinkCleaner.clean("https://soundcloud.com/artist/track?utm_source=clipboard&si=abc")
        );
    }

    @Test
    public void clean_stripsKnownTrackersEverywhereElse() {
        assertEquals(
                "https://example.com/article?id=42",
                LinkCleaner.clean("https://example.com/article?id=42&utm_campaign=spring&fbclid=IwAR99&gclid=zzz")
        );
        assertEquals(
                "https://example.com/page",
                LinkCleaner.clean("https://example.com/page?utm_source=x&utm_medium=y")
        );
    }

    @Test
    public void clean_preservesFragmentsAndUntouchedUrls() {
        assertEquals(
                "https://example.com/doc#section",
                LinkCleaner.clean("https://example.com/doc?utm_source=x#section")
        );
        assertEquals(
                "https://example.com/clean",
                LinkCleaner.clean("https://example.com/clean")
        );
        assertEquals(
                "not a url",
                LinkCleaner.clean("not a url")
        );
        assertEquals(
                "ftp://example.com/file?x=1",
                LinkCleaner.clean("ftp://example.com/file?x=1")
        );
        assertEquals(null, LinkCleaner.clean(null));
        assertEquals("", LinkCleaner.clean(""));
    }

    @Test
    public void clean_keepsParameterEncodingForRetainedPairs() {
        assertEquals(
                "https://example.com/a?title=a%2Fb&keepme=yes",
                LinkCleaner.clean("https://example.com/a?title=a%2Fb&utm_source=x&keepme=yes")
        );
    }
}
