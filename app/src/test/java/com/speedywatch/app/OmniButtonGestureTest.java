package com.speedywatch.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class OmniButtonGestureTest {
    @Test
    public void swipesResolveAllEightDirections() {
        assertEquals(
                OmniButtonGesture.Direction.RIGHT,
                OmniButtonGesture.direction(18f, 0f, 16f)
        );
        assertEquals(
                OmniButtonGesture.Direction.DOWN_RIGHT,
                OmniButtonGesture.direction(18f, 18f, 16f)
        );
        assertEquals(
                OmniButtonGesture.Direction.DOWN,
                OmniButtonGesture.direction(0f, 18f, 16f)
        );
        assertEquals(
                OmniButtonGesture.Direction.DOWN_LEFT,
                OmniButtonGesture.direction(-18f, 18f, 16f)
        );
        assertEquals(
                OmniButtonGesture.Direction.LEFT,
                OmniButtonGesture.direction(-18f, 0f, 16f)
        );
        assertEquals(
                OmniButtonGesture.Direction.UP_LEFT,
                OmniButtonGesture.direction(-18f, -18f, 16f)
        );
        assertEquals(
                OmniButtonGesture.Direction.UP,
                OmniButtonGesture.direction(0f, -18f, 16f)
        );
        assertEquals(
                OmniButtonGesture.Direction.UP_RIGHT,
                OmniButtonGesture.direction(18f, -18f, 16f)
        );
    }

    @Test
    public void shortOrInvalidMovement_hasNoDirection() {
        assertEquals(
                OmniButtonGesture.Direction.NONE,
                OmniButtonGesture.direction(8f, 8f, 16f)
        );
        assertEquals(
                OmniButtonGesture.Direction.NONE,
                OmniButtonGesture.direction(Float.NaN, 20f, 16f)
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
