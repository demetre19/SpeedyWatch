package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

public final class CapturedMediaRequestTest {
    private static final String VIMEO_PAGE = "https://vimeo.com/76979871";

    @Test
    public void capturesAllowlistedHeadersFromOwnedManifest() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cookie", "vimeo=logged-in");
        headers.put("Referer", "https://player.vimeo.com/video/76979871");
        headers.put("Origin", "https://player.vimeo.com");
        headers.put("User-Agent", "SpeedyWatch test");
        headers.put("Authorization", "Bearer private-session");

        CapturedMediaRequest captured = CapturedMediaRequest.observe(
                VIMEO_PAGE,
                "https://vod.vimeocdn.com/video/master.m3u8?token=signed",
                "GET",
                headers,
                null
        );

        assertEquals("https://vod.vimeocdn.com/video/master.m3u8?token=signed", captured.mediaUrl);
        assertEquals("vimeo=logged-in", captured.cookieHeader);
        assertEquals("https://player.vimeo.com/video/76979871", captured.referer);
        assertEquals("https://player.vimeo.com", captured.origin);
        assertEquals("Bearer private-session", captured.authorization);
        assertTrue(captured.matches(VIMEO_PAGE));
    }

    @Test
    public void rejectsSegmentsAndCrossServiceResources() {
        CapturedMediaRequest baseline = CapturedMediaRequest.observe(
                VIMEO_PAGE,
                "https://vod.vimeocdn.com/video/master.m3u8",
                "GET",
                new HashMap<>(),
                null
        );

        assertSame(baseline, CapturedMediaRequest.observe(
                VIMEO_PAGE,
                "https://vod.vimeocdn.com/video/chunk-01.m4s",
                "GET",
                new HashMap<>(),
                baseline
        ));
        assertSame(baseline, CapturedMediaRequest.observe(
                VIMEO_PAGE,
                "https://video.twimg.com/video/example.mp4",
                "GET",
                new HashMap<>(),
                baseline
        ));
        assertNull(CapturedMediaRequest.observe(
                VIMEO_PAGE,
                "https://vod.vimeocdn.com/video/master.m3u8",
                "POST",
                new HashMap<>(),
                null
        ));
    }

    @Test
    public void keepsManifestInsteadOfLaterLowerQualityDirectFile() {
        CapturedMediaRequest manifest = CapturedMediaRequest.observe(
                VIMEO_PAGE,
                "https://vod.vimeocdn.com/video/master.m3u8",
                "GET",
                new HashMap<>(),
                null
        );

        CapturedMediaRequest observed = CapturedMediaRequest.observe(
                VIMEO_PAGE,
                "https://vod.vimeocdn.com/video/360.mp4",
                "GET",
                new HashMap<>(),
                manifest
        );

        assertSame(manifest, observed);
    }

    @Test
    public void acceptsInstagramMediaOnItsExplicitMetaCdn() {
        assertTrue(CapturedMediaRequest.isAllowedResource(
                "https://www.instagram.com/reel/ABC_123/",
                "https://video-lax3-1.xx.fbcdn.net/o1/v/example.mp4"
        ));
    }

    @Test
    public void dropsCrossServiceContextUrls() {
        assertNull(CapturedMediaRequest.validContextUrl(
                VIMEO_PAGE,
                "https://x.com/"
        ));
    }
}
