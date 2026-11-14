package com.speedywatch.app;

import android.graphics.Color;

/**
 * Central palette for the native chrome and dialogs. Dark is the original
 * palette; light inverts neutral grays and remaps the few named accent tints
 * while keeping brand and saturated colors unchanged. All literal colors in
 * the UI flow through {@link #rgb(int, int, int)} or the semantic accessors so
 * a theme switch only needs to flip {@link #light}.
 */
final class UiTheme {
    private static final int DARK_BACKGROUND = 0xFF0F0F0F;
    private static final int LIGHT_BACKGROUND = 0xFFFFFFFF;
    private static final int DARK_PANEL = 0xFF1E1E1E;
    private static final int LIGHT_PANEL = 0xFFF0F0F0;
    private static final int DARK_BUTTON = 0xFF303030;
    private static final int LIGHT_BUTTON = 0xFFE2E2E2;
    private static final int ACTIVE = 0xFFFF0033;
    private static final int DARK_MUTED = 0xFFB9B9B9;
    private static final int LIGHT_MUTED = 0xFF5C5C5C;

    private static volatile boolean light;

    private UiTheme() {
    }

    static boolean isLight() {
        return light;
    }

    static void setLight(boolean value) {
        light = value;
    }

    static int background() {
        return light ? LIGHT_BACKGROUND : DARK_BACKGROUND;
    }

    static int panel() {
        return light ? LIGHT_PANEL : DARK_PANEL;
    }

    static int button() {
        return light ? LIGHT_BUTTON : DARK_BUTTON;
    }

    static int active() {
        return ACTIVE;
    }

    static int muted() {
        return light ? LIGHT_MUTED : DARK_MUTED;
    }

    /** Primary readable text/icon color for the active theme. */
    static int white() {
        return light ? 0xFF1B1B1B : Color.WHITE;
    }

    /** Text drawn on accent or brand-colored fills; stays white in both themes. */
    static int onAccent() {
        return Color.WHITE;
    }

    /** Vector icon tint for icons drawn on neutral panels and buttons. */
    static int icon() {
        return white();
    }

    static int rgb(int red, int green, int blue) {
        return resolve(Color.rgb(red, green, blue));
    }

    static int argb(int alpha, int red, int green, int blue) {
        return resolve(Color.argb(alpha, red, green, blue));
    }

    /**
     * Resolves a dark-palette literal to the active theme. Neutral grays are
     * channel-inverted so existing border/hint/divider shades keep their
     * relative separation; saturated colors (brand red, success green, the
     * chat bubble dark red) are unchanged.
     */
    private static int resolve(int color) {
        if (!light) {
            return color;
        }
        Integer override = LIGHT_OVERRIDES.get(color);
        if (override != null) {
            return override;
        }
        int alpha = (color >>> 24) & 0xFF;
        int r = (color >>> 16) & 0xFF;
        int g = (color >>> 8) & 0xFF;
        int b = color & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        int min = Math.min(r, Math.min(g, b));
        if (max - min < 24) {
            return Color.argb(alpha, 255 - r, 255 - g, 255 - b);
        }
        return color;
    }

    private static final java.util.Map<Integer, Integer> LIGHT_OVERRIDES =
            new java.util.HashMap<>();

    static {
        LIGHT_OVERRIDES.put(DARK_BACKGROUND, LIGHT_BACKGROUND);
        LIGHT_OVERRIDES.put(DARK_PANEL, LIGHT_PANEL);
        LIGHT_OVERRIDES.put(DARK_BUTTON, LIGHT_BUTTON);
        LIGHT_OVERRIDES.put(0xFFB9B9B9, 0xFF5C5C5C);  // muted text
        LIGHT_OVERRIDES.put(0xFFB4B4B4, 0xFF5C5C5C);  // muted text (Settings)
        LIGHT_OVERRIDES.put(0xFF5AB4FF, 0xFF0D62BF);  // link blue
        LIGHT_OVERRIDES.put(0xFFE1E1E1, 0xFF333333);  // near-white body text
        LIGHT_OVERRIDES.put(0xFF482020, 0xFFF0D8DC);  // selected-row red tint
        LIGHT_OVERRIDES.put(0xFF3A121C, 0xFFF3D9DE);  // chat bubble fill
        LIGHT_OVERRIDES.put(0xFF602332, 0xFFE8B7C0);  // chat bubble stroke
        LIGHT_OVERRIDES.put(0xFF3A181F, 0xFFF0D8DC);  // transcript row highlight
        LIGHT_OVERRIDES.put(0xFF262626, 0xFFD2D2D2);  // selected speed button
    }
}
