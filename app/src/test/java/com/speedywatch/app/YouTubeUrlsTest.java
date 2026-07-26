package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class YouTubeUrlsTest {
    private static final String VIDEO_ID = "dQw4w9WgXcQ";
    private static final String CANONICAL_URL =
            "https://www.youtube.com/watch?v=" + VIDEO_ID;

    @Test
    public void canonicalVideoUrl_acceptsSupportedYouTubeShapes() {
        assertEquals(CANONICAL_URL, YouTubeUrls.canonicalVideoUrl(
                "https://youtu.be/" + VIDEO_ID + "?si=share"
        ));
        assertEquals(CANONICAL_URL, YouTubeUrls.canonicalVideoUrl(
                "https://m.youtube.com/shorts/" + VIDEO_ID
        ));
        assertEquals(CANONICAL_URL, YouTubeUrls.canonicalVideoUrl(
                "https://music.youtube.com/watch?v=" + VIDEO_ID
        ));
        assertEquals(CANONICAL_URL, YouTubeUrls.canonicalVideoUrl(
                "https://www.youtube-nocookie.com/embed/" + VIDEO_ID
        ));
        assertEquals(CANONICAL_URL, YouTubeUrls.canonicalVideoUrl(
                "https://www.youtube.com/attribution_link?u=%2Fwatch%3Fv%3D" + VIDEO_ID
        ));
    }

    @Test
    public void canonicalVideoUrlFromText_extractsSharedUrl() {
        assertEquals(CANONICAL_URL, YouTubeUrls.canonicalVideoUrlFromText(
                "Watch this: https://youtu.be/" + VIDEO_ID + ")."
        ));
    }

    @Test
    public void searchOrVideoUrl_routesKeywordsAndRejectsUnsupportedUrls() {
        assertEquals(
                "https://www.youtube.com/results?search_query=faster+playback",
                YouTubeUrls.searchOrVideoUrl("faster playback")
        );
        assertEquals(
                CANONICAL_URL,
                YouTubeUrls.searchOrVideoUrl("https://www.youtube.com/watch?v=" + VIDEO_ID)
        );
        assertNull(YouTubeUrls.searchOrVideoUrl("http://youtu.be/" + VIDEO_ID));
        assertNull(YouTubeUrls.searchOrVideoUrl("https://example.com/watch?v=" + VIDEO_ID));
    }
}
