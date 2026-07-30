package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.yausername.youtubedl_android.YoutubeDLRequest;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

public final class MediaDownloadEngineTest {
    private static final String VIMEO_PAGE = "https://vimeo.com/980152407";
    private static final String VIMEO_MANIFEST =
            "https://vod.vimeocdn.com/video/master.m3u8?token=signed";

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void authenticatedVimeoCookiesCoverPageAndCapturedCdnDomains() throws Exception {
        File directory = temporaryFolder.newFolder("vimeo-cookies");

        File cookies = MediaDownloadEngine.writeSessionCookies(
                directory,
                VIMEO_PAGE,
                "page_session=logged-in",
                VIMEO_MANIFEST,
                "cdn_session=signed"
        );

        assertNotNull(cookies);
        String contents = new String(
                Files.readAllBytes(cookies.toPath()),
                StandardCharsets.UTF_8
        );
        assertTrue(contents.contains(
                ".vimeo.com\tTRUE\t/\tTRUE\t0\tpage_session\tlogged-in"
        ));
        assertTrue(contents.contains(
                ".vimeocdn.com\tTRUE\t/\tTRUE\t0\tcdn_session\tsigned"
        ));
    }

    @Test
    public void capturedCookiesCannotCrossServiceBoundaries() throws Exception {
        File directory = temporaryFolder.newFolder("cross-service-cookies");

        File cookies = MediaDownloadEngine.writeSessionCookies(
                directory,
                VIMEO_PAGE,
                "page_session=logged-in",
                "https://x.com/example/status/1234567890",
                "x_session=must-not-leak"
        );

        assertNotNull(cookies);
        String contents = new String(
                Files.readAllBytes(cookies.toPath()),
                StandardCharsets.UTF_8
        );
        assertTrue(contents.contains("page_session\tlogged-in"));
        assertFalse(contents.contains("x_session"));
        assertFalse(contents.contains("x.com"));
    }

    @Test
    public void VimeoTriesCanonicalPageWithCapturedContextBeforeFallbacks() {
        assertEquals(
                List.of(
                        SpeedyWatchDownloadService.AttemptSource.PAGE_WITH_CAPTURED_CONTEXT,
                        SpeedyWatchDownloadService.AttemptSource.CAPTURED_MEDIA,
                        SpeedyWatchDownloadService.AttemptSource.PAGE
                ),
                SpeedyWatchDownloadService.attemptSequence(VIMEO_PAGE, true)
        );
        assertEquals(
                VIMEO_MANIFEST,
                SpeedyWatchDownloadService.targetUrlForAttempt(
                        VIMEO_PAGE,
                        VIMEO_MANIFEST,
                        SpeedyWatchDownloadService.AttemptSource.CAPTURED_MEDIA
                )
        );
        assertEquals(
                VIMEO_PAGE,
                SpeedyWatchDownloadService.targetUrlForAttempt(
                        VIMEO_PAGE,
                        VIMEO_MANIFEST,
                        SpeedyWatchDownloadService.AttemptSource.PAGE_WITH_CAPTURED_CONTEXT
                )
        );
        assertEquals(
                VIMEO_PAGE,
                SpeedyWatchDownloadService.targetUrlForAttempt(
                        VIMEO_PAGE,
                        VIMEO_MANIFEST,
                        SpeedyWatchDownloadService.AttemptSource.PAGE
                )
        );
        assertEquals(
                List.of(
                        SpeedyWatchDownloadService.AttemptSource.CAPTURED_MEDIA,
                        SpeedyWatchDownloadService.AttemptSource.PAGE
                ),
                SpeedyWatchDownloadService.attemptSequence(
                        "https://x.com/example/status/1234567890",
                        true
                )
        );
    }

    @Test
    public void capturedVimeoFfmpegCommandPreservesBrowserContextAndOutputKind()
            throws Exception {
        File mp3 = new File(temporaryFolder.newFolder("vimeo-ffmpeg"), "source.mp3");
        List<String> command = SpeedyWatchDownloadService.buildCapturedFfmpegCommand(
                "/native/libffmpeg.so",
                VIMEO_MANIFEST,
                SpeedyWatchDownloadService.KIND_MP3,
                SpeedyWatchSettings.MP3_QUALITY_STANDARD,
                "SpeedyWatch test",
                "https://player.vimeo.com/video/980152407",
                "https://player.vimeo.com",
                "cdn_session=authenticated",
                "Bearer captured-token",
                mp3
        );

        assertEquals("/native/libffmpeg.so", command.get(0));
        assertEquals(mp3.getAbsolutePath(), command.get(command.size() - 1));
        assertTrue(command.contains(VIMEO_MANIFEST));
        assertTrue(command.contains("libmp3lame"));
        assertTrue(command.contains("128K"));
        int headersIndex = command.indexOf("-headers");
        assertTrue(headersIndex >= 0);
        String headers = command.get(headersIndex + 1);
        assertTrue(headers.contains("Referer: https://player.vimeo.com/video/980152407"));
        assertTrue(headers.contains("Origin: https://player.vimeo.com"));
        assertTrue(headers.contains("Cookie: cdn_session=authenticated"));
        assertTrue(headers.contains("Authorization: Bearer captured-token"));
    }

    @Test
    public void capturedVimeoUsesOnlyDownloaderVerifiedTitle() throws Exception {
        File directory = temporaryFolder.newFolder("captured-title");
        MediaDownloadEngine.DownloadedMedia media =
                SpeedyWatchDownloadService.resolvedDownloadedMedia(
                        directory,
                        "Verified Vimeo Title",
                        true,
                        true
                );

        assertEquals("Verified Vimeo Title", media.title);
    }

    @Test(expected = IOException.class)
    public void capturedVimeoRejectsUnverifiedDialogTitle() throws Exception {
        SpeedyWatchDownloadService.resolvedDownloadedMedia(
                temporaryFolder.newFolder("unverified-title"),
                "Video",
                true,
                false
        );
    }

    @Test
    public void downloadRequestEndsWithCanonicalVimeoPage() throws Exception {
        File directory = temporaryFolder.newFolder("vimeo-request");
        YoutubeDLRequest request = SpeedyWatchDownloadService.buildRequest(
                VIMEO_PAGE,
                SpeedyWatchDownloadService.KIND_MP4,
                720,
                SpeedyWatchSettings.MP3_QUALITY_STANDARD,
                "SpeedyWatch test",
                VIMEO_PAGE,
                null,
                directory
        );

        List<String> command = request.buildCommand();
        assertEquals(VIMEO_PAGE, command.get(command.size() - 1));
    }

    @Test
    public void mp4SelectorAcceptsHeightBoundedAacAudioWithoutM4aContainer()
            throws Exception {
        File directory = temporaryFolder.newFolder("vimeo-hls-request");
        YoutubeDLRequest request = SpeedyWatchDownloadService.buildRequest(
                VIMEO_PAGE,
                SpeedyWatchDownloadService.KIND_MP4,
                720,
                SpeedyWatchSettings.MP3_QUALITY_STANDARD,
                "SpeedyWatch test",
                VIMEO_PAGE,
                null,
                directory
        );

        List<String> command = request.buildCommand();
        int formatOption = command.indexOf("-f");
        assertTrue(formatOption >= 0);
        String selector = command.get(formatOption + 1);
        assertTrue(selector.contains(
                "bestvideo[height<=720][ext=mp4][vcodec^=avc1]"
                        + "+bestaudio[acodec^=mp4a]"
        ));
        assertFalse(selector.contains("bestvideo[ext=mp4]"));
        assertFalse(selector.contains("/best[ext=mp4]"));
    }
}
