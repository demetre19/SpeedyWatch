package com.speedywatch.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

final class VideoQuizDialog {
    private static final int BACKGROUND = Color.rgb(15, 15, 15);
    private static final int PANEL = Color.rgb(30, 30, 30);
    private static final int BUTTON = Color.rgb(48, 48, 48);
    private static final int ACTIVE = Color.rgb(255, 0, 51);
    private static final int MUTED = Color.rgb(185, 185, 185);
    private static final int[] QUESTION_COUNTS = {6, 10, 12, 20};

    private final Activity activity;
    private final YouTubeSubsDialog.TranscriptHost host;
    private final SpeedyWatchSettings settings;
    private final OpenRouterClient client;
    private final ExecutorService executor;
    private final SavedSummaryStore savedSummaryStore;
    private final List<TranscriptEntry> entries = new ArrayList<>();
    private final List<Button> countButtons = new ArrayList<>();

    private Dialog dialog;
    private TextView status;
    private TextView output;
    private Button createButton;
    private Button saveQuizButton;
    private Button shareQuizButton;
    private int questionCount = 10;
    private String videoTitle = "YouTube Video";
    private String videoUrl = "";
    private String currentQuizText = "";
    private String currentQuizLabel = "";

    VideoQuizDialog(
            Activity activity,
            YouTubeSubsDialog.TranscriptHost host,
            SpeedyWatchSettings settings,
            OpenRouterClient client,
            ExecutorService executor,
            SavedSummaryStore savedSummaryStore
    ) {
        this.activity = activity;
        this.host = host;
        this.settings = settings;
        this.client = client;
        this.executor = executor;
        this.savedSummaryStore = savedSummaryStore;
    }

    void show() {
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
        loadTranscript();
    }

    private View buildContent() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        content.setBackground(panelBackground(BACKGROUND, Color.rgb(70, 70, 70)));

        LinearLayout header = horizontalLayout();
        LinearLayout headerText = new LinearLayout(activity);
        headerText.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Video Quiz Prep", 21, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        headerText.addView(title);
        status = text("Loading subtitles...", 12, MUTED);
        headerText.addView(status);
        header.addView(headerText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton close = new ImageButton(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setContentDescription("Close Video Quiz");
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setBackground(panelBackground(PANEL, BUTTON));
        close.setOnClickListener(ignored -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        closeParams.setMarginStart(dp(8));
        header.addView(close, closeParams);
        content.addView(header);

        TextView guidance = text(
                "Choose how many important questions you want to know before watching.",
                14,
                Color.WHITE
        );
        guidance.setPadding(0, dp(12), 0, dp(8));
        content.addView(guidance);

        LinearLayout countRow = horizontalLayout();
        for (int count : QUESTION_COUNTS) {
            Button countButton = button(String.valueOf(count));
            countButton.setTag(count);
            countButton.setOnClickListener(ignored -> selectQuestionCount((int) countButton.getTag()));
            countButtons.add(countButton);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
            if (countRow.getChildCount() > 0) {
                params.setMarginStart(dp(8));
            }
            countRow.addView(countButton, params);
        }
        content.addView(countRow);
        updateCountButtons();

        createButton = button("Create 10 questions");
        createButton.setEnabled(false);
        createButton.setBackground(panelBackground(ACTIVE, ACTIVE));
        createButton.setOnClickListener(ignored -> createQuiz());
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(46)
        );
        createParams.setMargins(0, dp(8), 0, dp(8));
        content.addView(createButton, createParams);

        output = text("Your pre-watch questions will appear here.", 15, Color.WHITE);
        output.setTextIsSelectable(true);
        output.setLineSpacing(0, 1.2f);
        output.setPadding(dp(10), dp(10), dp(10), dp(10));
        ScrollView outputScroll = new ScrollView(activity);
        outputScroll.setBackground(panelBackground(PANEL, Color.rgb(55, 55, 55)));
        outputScroll.addView(output);
        content.addView(outputScroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        LinearLayout quizActions = horizontalLayout();
        saveQuizButton = button("Save quiz");
        saveQuizButton.setEnabled(false);
        saveQuizButton.setOnClickListener(ignored -> saveQuiz());
        quizActions.addView(saveQuizButton, new LinearLayout.LayoutParams(0, dp(44), 1f));
        shareQuizButton = button("Share quiz");
        shareQuizButton.setEnabled(false);
        shareQuizButton.setOnClickListener(ignored -> TextShare.showChooser(
                activity,
                videoTitle,
                currentQuizLabel,
                currentQuizText,
                videoUrl
        ));
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        shareParams.setMarginStart(dp(8));
        quizActions.addView(shareQuizButton, shareParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionsParams.setMargins(0, dp(8), 0, 0);
        content.addView(quizActions, actionsParams);
        return content;
    }

    private void loadTranscript() {
        host.loadTranscript(new YouTubeSubsDialog.TranscriptCallback() {
            @Override
            public void onLoaded(List<TranscriptEntry> loaded, String title, String url) {
                if (dialog == null || !dialog.isShowing()) {
                    return;
                }
                entries.clear();
                entries.addAll(loaded);
                videoTitle = title == null || title.trim().isEmpty() ? "YouTube Video" : title;
                videoUrl = url == null ? "" : url;
                status.setText(entries.size() + " subtitles ready");
                createButton.setEnabled(!entries.isEmpty());
            }

            @Override
            public void onError(String message) {
                if (dialog == null || !dialog.isShowing()) {
                    return;
                }
                status.setText(message);
                Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void selectQuestionCount(int count) {
        questionCount = count;
        createButton.setText("Create " + count + " questions");
        updateCountButtons();
    }

    private void updateCountButtons() {
        for (Button button : countButtons) {
            boolean selected = (int) button.getTag() == questionCount;
            button.setBackground(panelBackground(selected ? ACTIVE : BUTTON, selected ? ACTIVE : BUTTON));
        }
    }

    private void createQuiz() {
        final String apiKey;
        try {
            apiKey = settings.getApiKey();
        } catch (GeneralSecurityException error) {
            Toast.makeText(activity, "Stored API key could not be decrypted", Toast.LENGTH_LONG).show();
            return;
        }
        String modelId = settings.getModelId();
        if (apiKey.trim().isEmpty() || modelId.trim().isEmpty()) {
            Toast.makeText(activity, "Configure OpenRouter in Settings first", Toast.LENGTH_LONG).show();
            return;
        }
        String prompt = settings.getQuizPrompt();
        if (prompt.trim().isEmpty()) {
            Toast.makeText(activity, "Quiz prompt is empty", Toast.LENGTH_LONG).show();
            return;
        }

        int requestedCount = questionCount;
        currentQuizText = "";
        currentQuizLabel = "";
        saveQuizButton.setEnabled(false);
        shareQuizButton.setEnabled(false);
        createButton.setEnabled(false);
        status.setText("Creating " + requestedCount + " questions with " + modelId);
        output.setText("Creating your pre-watch questions...");
        String userMessage = buildUserMessage(requestedCount);
        executor.execute(() -> {
            try {
                String result = client.summarize(
                        apiKey,
                        modelId,
                        prompt,
                        userMessage
                );
                activity.runOnUiThread(() -> {
                    if (dialog != null && dialog.isShowing()) {
                        output.setText(MarkdownRenderer.render(
                                result,
                                activity.getResources().getDisplayMetrics().density
                        ));
                        currentQuizText = result;
                        currentQuizLabel = "Quiz | " + requestedCount + " questions";
                        saveQuizButton.setEnabled(true);
                        shareQuizButton.setEnabled(true);
                        status.setText(requestedCount + " questions ready | tap Save or Share below");
                        createButton.setEnabled(true);
                    }
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (dialog != null && dialog.isShowing()) {
                        String message = safeMessage(error, "Quiz generation failed");
                        output.setText(message);
                        status.setText("Quiz generation failed");
                        createButton.setEnabled(true);
                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }
    private void saveQuiz() {
        if (currentQuizText.trim().isEmpty() || currentQuizLabel.trim().isEmpty()) {
            return;
        }
        try {
            savedSummaryStore.save(
                    videoTitle,
                    currentQuizLabel,
                    currentQuizText,
                    videoUrl
            );
            Toast.makeText(activity, "Quiz saved", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException error) {
            Toast.makeText(activity, safeMessage(error, "Quiz could not be saved"), Toast.LENGTH_LONG).show();
        } catch (RuntimeException error) {
            Toast.makeText(activity, "Quiz could not be saved", Toast.LENGTH_LONG).show();
        }
    }


    private String buildUserMessage(int requestedCount) {
        StringBuilder transcript = new StringBuilder();
        for (TranscriptEntry entry : entries) {
            transcript.append(entry.text).append('\n');
        }
        return "Source: YouTube Subtitles\nTitle: "
                + videoTitle
                + "\nURL: "
                + videoUrl
                + "\nRequested question count: "
                + requestedCount
                + "\n\nTranscript:\n"
                + transcript;
    }

    private LinearLayout horizontalLayout() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private Button button(String value) {
        Button button = new Button(activity);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(10), 0, dp(10), 0);
        button.setBackground(panelBackground(BUTTON, BUTTON));
        return button;
    }

    private TextView text(String value, int size, int color) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(color);
        return text;
    }

    private GradientDrawable panelBackground(int fill, int stroke) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(6));
        shape.setStroke(dp(1), stroke);
        return shape;
    }

    private int dp(int value) {
        return Math.round(value * activity.getResources().getDisplayMetrics().density);
    }

    private static String safeMessage(Exception error, String fallback) {
        String message = error.getMessage();
        return message == null || message.trim().isEmpty() ? fallback : message;
    }
}
