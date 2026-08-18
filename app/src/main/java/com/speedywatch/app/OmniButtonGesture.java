package com.speedywatch.app;

final class OmniButtonGesture {
    enum Action {
        NONE,
        PREVIOUS_CHAPTER,
        NEXT_CHAPTER,
        YOUTUBE_HISTORY,
        WATCH_LATER
    }

    private OmniButtonGesture() {
    }

    static Action action(float deltaX, float deltaY, float minimumDistance) {
        if (!Float.isFinite(deltaX)
                || !Float.isFinite(deltaY)
                || !Float.isFinite(minimumDistance)
                || minimumDistance <= 0f) {
            return Action.NONE;
        }
        float horizontal = Math.abs(deltaX);
        float vertical = Math.abs(deltaY);
        if (Math.max(horizontal, vertical) < minimumDistance) {
            return Action.NONE;
        }
        if (horizontal >= vertical) {
            return deltaX > 0f ? Action.NEXT_CHAPTER : Action.PREVIOUS_CHAPTER;
        }
        return deltaY < 0f ? Action.YOUTUBE_HISTORY : Action.WATCH_LATER;
    }

    static int nextTapCount(int currentCount, long previousTapAt, long tapAt, long maximumGap) {
        if (tapAt < 0L || maximumGap <= 0L) {
            return 1;
        }
        if (currentCount <= 0
                || previousTapAt < 0L
                || tapAt < previousTapAt
                || tapAt - previousTapAt > maximumGap) {
            return 1;
        }
        return Math.min(3, currentCount + 1);
    }
}
