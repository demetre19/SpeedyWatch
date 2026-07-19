package com.speedywatch.app;

final class TranscriptEntry {
    final double startSeconds;
    final double durationSeconds;
    final String text;

    TranscriptEntry(double startSeconds, double durationSeconds, String text) {
        this.startSeconds = startSeconds;
        this.durationSeconds = durationSeconds;
        this.text = text;
    }

    String timestamp() {
        int totalSeconds = Math.max(0, (int) Math.floor(startSeconds));
        int hours = totalSeconds / 3600;
        int minutes = (totalSeconds % 3600) / 60;
        int seconds = totalSeconds % 60;
        if (hours > 0) {
            return String.format(java.util.Locale.US, "%d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format(java.util.Locale.US, "%d:%02d", minutes, seconds);
    }
}
