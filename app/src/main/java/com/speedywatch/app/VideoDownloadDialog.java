package com.speedywatch.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.Intent;
import android.content.res.ColorStateList;
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
import android.widget.ProgressBar;
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
    private final SpeedyWatchSettings settings;
    private final String videoUrl;
    private final boolean fromClipboard;
    private final String cookieHeader;
    private final String userAgent;
    private final String referer;
    private final CapturedMediaRequest capturedMediaRequest;
    private final String capturedCookieHeader;
    private final boolean soundCloud;

    private Dialog dialog;
    private TextView status;
    private ProgressBar checkingProgress;
    private TextView title;
    private LinearLayout choices;
    private String videoTitle = "Video";
    private boolean titleVerified;

    VideoDownloadDialog(
            Activity activity,
            ExecutorService executor,
            SpeedyWatchSettings settings,
            String videoUrl,
            boolean fromClipboard,
            String cookieHeader,
            String userAgent,
            String referer,
            CapturedMediaRequest capturedMediaRequest,
            String capturedCookieHeader,
            String initialTitle,
            boolean initialTitleVerified
    ) {
        this.activity = activity;
        this.executor = executor;
        this.settings = settings;
        this.videoUrl = videoUrl;
        this.fromClipboard = fromClipboard;
        this.cookieHeader = cookieHeader;
        this.userAgent = userAgent;
        this.referer = referer;
        this.capturedMediaRequest = capturedMediaRequest;
        this.capturedCookieHeader = capturedCookieHeader;
        this.soundCloud = SupportedSite.forUrl(videoUrl) == SupportedSite.SOUNDCLOUD;
        this.videoTitle = MediaDownloadEngine.safeDisplayName(initialTitle);
        this.titleVerified = initialTitleVerified;
    }

    void show() {
        if (!MediaDownloadEngine.isSupportedDownloadUrl(videoUrl)) {
            Toast.makeText(
                    activity,
                    "Copy a supported media URL or open supported media first",
                    Toast.LENGTH_LONG
            ).show();
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
        title = text(soundCloud ? "Download audio" : "Download video", 21, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        heading.addView(title);
        status = text(
                fromClipboard
                        ? (soundCloud ? "Checking clipboard track..." : "Checking clipboard video...")
                        : (soundCloud ? "Checking this track..." : "Checking this video..."),
                12,
                MUTED
        );
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

        checkingProgress = new ProgressBar(
                activity,
                null,
                android.R.attr.progressBarStyleHorizontal
        );
        checkingProgress.setIndeterminate(true);
        checkingProgress.setIndeterminateTintList(ColorStateList.valueOf(ACTIVE));
        checkingProgress.setContentDescription(soundCloud ? "Checking audio" : "Checking video formats");
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(3)
        );
        progressParams.setMargins(0, dp(8), 0, 0);
        content.addView(checkingProgress, progressParams);

        TextView guidance = text(
                soundCloud
                        ? "Choose an MP3 quality."
                        : "Choose an MP3 quality or the maximum MP4 resolution you want.",
                14,
                Color.WHITE
        );
        guidance.setPadding(0, dp(14), 0, dp(10));
        content.addView(guidance);

        choices = new LinearLayout(activity);
        choices.setOrientation(LinearLayout.VERTICAL);
        showChoices(soundCloud ? java.util.Collections.emptyList() : STANDARD_RESOLUTIONS);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.setBackground(panelBackground(PANEL, Color.rgb(55, 55, 55)));
        scroll.addView(choices);
        content.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        TextView destination = text(
                soundCloud
                        ? "Saves by artist in Downloads/SpeedyWatch/SoundCloud."
                        : "Downloads continue in notifications and save to Downloads/SpeedyWatch.",
                12,
                MUTED
        );
        content.addView(destination);
        return content;
    }

    private void loadFormats() {
        executor.execute(() -> {
            try {
                MediaDownloadEngine.Metadata metadata = MediaDownloadEngine.loadMetadata(
                        activity,
                        videoUrl,
                        cookieHeader,
                        userAgent,
                        referer,
                        capturedMediaRequest,
                        capturedCookieHeader
                );
                activity.runOnUiThread(() -> showFormats(metadata));
            } catch (Exception error) {
                activity.runOnUiThread(this::showStandardOptions);
            }
        });
    }

    private void showFormats(MediaDownloadEngine.Metadata metadata) {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        videoTitle = metadata.title;
        titleVerified = true;
        title.setText(metadata.title);
        checkingProgress.setVisibility(View.GONE);
        status.setText(
                (fromClipboard ? (soundCloud ? "Clipboard track • " : "Clipboard video • ") : "")
                        + (soundCloud
                        ? "MP3 options ready"
                        : metadata.resolutions.size() + " video quality options ready")
        );
        showChoices(metadata.resolutions);
    }

    private void showChoices(List<Integer> resolutions) {
        choices.removeAllViews();

        String defaultQuality = settings.getDefaultMp3Quality();
        addMp3Choice(defaultQuality, true, true);
        if (!SpeedyWatchSettings.MP3_QUALITY_HIGH.equals(defaultQuality)) {
            addMp3Choice(SpeedyWatchSettings.MP3_QUALITY_HIGH, false, false);
        }
        if (!SpeedyWatchSettings.MP3_QUALITY_STANDARD.equals(defaultQuality)) {
            addMp3Choice(SpeedyWatchSettings.MP3_QUALITY_STANDARD, false, false);
        }
        if (!SpeedyWatchSettings.MP3_QUALITY_COMPACT.equals(defaultQuality)) {
            addMp3Choice(SpeedyWatchSettings.MP3_QUALITY_COMPACT, false, false);
        }

        for (int height : resolutions) {
            Button mp4 = choiceButton(height + "p MP4");
            mp4.setOnClickListener(ignored -> startDownload(
                    SpeedyWatchDownloadService.KIND_MP4,
                    height,
                    SpeedyWatchSettings.MP3_QUALITY_STANDARD
            ));
            choices.addView(mp4, choiceParams(false));
        }
    }

    private void addMp3Choice(String quality, boolean first, boolean isDefault) {
        String label = "MP3 — " + SpeedyWatchSettings.mp3QualityLabel(quality)
                + (isDefault ? " · default" : "");
        Button mp3 = choiceButton(label);
        mp3.setOnClickListener(ignored -> startDownload(
                SpeedyWatchDownloadService.KIND_MP3,
                0,
                quality
        ));
        choices.addView(mp3, choiceParams(first));
    }

    private void showStandardOptions() {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        checkingProgress.setVisibility(View.GONE);
        status.setText(
                fromClipboard
                        ? (soundCloud
                        ? "Clipboard track • MP3 options ready"
                        : "Clipboard video • standard download options ready")
                        : (soundCloud ? "MP3 options ready" : "Standard download options ready")
        );
        showChoices(soundCloud ? java.util.Collections.emptyList() : STANDARD_RESOLUTIONS);
        Toast.makeText(
                activity,
                soundCloud ? "MP3 download options are ready" : "Standard download options are ready",
                Toast.LENGTH_LONG
        ).show();
    }

    private void startDownload(String kind, int height, String mp3Quality) {
        Intent intent = new Intent(activity, SpeedyWatchDownloadService.class)
                .setAction(SpeedyWatchDownloadService.ACTION_DOWNLOAD)
                .putExtra(SpeedyWatchDownloadService.EXTRA_URL, videoUrl)
                .putExtra(SpeedyWatchDownloadService.EXTRA_TITLE, videoTitle)
                .putExtra(
                        SpeedyWatchDownloadService.EXTRA_TITLE_VERIFIED,
                        titleVerified
                )
                .putExtra(SpeedyWatchDownloadService.EXTRA_KIND, kind)
                .putExtra(SpeedyWatchDownloadService.EXTRA_HEIGHT, height)
                .putExtra(SpeedyWatchDownloadService.EXTRA_MP3_QUALITY, mp3Quality)
                .putExtra(SpeedyWatchDownloadService.EXTRA_COOKIE_HEADER, cookieHeader)
                .putExtra(SpeedyWatchDownloadService.EXTRA_USER_AGENT, userAgent)
                .putExtra(SpeedyWatchDownloadService.EXTRA_REFERER, referer);
        if (capturedMediaRequest != null) {
            intent.putExtra(
                    SpeedyWatchDownloadService.EXTRA_CAPTURED_MEDIA_URL,
                    capturedMediaRequest.mediaUrl
            );
            intent.putExtra(
                    SpeedyWatchDownloadService.EXTRA_CAPTURED_COOKIE_HEADER,
                    capturedCookieHeader
            );
            intent.putExtra(
                    SpeedyWatchDownloadService.EXTRA_CAPTURED_USER_AGENT,
                    capturedMediaRequest.userAgent
            );
            intent.putExtra(
                    SpeedyWatchDownloadService.EXTRA_CAPTURED_REFERER,
                    capturedMediaRequest.referer
            );
            intent.putExtra(
                    SpeedyWatchDownloadService.EXTRA_CAPTURED_ORIGIN,
                    capturedMediaRequest.origin
            );
            intent.putExtra(
                    SpeedyWatchDownloadService.EXTRA_CAPTURED_AUTHORIZATION,
                    capturedMediaRequest.authorization
            );
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            activity.startForegroundService(intent);
        } else {
            activity.startService(intent);
        }
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
