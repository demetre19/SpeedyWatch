package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class SupportedSiteTest {
    @Test
    public void inAppNavigation_acceptsOnlyFirstPartyHttpsPages() {
        assertTrue(SupportedSite.isInAppNavigationUrl("https://www.bilibili.com/video/BV1PE411u7ox/"));
        assertTrue(SupportedSite.isInAppNavigationUrl("https://player.vimeo.com/video/980152407"));
        assertTrue(SupportedSite.isInAppNavigationUrl("https://x.com/example/status/1234567890"));
        assertTrue(SupportedSite.isInAppNavigationUrl("https://www.facebook.com/example/videos/1234567890"));
        assertTrue(SupportedSite.isInAppNavigationUrl(
                "https://soundcloud.com/example-artist/example-track"
        ));
        assertTrue(SupportedSite.isInAppNavigationUrl(
                "https://www.bilibili.tv/en/video/4800271834815488"
        ));
        assertTrue(SupportedSite.isInAppNavigationUrl(
                "https://mega.nz/folder/AbCdEf12#AbCdEfGhIjKlMnOpQrStUv"
        ));
        assertFalse(SupportedSite.isInAppNavigationUrl("https://mega.nz/"));

        assertFalse(SupportedSite.isInAppNavigationUrl("http://vimeo.com/76979871"));
        assertFalse(SupportedSite.isInAppNavigationUrl("https://cdninstagram.com/video.mp4"));
        assertFalse(SupportedSite.isInAppNavigationUrl("https://googlevideo.com/videoplayback"));
        assertFalse(SupportedSite.isInAppNavigationUrl(
                "https://cf-media.sndcdn.com/audio.mp3"
        ));
        assertFalse(SupportedSite.isInAppNavigationUrl("https://example.com/video/123"));
        assertFalse(SupportedSite.isInAppNavigationUrl(
                "https://www.loom.com/share/40d92b478f4e4381a25d32da4709c68b"
        ));
        assertFalse(SupportedSite.isInAppNavigationUrl(
                "https://mega.nz/folder/AbCdEf12"
        ));
        assertFalse(SupportedSite.isInAppNavigationUrl(
                "https://mega.nz.evil.example/folder/AbCdEf12#AbCdEfGhIjKlMnOpQrStUv"
        ));
        assertFalse(SupportedSite.isInAppNavigationUrl("https://mega.nz/account"));
    }

    @Test
    public void downloadValidation_acceptsMediaPagesAndRejectsBrowsePages() {
        assertTrue(SupportedSite.isSupportedDownloadUrl("https://www.bilibili.com/video/BV1PE411u7ox/"));
        assertTrue(SupportedSite.isSupportedDownloadUrl("https://www.instagram.com/reel/C0example_1/"));
        assertTrue(SupportedSite.isSupportedDownloadUrl("https://player.vimeo.com/video/980152407"));
        assertTrue(SupportedSite.isSupportedDownloadUrl("https://x.com/example/status/1234567890"));
        assertTrue(SupportedSite.isSupportedDownloadUrl("https://www.facebook.com/watch/?v=1234567890"));
        assertTrue(SupportedSite.isSupportedDownloadUrl(
                "https://www.bilibili.tv/en/video/4800271834815488"
        ));
        assertTrue(SupportedSite.isSupportedDownloadUrl(
                "https://www.loom.com/share/40d92b478f4e4381a25d32da4709c68b"
                        + "?requester_email=mitko19%40gmail.com"
        ));
        assertTrue(SupportedSite.isSupportedDownloadUrl(
                "https://soundcloud.com/example-artist/example-track"
        ));
        assertTrue(SupportedSite.isSupportedDownloadUrl(
                "https://on.soundcloud.com/Example123"
        ));

        assertFalse(SupportedSite.isSupportedDownloadUrl("https://www.bilibili.com/"));
        assertFalse(SupportedSite.isSupportedDownloadUrl("https://vimeo.com/search?q=captions"));
        assertFalse(SupportedSite.isSupportedDownloadUrl("https://x.com/search?q=video"));
        assertFalse(SupportedSite.isSupportedDownloadUrl("https://vimeocdn.com/segment.mp4"));
        assertFalse(SupportedSite.isSupportedDownloadUrl("https://www.loom.com/"));
        assertFalse(SupportedSite.isSupportedDownloadUrl(
                "https://soundcloud.com/example-artist/tracks"
        ));
        assertFalse(SupportedSite.isSupportedDownloadUrl(
                "https://soundcloud.com/search?q=example"
        ));
        assertFalse(SupportedSite.isSupportedDownloadUrl(
                "https://cdn.loom.com/assets/video.mp4"
        ));
        assertFalse(SupportedSite.isSupportedDownloadUrl(
                "https://mega.nz/folder/AbCdEf12#AbCdEfGhIjKlMnOpQrStUv"
        ));
    }

    @Test
    public void sharedTextExtraction_requiresAnExactSupportedMediaUrl() {
        assertEquals(
                "https://player.vimeo.com/video/980152407",
                SupportedSite.downloadUrlFromText("Watch: https://player.vimeo.com/video/980152407).")
        );
        assertNull(SupportedSite.downloadUrlFromText("Browse https://vimeo.com/search?q=captions"));
        assertNull(SupportedSite.downloadUrlFromText("Internal https://127.0.0.1/video/123"));
        assertNull(SupportedSite.downloadUrlFromText("Credentials https://user:pass@vimeo.com/123"));
    }

    @Test
    public void pickerContainsOnlyInAppServices() {
        SupportedSite[] sites = SupportedSite.browsableValues();
        assertEquals(8, sites.length);
        assertEquals(SupportedSite.YOUTUBE, sites[0]);
        assertEquals(SupportedSite.BILIBILI, sites[1]);
        assertEquals(SupportedSite.FACEBOOK, sites[5]);
        assertEquals(SupportedSite.SOUNDCLOUD, sites[6]);
        assertEquals(SupportedSite.MEGA, sites[7]);
        assertFalse(SupportedSite.MEGA.supportsKeywordSearch());
        assertNull(SupportedSite.MEGA.searchUrl("anything"));
    }

    @Test
    public void megaPublicLinks_preserveOnlyBoundedAccessFragments() {
        String link = "https://mega.nz/folder/AbCdEf12#AbCdEfGhIjKlMnOpQrStUv";

        assertEquals(link, SupportedSite.validatedHttpsUrl(link));
        assertEquals(link, SupportedSite.supportedUrlFromText("Open " + link));
        assertNull(SupportedSite.supportedUrlFromText("https://mega.nz/"));
        String fileLink = "https://mega.nz/file/ZyXwVu12"
                + "#ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghi12345678";
        assertEquals(fileLink, SupportedSite.validatedHttpsUrl(fileLink));
        assertEquals(fileLink, SupportedSite.supportedUrlFromText(fileLink));
        assertNull(SupportedSite.validatedHttpsUrl(
                "https://mega.nz/file/ZyXwVu12"
                        + "#ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghi1234567"
        ));
        assertNull(SupportedSite.validatedHttpsUrl(
                "https://mega.nz/folder/AbCdEf12#too-short"
        ));
        assertNull(SupportedSite.validatedHttpsUrl(
                "https://mega.nz/#AbCdEfGhIjKlMnOpQrStUv"
        ));
        assertEquals(
                "https://vimeo.com/980152407",
                SupportedSite.validatedHttpsUrl("https://vimeo.com/980152407#discarded")
        );
    }

    @Test
    public void serviceFoldersFollowTheDownloadUrlAndAvailableMetadata() {
        assertEquals(
                "Download/SpeedyWatch/YouTube/audio/Example Channel",
                SpeedyWatchDownloadService.relativeDownloadPath(
                        "https://youtu.be/dQw4w9WgXcQ",
                        SpeedyWatchDownloadService.KIND_MP3,
                        "Example Channel"
                )
        );
        assertEquals(
                "Download/SpeedyWatch/BiliBili/Presenter",
                SpeedyWatchDownloadService.relativeDownloadPath(
                        "https://www.bilibili.tv/en/video/4800271834815488",
                        SpeedyWatchDownloadService.KIND_MP4,
                        "Presenter"
                )
        );
        assertEquals(
                "Download/SpeedyWatch/Instagram",
                SpeedyWatchDownloadService.relativeDownloadPath(
                        "https://www.instagram.com/reel/C0example_1/",
                        SpeedyWatchDownloadService.KIND_MP4,
                        null
                )
        );
        assertEquals(
                "Download/SpeedyWatch/Loom",
                SpeedyWatchDownloadService.relativeDownloadPath(
                        "https://www.loom.com/share/40d92b478f4e4381a25d32da4709c68b",
                        SpeedyWatchDownloadService.KIND_MP4,
                        null
                )
        );
        assertEquals(
                "Download/SpeedyWatch/SoundCloud/Example Artist",
                SpeedyWatchDownloadService.relativeDownloadPath(
                        "https://soundcloud.com/example-artist/example-track",
                        SpeedyWatchDownloadService.KIND_MP3,
                        "Example Artist"
                )
        );
    }

    @Test
    public void cookiesRemainScopedToTheirActualServiceDomain() {
        assertEquals(".vimeo.com", SupportedSite.cookieDomainForUrl(
                "https://vimeo.com/980152407"
        ));
        assertEquals(".vimeo.com", SupportedSite.cookieDomainForUrl(
                "https://player.vimeo.com/video/980152407"
        ));
        assertEquals(".vimeocdn.com", SupportedSite.cookieDomainForUrl(
                "https://vod.vimeocdn.com/segment.mp4"
        ));
        assertEquals("", SupportedSite.cookieDomainForUrl("https://example.com/video/123"));
    }
}
