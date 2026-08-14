package com.speedywatch.app;

final class FloatingControlPosition {
    private FloatingControlPosition() {
    }

    static int resolve(
            float fraction,
            int minimum,
            int maximum,
            int defaultCoordinate
    ) {
        if (maximum <= minimum) {
            return minimum;
        }
        if (!Float.isFinite(fraction) || fraction < 0f || fraction > 1f) {
            return clamp(defaultCoordinate, minimum, maximum);
        }
        return clamp(
                minimum + Math.round((maximum - minimum) * fraction),
                minimum,
                maximum
        );
    }

    static float fraction(int coordinate, int minimum, int maximum) {
        if (maximum <= minimum) {
            return 0f;
        }
        int bounded = clamp(coordinate, minimum, maximum);
        return (bounded - minimum) / (float) (maximum - minimum);
    }

    static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
