package com.speedywatch.app;

final class SummaryTextZoom {
    static final float MIN_SP = 15f;
    static final float MAX_SP = 30f;

    private SummaryTextZoom() {
    }

    static float scale(float currentSizeSp, float scaleFactor) {
        if (!Float.isFinite(currentSizeSp)
                || !Float.isFinite(scaleFactor)
                || scaleFactor <= 0f) {
            return MIN_SP;
        }
        return Math.max(MIN_SP, Math.min(MAX_SP, currentSizeSp * scaleFactor));
    }
}
