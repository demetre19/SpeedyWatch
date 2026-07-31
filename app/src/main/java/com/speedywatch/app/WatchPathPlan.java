package com.speedywatch.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

final class WatchPathPlan {
    private static final int MAXIMUM_RESPONSE_LENGTH = 200_000;
    private static final int MAXIMUM_SEGMENTS = 20;
    private static final double MAXIMUM_VIDEO_SECONDS = 604_800;

    static final class Segment {
        final double startSeconds;
        final double endSeconds;
        final String title;
        final String reason;

        Segment(double startSeconds, double endSeconds, String title, String reason) {
            this.startSeconds = startSeconds;
            this.endSeconds = endSeconds;
            this.title = title;
            this.reason = reason;
        }

        double durationSeconds() {
            return endSeconds - startSeconds;
        }
    }

    static final class Gap {
        final double startSeconds;
        final double endSeconds;

        Gap(double startSeconds, double endSeconds) {
            this.startSeconds = startSeconds;
            this.endSeconds = endSeconds;
        }
    }

    final String sourceUrl;
    final String goal;
    final int budgetMinutes;
    final double transcriptDurationSeconds;
    final List<Segment> segments;

    private WatchPathPlan(
            String sourceUrl,
            String goal,
            int budgetMinutes,
            double transcriptDurationSeconds,
            List<Segment> segments
    ) {
        this.sourceUrl = sourceUrl;
        this.goal = goal;
        this.budgetMinutes = budgetMinutes;
        this.transcriptDurationSeconds = transcriptDurationSeconds;
        this.segments = Collections.unmodifiableList(new ArrayList<>(segments));
    }

    static WatchPathPlan parse(
            String response,
            String sourceUrl,
            String goal,
            int budgetMinutes,
            double transcriptDurationSeconds
    ) {
        String normalizedGoal = normalizeGoal(goal);
        validatePlanContext(sourceUrl, budgetMinutes, transcriptDurationSeconds);
        if (response == null || response.trim().isEmpty() || response.length() > MAXIMUM_RESPONSE_LENGTH) {
            throw invalidResponse();
        }

        String body = unwrapCodeFence(response.trim());
        String[] lines = body.split("\\r?\\n");
        int lineIndex = firstContentLine(lines, 0);
        if (lineIndex >= lines.length || !"WATCHPATH 1".equals(lines[lineIndex].trim())) {
            throw invalidResponse();
        }

        List<Segment> segments = new ArrayList<>();
        double previousEnd = -1;
        double totalDuration = 0;
        for (lineIndex++; lineIndex < lines.length; lineIndex++) {
            String line = lines[lineIndex].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] fields = line.split("\\|", -1);
            if (fields.length != 5 || !"SEGMENT".equals(fields[0].trim())) {
                throw invalidResponse();
            }
            if (segments.size() >= MAXIMUM_SEGMENTS) {
                throw invalidResponse();
            }

            double start = parseSeconds(fields[1]);
            double end = parseSeconds(fields[2]);
            String title = boundedField(fields[3], 120);
            String reason = boundedField(fields[4], 300);
            if (start < 0
                    || end <= start
                    || end > transcriptDurationSeconds + 1
                    || (previousEnd >= 0 && start < previousEnd)) {
                throw invalidResponse();
            }
            totalDuration += end - start;
            if (totalDuration > budgetMinutes * 60.0 + 0.5) {
                throw invalidResponse();
            }
            segments.add(new Segment(start, end, title, reason));
            previousEnd = end;
        }
        if (segments.isEmpty()) {
            throw invalidResponse();
        }
        return new WatchPathPlan(
                sourceUrl.trim(),
                normalizedGoal,
                budgetMinutes,
                transcriptDurationSeconds,
                segments
        );
    }

    static String buildUserMessage(
            String sourceLabel,
            String videoTitle,
            String sourceUrl,
            String goal,
            int budgetMinutes,
            List<TranscriptEntry> entries
    ) {
        String normalizedGoal = normalizeGoal(goal);
        if (entries == null || entries.isEmpty() || !isSupportedBudget(budgetMinutes)) {
            throw new IllegalArgumentException("WatchPath request data is incomplete");
        }
        double transcriptDuration = transcriptDuration(entries);
        StringBuilder transcript = new StringBuilder();
        for (TranscriptEntry entry : entries) {
            double entryDuration = Double.isFinite(entry.durationSeconds)
                    ? Math.max(1, entry.durationSeconds)
                    : 1;
            double endSeconds = entry.startSeconds + entryDuration;
            transcript.append(String.format(
                    Locale.US,
                    "[startSeconds=%.3f endSeconds=%.3f] %s %s%n",
                    entry.startSeconds,
                    endSeconds,
                    entry.timestamp(),
                    entry.text
            ));
        }
        return "Source: " + singleLine(sourceLabel)
                + "\nTitle: " + singleLine(videoTitle)
                + "\nURL: " + singleLine(sourceUrl)
                + "\nViewing goal: " + normalizedGoal
                + "\nTime budget minutes: " + budgetMinutes
                + "\nTranscript duration seconds: "
                + String.format(Locale.US, "%.1f", transcriptDuration)
                + "\n\nTranscript:\n"
                + transcript;
    }

    static double transcriptDuration(List<TranscriptEntry> entries) {
        double duration = 0;
        if (entries != null) {
            for (TranscriptEntry entry : entries) {
                if (entry != null && Double.isFinite(entry.startSeconds)) {
                    double entryDuration = Double.isFinite(entry.durationSeconds)
                            ? Math.max(1, entry.durationSeconds)
                            : 1;
                    duration = Math.max(duration, entry.startSeconds + entryDuration);
                }
            }
        }
        if (duration <= 0 || duration > MAXIMUM_VIDEO_SECONDS) {
            throw new IllegalArgumentException("Transcript duration is invalid");
        }
        return duration;
    }

    double selectedDurationSeconds() {
        double duration = 0;
        for (Segment segment : segments) {
            duration += segment.durationSeconds();
        }
        return duration;
    }

    List<Gap> skippedRanges() {
        List<Gap> gaps = new ArrayList<>();
        double cursor = 0;
        for (Segment segment : segments) {
            if (segment.startSeconds - cursor >= 1) {
                gaps.add(new Gap(cursor, segment.startSeconds));
            }
            cursor = Math.max(cursor, segment.endSeconds);
        }
        if (transcriptDurationSeconds - cursor >= 1) {
            gaps.add(new Gap(cursor, transcriptDurationSeconds));
        }
        return gaps;
    }

    static String timestamp(double seconds) {
        int totalSeconds = Math.max(0, (int) Math.floor(seconds));
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int remainingSeconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(Locale.US, "%d:%02d:%02d", hours, minutes, remainingSeconds);
        }
        return String.format(Locale.US, "%d:%02d", minutes, remainingSeconds);
    }

    private static void validatePlanContext(
            String sourceUrl,
            int budgetMinutes,
            double transcriptDurationSeconds
    ) {
        if (sourceUrl == null
                || sourceUrl.trim().isEmpty()
                || sourceUrl.length() > 4_000
                || !isSupportedBudget(budgetMinutes)
                || !Double.isFinite(transcriptDurationSeconds)
                || transcriptDurationSeconds <= 0
                || transcriptDurationSeconds > MAXIMUM_VIDEO_SECONDS) {
            throw new IllegalArgumentException("WatchPath request data is incomplete");
        }
    }

    private static boolean isSupportedBudget(int budgetMinutes) {
        return budgetMinutes == 5 || budgetMinutes == 10 || budgetMinutes == 20;
    }

    private static String normalizeGoal(String goal) {
        String normalized = singleLine(goal).trim();
        if (normalized.isEmpty() || normalized.length() > 500) {
            throw new IllegalArgumentException("Enter a WatchPath goal up to 500 characters");
        }
        return normalized;
    }

    private static String singleLine(String value) {
        return value == null ? "" : value.replace('\r', ' ').replace('\n', ' ');
    }

    private static int firstContentLine(String[] lines, int start) {
        int index = start;
        while (index < lines.length && lines[index].trim().isEmpty()) {
            index++;
        }
        return index;
    }

    private static String unwrapCodeFence(String value) {
        if (!value.startsWith("```")) {
            return value;
        }
        int firstLineEnd = value.indexOf('\n');
        int closingFence = value.lastIndexOf("```");
        if (firstLineEnd < 0 || closingFence <= firstLineEnd) {
            throw invalidResponse();
        }
        return value.substring(firstLineEnd + 1, closingFence).trim();
    }

    private static double parseSeconds(String value) {
        try {
            double seconds = Double.parseDouble(value.trim());
            if (!Double.isFinite(seconds) || seconds < 0 || seconds > MAXIMUM_VIDEO_SECONDS) {
                throw invalidResponse();
            }
            return seconds;
        } catch (NumberFormatException error) {
            throw invalidResponse();
        }
    }

    private static String boundedField(String value, int maximumLength) {
        String normalized = value.trim();
        if (normalized.isEmpty() || normalized.length() > maximumLength) {
            throw invalidResponse();
        }
        for (int index = 0; index < normalized.length(); index++) {
            if (Character.isISOControl(normalized.charAt(index))) {
                throw invalidResponse();
            }
        }
        return normalized;
    }

    private static IllegalArgumentException invalidResponse() {
        return new IllegalArgumentException(
                "WatchPath returned an invalid route. Check its prompt in Settings and try again."
        );
    }
}
