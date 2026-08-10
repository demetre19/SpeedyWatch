package com.speedywatch.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class YouTubeSubsDialogTest {
    @Test
    public void scaledSummaryTextSize_followsPinchWithinReadableBounds() {
        assertEquals(22.5f, SummaryTextZoom.scale(15f, 1.5f), 0.001f);
        assertEquals(15f, SummaryTextZoom.scale(16f, 0.5f), 0.001f);
        assertEquals(30f, SummaryTextZoom.scale(24f, 2f), 0.001f);
    }

    @Test
    public void scaledSummaryTextSize_rejectsInvalidGestureValues() {
        assertEquals(15f, SummaryTextZoom.scale(18f, 0f), 0.001f);
        assertEquals(15f, SummaryTextZoom.scale(Float.NaN, 1f), 0.001f);
        assertEquals(15f, SummaryTextZoom.scale(18f, Float.POSITIVE_INFINITY), 0.001f);
    }
}
