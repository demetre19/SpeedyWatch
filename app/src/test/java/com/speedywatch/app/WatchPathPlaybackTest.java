package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WatchPathPlaybackTest {
    private static final String SOURCE = "https://www.youtube.com/watch?v=dQw4w9WgXcQ";

    @Test
    public void playback_skipsInOrderAndCompletesAfterFinalSegment() {
        WatchPathPlayback playback = playback();

        assertSeek(playback.start(), 0);
        assertFalse(playback.onPlaybackTime(9.9).shouldSeek());
        assertSeek(playback.onPlaybackTime(10), 20);
        assertEquals(1, playback.segmentIndex());
        assertTrue(playback.canUndo());
        assertSeek(playback.onPlaybackTime(30), 40);

        WatchPathPlayback.Action completed = playback.onPlaybackTime(50);
        assertTrue(completed.completed);
        assertFalse(playback.isActive());
    }

    @Test
    public void undo_playsSkippedSectionAndResumesWithoutASecondSeek() {
        WatchPathPlayback playback = playback();
        playback.start();
        assertSeek(playback.onPlaybackTime(10), 20);

        assertSeek(playback.undo(), 10);
        assertTrue(playback.isReviewingSkippedSection());
        assertFalse(playback.canUndo());
        assertFalse(playback.onPlaybackTime(19.9).shouldSeek());
        assertFalse(playback.onPlaybackTime(20).shouldSeek());
        assertFalse(playback.isReviewingSkippedSection());
        assertEquals(1, playback.segmentIndex());
        assertSeek(playback.onPlaybackTime(30), 40);
    }

    @Test
    public void nextDuringUndoReview_resumesThePendingSelectedSegment() {
        WatchPathPlayback playback = playback();
        playback.start();
        playback.onPlaybackTime(10);
        playback.undo();

        assertSeek(playback.next(), 20);
        assertFalse(playback.isReviewingSkippedSection());
        assertEquals(1, playback.segmentIndex());
    }

    @Test
    public void previousAndNext_keepRouteNavigationBounded() {
        WatchPathPlayback playback = playback();
        assertSeek(playback.previous(), 0);
        assertSeek(playback.next(), 20);
        assertSeek(playback.previous(), 0);
        assertSeek(playback.next(), 20);
        assertSeek(playback.next(), 40);
        assertFalse(playback.next().shouldSeek());
        assertEquals(2, playback.segmentIndex());
    }

    private static WatchPathPlayback playback() {
        WatchPathPlan plan = WatchPathPlan.parse(
                "WATCHPATH 1\n"
                        + "SEGMENT | 0 | 10 | Intro | Context\n"
                        + "SEGMENT | 20 | 30 | Steps | Implementation\n"
                        + "SEGMENT | 40 | 50 | Result | Outcome",
                SOURCE,
                "Learn the implementation",
                5,
                60
        );
        return new WatchPathPlayback(plan);
    }

    private static void assertSeek(WatchPathPlayback.Action action, double seconds) {
        assertTrue(action.shouldSeek());
        assertEquals(seconds, action.seekSeconds, 0.001);
        assertFalse(action.completed);
    }
}
