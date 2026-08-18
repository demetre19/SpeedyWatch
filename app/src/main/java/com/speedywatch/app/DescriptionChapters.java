package com.speedywatch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class DescriptionChapters {
    static final int MAXIMUM_DESCRIPTION_LENGTH = 100_000;
    private static final int MAXIMUM_CHAPTERS = 200;
    private static final double MAXIMUM_VIDEO_SECONDS = 604_800;
    private static final double MINIMUM_CHAPTER_SECONDS = 10;
    private static final double PREVIOUS_RESTART_SECONDS = 3;
    private static final float SWIPE_ACTIVE_HEIGHT_FRACTION = 0.8f;
    private static final Pattern CHAPTER_LINE = Pattern.compile(
            "^\\s*(?:(?:[-*•>])\\s*)?((?:\\d{1,3}:)?\\d{1,3}:\\d{2})\\s+"
                    + "(?:[-–—|]\\s*)?(.{1,200}?)\\s*$"
    );

    private DescriptionChapters() {
    }
    static boolean swipeStartsInActiveRegion(
            boolean chaptersAvailable,
            float startY,
            float viewHeight
    ) {
        return chaptersAvailable
                && Float.isFinite(startY)
                && Float.isFinite(viewHeight)
                && startY >= 0
                && viewHeight > 0
                && startY < viewHeight * SWIPE_ACTIVE_HEIGHT_FRACTION;
    }

    static int swipeDirection(float deltaX, float deltaY, float minimumDistance) {
        if (!Float.isFinite(deltaX)
                || !Float.isFinite(deltaY)
                || !Float.isFinite(minimumDistance)
                || minimumDistance <= 0
                || Math.abs(deltaX) < minimumDistance
                || Math.abs(deltaX) <= Math.abs(deltaY) * 1.5f) {
            return 0;
        }
        return deltaX > 0 ? 1 : -1;
    }


    static List<Chapter> parse(String description, double durationSeconds) {
        if (description == null
                || description.isEmpty()
                || description.length() > MAXIMUM_DESCRIPTION_LENGTH
                || !Double.isFinite(durationSeconds)
                || durationSeconds <= 0
                || durationSeconds > MAXIMUM_VIDEO_SECONDS) {
            return List.of();
        }

        List<Chapter> chapters = new ArrayList<>();
        for (String line : description.split("\\R", -1)) {
            Matcher matcher = CHAPTER_LINE.matcher(line);
            if (!matcher.matches()) {
                continue;
            }
            double startSeconds = parseTimestamp(matcher.group(1));
            String title = matcher.group(2).trim();
            if (!Double.isFinite(startSeconds)
                    || startSeconds < 0
                    || startSeconds >= durationSeconds
                    || title.isEmpty()
                    || chapters.size() >= MAXIMUM_CHAPTERS) {
                return List.of();
            }
            chapters.add(new Chapter(startSeconds, title));
        }

        if (chapters.size() < 3 || chapters.get(0).startSeconds != 0) {
            return List.of();
        }
        for (int index = 1; index < chapters.size(); index++) {
            if (chapters.get(index).startSeconds - chapters.get(index - 1).startSeconds
                    < MINIMUM_CHAPTER_SECONDS) {
                return List.of();
            }
        }
        if (durationSeconds - chapters.get(chapters.size() - 1).startSeconds
                < MINIMUM_CHAPTER_SECONDS) {
            return List.of();
        }
        return Collections.unmodifiableList(chapters);
    }

    static Chapter target(List<Chapter> chapters, double currentSeconds, boolean next) {
        if (chapters == null || chapters.isEmpty() || !Double.isFinite(currentSeconds)) {
            return null;
        }
        if (next) {
            for (Chapter chapter : chapters) {
                if (chapter.startSeconds > currentSeconds + 0.25) {
                    return chapter;
                }
            }
            return null;
        }

        int currentIndex = -1;
        for (int index = 0; index < chapters.size(); index++) {
            if (chapters.get(index).startSeconds <= currentSeconds + 0.25) {
                currentIndex = index;
            } else {
                break;
            }
        }
        if (currentIndex < 0) {
            return null;
        }
        Chapter current = chapters.get(currentIndex);
        if (currentSeconds - current.startSeconds > PREVIOUS_RESTART_SECONDS) {
            return current;
        }
        return currentIndex > 0 ? chapters.get(currentIndex - 1) : null;
    }

    private static double parseTimestamp(String value) {
        String[] fields = value.split(":", -1);
        try {
            if (fields.length == 2) {
                int minutes = Integer.parseInt(fields[0]);
                int seconds = Integer.parseInt(fields[1]);
                return seconds < 60 ? minutes * 60d + seconds : Double.NaN;
            }
            if (fields.length == 3) {
                int hours = Integer.parseInt(fields[0]);
                int minutes = Integer.parseInt(fields[1]);
                int seconds = Integer.parseInt(fields[2]);
                return minutes < 60 && seconds < 60
                        ? hours * 3600d + minutes * 60d + seconds
                        : Double.NaN;
            }
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
        return Double.NaN;
    }

    static final class Chapter {
        final double startSeconds;
        final String title;

        Chapter(double startSeconds, String title) {
            this.startSeconds = startSeconds;
            this.title = title;
        }
    }
}
