package com.speedywatch.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.Arrays;
import java.util.List;

final class VideoDownloadDialog {
    private static final int BACKGROUND = Color.rgb(15, 15, 15);
    private static final int PANEL = Color.rgb(30, 30, 30);
    private static final int BUTTON = Color.rgb(48, 48, 48);
    private static final int ACTIVE = Color.rgb(255, 0, 51);
    private static final int MUTED = Color.rgb(185, 185, 185);
    private static final List<Integer> STANDARD_RESOLUTIONS =
            Arrays.asList(2160, 1440, 1080, 720, 480, 360);
    private final Activity activity;
    private final ExecutorService executor;
    private final String videoUrl;

    private Dialog dialog;
    private TextView status;
    private TextView title;
    private LinearLayout choices;
    private String videoTitle = "YouTube Video";

    VideoDownloadDialog(Activity activity, ExecutorService executor, String videoUrl) {
        this.activity = activity;
        this.executor = executor;
        this.videoUrl = videoUrl;
    }

    void show() {
        if (!YouTubeDownloadEngine.isSupportedYouTubeUrl(videoUrl)) {
            Toast.makeText(activity, "Open a YouTube video first", Toast.LENGTH_LONG).show();
            return;
        }
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(buildContent());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            window.setGravity(Gravity.CENTER);
        }
        loadFormats();
    }

    private View buildContent() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        content.setBackground(panelBackground(BACKGROUND, Color.rgb(70, 70, 70)));

        LinearLayout header = horizontalLayout();
        LinearLayout heading = new LinearLayout(activity);
        heading.setOrientation(LinearLayout.VERTICAL);
        title = text("Download video", 21, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        heading.addView(title);
        status = text("Checking this video...", 12, MUTED);
        heading.addView(status);
        header.addView(heading, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        ImageButton close = new ImageButton(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setContentDescription("Close downloads");
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setBackground(panelBackground(PANEL, BUTTON));
        close.setOnClickListener(ignored -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        closeParams.setMarginStart(dp(8));
        header.addView(close, closeParams);
        content.addView(header);

        TextView guidance = text(
                "Choose MP3 audio or the maximum MP4 resolution you want.",
                14,
                Color.WHITE
        );
        guidance.setPadding(0, dp(14), 0, dp(10));
        content.addView(guidance);

        choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.VERTICAL);
        showChoices(STANDARD_RESOLUTIONS);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackground(panelBackground(PANEL, Color.rgb(55, 55, 55)));
        scroll.addView(choices);
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        TextView destination = text("Downloads continue in notifications and save to Downloads/SpeedyWatch.", 12, MUTED);
        destination.setPadding(0, dp(10), 0, 0);
        content.addView(destination);
        return content;
    }

    private void loadFormats() {
        executor.execute(() -> {
            try {
                YouTubeDownloadEngine.Metadata metadata = YouTubeDownloadEngine.loadMetadata(activity, videoUrl);
                activity.runOnUiThread(() -> showFormats(metadata));
            } catch (Exception error) {
                activity.runOnUiThread(this::showStandardOptions);
            }
        });
    }

    private void showFormats(YouTubeDownloadEngine.Metadata metadata) {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        videoTitle = metadata.title;
        title.setText(metadata.title);
        status.setText(metadata.resolutions.size() + " video quality options ready");
        showChoices(metadata.resolutions);
    }

    private void showChoices(List<Integer> resolutions) {
        choices.removeAllViews();

        Button mp3 = choiceButton("MP3 — best available audio");
        mp3.setOnClickListener(ignored -> startDownload(
                SpeedyWatchDownloadService.KIND_MP3,
                0
        ));
        choices.addView(mp3, choiceParams(true));

        for (int height : resolutions) {
            Button mp4 = choiceButton(height + "p MP4");
            mp4.setOnClickListener(ignored -> startDownload(
                    SpeedyWatchDownloadService.KIND_MP4,
                    height
            ));
            choices.addView(mp4, choiceParams(false));
        }
    }

    private void showStandardOptions() {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        status.setText("Standard download options ready");
        showChoices(STANDARD_RESOLUTIONS);
        Toast.makeText(activity, "Standard download options are ready", Toast.LENGTH_LONG).show();
    }

    private void startDownload(String kind, int height) {
        Intent intent = new Intent(activity, SpeedyWatchDownloadService.class)
                .setAction(SpeedyWatchDownloadService.ACTION_DOWNLOAD)
                .putExtra(SpeedyWatchDownloadService.EXTRA_URL, videoUrl)
                .putExtra(SpeedyWatchDownloadService.EXTRA_TITLE, videoTitle)
                .putExtra(SpeedyWatchDownloadService.EXTRA_KIND, kind)
                .putExtra(SpeedyWatchDownloadService.EXTRA_HEIGHT, height);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(intent);
        } else {
            activity.startService(intent);
        }
        String selection = SpeedyWatchDownloadService.KIND_MP3.equals(kind)
                ? "MP3"
                : height + "p MP4";
        Toast.makeText(activity, selection + " download started", Toast.LENGTH_LONG).show();
        dialog.dismiss();
    }

    private Button choiceButton(String label) {
        Button button = new Button(activity);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(15);
        button.setAllCaps(false);
        button.setGravity(Gravity.CENTER_VERTICAL);
        button.setPadding(dp(14), 0, dp(14), 0);
        button.setBackground(panelBackground(BUTTON, BUTTON));
        return button;
    }

    private LinearLayout.LayoutParams choiceParams(boolean first) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        params.setMargins(dp(8), first ? dp(8) : dp(6), dp(8), 0);
        return params;
    }

    private LinearLayout horizontalLayout() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private GradientDrawable panelBackground(int fill, int stroke) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(fill);
        background.setCornerRadius(dp(6));
        background.setStroke(dp(1), stroke);
        return background;
    }


    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }
}
