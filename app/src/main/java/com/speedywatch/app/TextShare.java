package com.speedywatch.app;

import android.app.Activity;
import android.content.Intent;
import android.widget.Toast;

final class TextShare {
    private TextShare() {
    }

    static void showChooser(
            Activity activity,
            String videoTitle,
            String contentLabel,
            String content,
            String sourceUrl
    ) {
        String value = content == null ? "" : content.trim();
        if (value.isEmpty()) {
            return;
        }

        String url = sourceUrl == null ? "" : sourceUrl.trim();
        if (!SavedSummaryStore.isSupportedSourceUrl(url)) {
            Toast.makeText(activity, "Original video URL is unavailable", Toast.LENGTH_LONG).show();
            return;
        }

        String title = videoTitle == null || videoTitle.trim().isEmpty()
                ? "YouTube Video"
                : videoTitle.trim();
        String label = contentLabel == null ? "" : contentLabel.trim();
        String subject = label.isEmpty() ? title : title + " - " + label;
        String shareText = subject + "\n\n" + value + "\n\nOriginal URL:\n" + url;

        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, subject)
                .putExtra(Intent.EXTRA_TEXT, shareText);
        activity.startActivity(Intent.createChooser(share, "Share with"));
    }
}
