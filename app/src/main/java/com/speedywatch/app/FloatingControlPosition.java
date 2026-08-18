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

    static int maximumCoordinate(
            int viewportSize,
            int trailingInset,
            int controlSize,
            int safeGap,
            int minimum
    ) {
        return Math.max(
                minimum,
                viewportSize - Math.max(0, trailingInset)
                        - Math.max(0, controlSize)
                        - Math.max(0, safeGap)
        );
    }
    static float expansionTranslation(
            int coordinate,
            int controlSize,
            float scale,
            int minimum,
            int maximum
    ) {
        if (!Float.isFinite(scale) || scale <= 1f || controlSize <= 0) {
            return 0f;
        }
        float expansion = controlSize * (scale - 1f) / 2f;
        float minimumTranslation = minimum - coordinate + expansion;
        float maximumTranslation = maximum - coordinate - expansion;
        return Math.max(minimumTranslation, Math.min(maximumTranslation, 0f));
    }


    static int clamp(int value, int minimum, int maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
