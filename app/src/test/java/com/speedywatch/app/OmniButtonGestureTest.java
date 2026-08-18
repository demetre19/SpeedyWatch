package com.speedywatch.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OmniButtonGestureTest {
    @Test
    public void horizontalSwipes_followRequestedChapterDirection() {
        assertEquals(
                OmniButtonGesture.Action.NEXT_CHAPTER,
                OmniButtonGesture.action(18f, 2f, 16f)
        );
        assertEquals(
                OmniButtonGesture.Action.PREVIOUS_CHAPTER,
                OmniButtonGesture.action(-18f, -2f, 16f)
        );
    }

    @Test
    public void verticalSwipes_openRequestedYouTubeDestinations() {
        assertEquals(
                OmniButtonGesture.Action.YOUTUBE_HISTORY,
                OmniButtonGesture.action(1f, -18f, 16f)
        );
        assertEquals(
                OmniButtonGesture.Action.WATCH_LATER,
                OmniButtonGesture.action(-1f, 18f, 16f)
        );
    }

    @Test
    public void shortOrInvalidMovement_hasNoAction() {
        assertEquals(
                OmniButtonGesture.Action.NONE,
                OmniButtonGesture.action(8f, 8f, 16f)
        );
        assertEquals(
                OmniButtonGesture.Action.NONE,
                OmniButtonGesture.action(Float.NaN, 20f, 16f)
        );
    }

    @Test
    public void tripleTap_requiresThreeConsecutiveTapsWithinGap() {
        assertEquals(1, OmniButtonGesture.nextTapCount(0, -1L, 1_000L, 300L));
        assertEquals(2, OmniButtonGesture.nextTapCount(1, 1_000L, 1_250L, 300L));
        assertEquals(3, OmniButtonGesture.nextTapCount(2, 1_250L, 1_500L, 300L));
        assertEquals(1, OmniButtonGesture.nextTapCount(2, 1_250L, 1_600L, 300L));
        assertEquals(1, OmniButtonGesture.nextTapCount(2, 1_500L, 1_400L, 300L));
    }
}
