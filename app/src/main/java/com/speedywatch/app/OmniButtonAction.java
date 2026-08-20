package com.speedywatch.app;

import java.util.Locale;

enum OmniButtonAction {
    NONE("none", "No action", AmountType.NONE, Double.NaN),
    PREVIOUS_CHAPTER("previous_chapter", "Previous chapter", AmountType.NONE, Double.NaN),
    NEXT_CHAPTER("next_chapter", "Next chapter", AmountType.NONE, Double.NaN),
    BROWSER_BACK("browser_back", "Browser Back", AmountType.NONE, Double.NaN),
    BROWSER_FORWARD("browser_forward", "Browser Forward", AmountType.NONE, Double.NaN),
    YOUTUBE_HISTORY("youtube_history", "YouTube History", AmountType.NONE, Double.NaN),
    WATCH_LATER("watch_later", "Watch Later", AmountType.NONE, Double.NaN),
    SEARCH("search", "Search", AmountType.NONE, Double.NaN),
    CHOOSE_SITE("choose_site", "Choose site", AmountType.NONE, Double.NaN),
    RELOAD("reload", "Reload page", AmountType.NONE, Double.NaN),
    SITE_HOME("site_home", "Open selected site's home", AmountType.NONE, Double.NaN),
    SPEED_UP("speed_up", "Speed up", AmountType.SPEED, Double.NaN),
    SPEED_DOWN("speed_down", "Slow down", AmountType.SPEED, Double.NaN),
    SPEED_0_5("speed_0_5", "Set speed to 0.5x", AmountType.NONE, 0.5),
    SPEED_0_8("speed_0_8", "Set speed to 0.8x", AmountType.NONE, 0.8),
    SPEED_1("speed_1", "Set speed to 1x", AmountType.NONE, 1.0),
    SPEED_1_5("speed_1_5", "Set speed to 1.5x", AmountType.NONE, 1.5),
    SPEED_2("speed_2", "Set speed to 2x", AmountType.NONE, 2.0),
    SPEED_2_5("speed_2_5", "Set speed to 2.5x", AmountType.NONE, 2.5),
    SPEED_3("speed_3", "Set speed to 3x", AmountType.NONE, 3.0),
    SPEED_4("speed_4", "Set speed to 4x", AmountType.NONE, 4.0),
    SEEK_FORWARD("seek_forward", "Fast-forward", AmountType.SECONDS, Double.NaN),
    SEEK_BACKWARD("seek_backward", "Rewind", AmountType.SECONDS, Double.NaN),
    PLAY_PAUSE("play_pause", "Play/Pause", AmountType.NONE, Double.NaN),
    PICTURE_IN_PICTURE("picture_in_picture", "Enter Picture-in-Picture", AmountType.NONE, Double.NaN),
    TOGGLE_SPEED_BAR("toggle_speed_bar", "Show/hide speed bar", AmountType.NONE, Double.NaN),
    SHARE("share", "Share current page", AmountType.NONE, Double.NaN),
    VIDEO_SUBS("video_subs", "Video Subs", AmountType.NONE, Double.NaN),
    SUMMARY_ONE("summary_one", "Summary One", AmountType.NONE, Double.NaN),
    SUMMARY_TWO("summary_two", "Summary Two", AmountType.NONE, Double.NaN),
    QUIZ("quiz", "Quiz", AmountType.NONE, Double.NaN),
    DOWNLOAD("download", "Download", AmountType.NONE, Double.NaN),
    SAVED("saved", "Saved", AmountType.NONE, Double.NaN),
    SETTINGS("settings", "Settings", AmountType.NONE, Double.NaN),
    LOCK_SCREEN("lock_screen", "Lock screen", AmountType.NONE, Double.NaN);

    enum AmountType {
        NONE,
        SPEED,
        SECONDS
    }

    final String id;
    final String label;
    final AmountType amountType;
    final double exactSpeed;

    OmniButtonAction(String id, String label, AmountType amountType, double exactSpeed) {
        this.id = id;
        this.label = label;
        this.amountType = amountType;
        this.exactSpeed = exactSpeed;
    }

    boolean usesAmount() {
        return amountType != AmountType.NONE;
    }

    boolean acceptsAmount(double amount) {
        if (!Double.isFinite(amount)) {
            return false;
        }
        if (amountType == AmountType.SPEED) {
            return amount >= 0.01 && amount <= 3.75;
        }
        if (amountType == AmountType.SECONDS) {
            return amount >= 1.0 && amount <= 600.0;
        }
        return true;
    }

    String label(double amount) {
        if (amountType == AmountType.SPEED) {
            return label + " by " + formatAmount(amount) + "x";
        }
        if (amountType == AmountType.SECONDS) {
            return label + " " + formatAmount(amount) + " seconds";
        }
        return label;
    }

    static OmniButtonAction fromId(String id) {
        if (id == null) {
            return null;
        }
        for (OmniButtonAction action : values()) {
            if (action.id.equals(id)) {
                return action;
            }
        }
        return null;
    }

    static OmniButtonAction defaultFor(OmniButtonGesture.Direction direction) {
        switch (direction) {
            case UP:
                return YOUTUBE_HISTORY;
            case UP_RIGHT:
                return SPEED_UP;
            case RIGHT:
                return NEXT_CHAPTER;
            case DOWN_RIGHT:
                return SEEK_FORWARD;
            case DOWN:
                return WATCH_LATER;
            case DOWN_LEFT:
                return SEEK_BACKWARD;
            case LEFT:
                return PREVIOUS_CHAPTER;
            case UP_LEFT:
                return SPEED_DOWN;
            default:
                return NONE;
        }
    }

    static double defaultAmount(OmniButtonAction action) {
        if (action == SPEED_UP) {
            return 0.5;
        }
        if (action == SPEED_DOWN) {
            return 0.25;
        }
        if (action == SEEK_FORWARD || action == SEEK_BACKWARD) {
            return 15.0;
        }
        return Double.NaN;
    }

    private static String formatAmount(double amount) {
        if (amount == Math.rint(amount)) {
            return String.format(Locale.US, "%.0f", amount);
        }
        return String.format(Locale.US, "%.2f", amount)
                .replaceAll("0+$", "")
                .replaceAll("\\.$", "");
    }
}
