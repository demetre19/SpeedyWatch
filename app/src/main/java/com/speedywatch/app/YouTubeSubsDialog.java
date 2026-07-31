package com.speedywatch.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.InputFilter;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

final class YouTubeSubsDialog {
    interface TranscriptHost {
        default void loadTranscript(TranscriptCallback callback) {
            loadTranscript("", callback);
        }
        void loadTranscript(String languageCode, TranscriptCallback callback);
        void loadCaptionOptions(CaptionOptionsCallback callback);
        void seekTo(double seconds);
        void currentTime(CurrentTimeCallback callback);
        void startWatchPath(WatchPathPlan plan);
        default String sourceLabel() {
            return "Video captions";
        }
    }

    interface CaptionOptionsCallback {
        void onLoaded(List<CaptionOption> options);
        void onError(String message);
    }

    interface CurrentTimeCallback {
        void onTime(double seconds);
    }

    interface TranscriptCallback {
        void onLoaded(List<TranscriptEntry> entries, String videoTitle, String videoUrl);
        void onError(String message);
    }

    private static final int BACKGROUND = Color.rgb(15, 15, 15);
    private static final int PANEL = Color.rgb(30, 30, 30);
    private static final int BUTTON = Color.rgb(48, 48, 48);
    private static final int ACTIVE = Color.rgb(255, 0, 51);
    private static final int MUTED = Color.rgb(185, 185, 185);
    private static final int[] WATCH_PATH_BUDGETS = {5, 10, 20};

    private final Activity activity;
    private final TranscriptHost host;
    private final SpeedyWatchSettings settings;
    private final OpenRouterClient client;
    private final ExecutorService executor;
    private final SavedSummaryStore savedSummaryStore;
    private final List<TranscriptEntry> entries = new ArrayList<>();
    private final List<ChatTurn> chatTurns = new ArrayList<>();
    private final Handler followHandler = new Handler(Looper.getMainLooper());
    private final Handler filterHandler = new Handler(Looper.getMainLooper());
    private final Runnable followTick = this::updateFollowPosition;
    private final Runnable filterTick = this::applyTranscriptFilter;
    private final List<CaptionOption> captionOptions = new ArrayList<>();

    private Dialog dialog;
    private TextView status;
    private EditText search;
    private ListView transcriptList;
    private TranscriptAdapter transcriptAdapter;
    private ScrollView summaryScroll;
    private LinearLayout summaryContent;
    private TextView summaryOutput;
    private Button summaryOneButton;
    private Button summaryTwoButton;
    private Button transcriptButton;
    private Button watchPathButton;
    private LinearLayout readingActions;
    private Button copySummaryButton;
    private Button saveSummaryButton;
    private Button shareSummaryButton;
    private TextView chatTitle;
    private LinearLayout chatRow;
    private EditText chatInput;
    private Button sendChatButton;
    private Button readingModeButton;
    private Button followButton;
    private ScrollView watchPathScroll;
    private LinearLayout watchPathContent;
    private LinearLayout watchPathResults;
    private EditText watchPathGoal;
    private Button createWatchPathButton;
    private final List<Button> watchPathBudgetButtons = new ArrayList<>();
    private int selectedWatchPathBudget = 10;
    private WatchPathPlan currentWatchPath;
    private boolean followPlayback;
    private Button languageButton;
    private String selectedLanguageCode = "";
    private double currentPlaybackTime = -1;
    private String videoTitle = "Video";
    private String videoUrl = "";
    private String currentSummaryText = "";
    private String currentSummaryLabel = "";
    private String currentSummaryPrompt = "";
    private double playbackPositionBeforeSummary = -1;
    private boolean restorePlaybackOnDismiss;
    private boolean transcriptSeekOnDismiss;

    YouTubeSubsDialog(
            Activity activity,
            TranscriptHost host,
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
        dialog.setOnDismissListener(ignored -> {
            followHandler.removeCallbacks(followTick);
            filterHandler.removeCallbacks(filterTick);
            if (restorePlaybackOnDismiss
                    && !transcriptSeekOnDismiss
                    && playbackPositionBeforeSummary > 0) {
                host.seekTo(playbackPositionBeforeSummary);
            }
        });
        loadTranscript();
    }
    private void capturePlaybackPosition(Runnable afterCapture) {
        playbackPositionBeforeSummary = -1;
        host.currentTime(seconds -> {
            if (dialog != null
                    && dialog.isShowing()
                    && Double.isFinite(seconds)
                    && seconds >= 0
                    && seconds <= 604800) {
                playbackPositionBeforeSummary = seconds;
            }
            afterCapture.run();
        });
    }


    private View buildContent() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(14), dp(12), dp(14), dp(12));
        content.setBackground(panelBackground(BACKGROUND, Color.rgb(70, 70, 70)));

        LinearLayout header = horizontalLayout();
        LinearLayout headerText = new LinearLayout(activity);
        headerText.setOrientation(LinearLayout.VERTICAL);
        TextView title = text("Video Subs", 21, Color.WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        headerText.addView(title);
        status = text("Loading subtitles...", 12, MUTED);
        headerText.addView(status);
        header.addView(headerText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton close = new ImageButton(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setContentDescription("Close Video Subs");
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setBackground(panelBackground(PANEL, BUTTON));
        close.setOnClickListener(ignored -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        closeParams.setMarginStart(dp(8));
        header.addView(close, closeParams);
        content.addView(header);
        LinearLayout modeActions = horizontalLayout();
        transcriptButton = button("Transcript");
        transcriptButton.setEnabled(false);
        transcriptButton.setOnClickListener(ignored -> showTranscript());
        watchPathButton = button("WatchPath");
        watchPathButton.setEnabled(false);
        watchPathButton.setOnClickListener(ignored -> showWatchPath());
        addWeighted(modeActions, transcriptButton, 1f, 0);
        addWeighted(modeActions, watchPathButton, 1f, dp(8));
        LinearLayout.LayoutParams modeParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        modeParams.setMargins(0, dp(10), 0, 0);
        content.addView(modeActions, modeParams);
        updateModeButtons(0);


        search = new EditText(activity);
        search.setSingleLine(true);
        search.setHint("Search subtitles...");
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.rgb(130, 130, 130));
        search.setTextSize(14);
        search.setPadding(dp(10), 0, dp(10), 0);
        search.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        search.setBackground(panelBackground(PANEL, Color.rgb(85, 85, 85)));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        searchParams.setMargins(0, dp(8), 0, dp(8));
        content.addView(search, searchParams);

        languageButton = button("Language: Auto");
        languageButton.setEnabled(false);
        languageButton.setOnClickListener(ignored -> showLanguagePicker());
        LinearLayout.LayoutParams languageParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        languageParams.setMargins(0, 0, 0, dp(8));
        content.addView(languageButton, languageParams);

        readingActions = horizontalLayout();
        readingModeButton = button("Lines");
        readingModeButton.setOnClickListener(ignored -> {
            boolean paragraphs = !transcriptAdapter.isParagraphMode();
            transcriptAdapter.setParagraphMode(paragraphs);
            readingModeButton.setText(paragraphs ? "Paragraphs" : "Lines");
            updateTranscriptStatus();
        });
        followButton = button("Follow: Off");
        followButton.setOnClickListener(ignored -> {
            followPlayback = !followPlayback;
            followButton.setText(followPlayback ? "Follow: On" : "Follow: Off");
            if (followPlayback) {
                updateFollowPosition();
            } else {
                followHandler.removeCallbacks(followTick);
                currentPlaybackTime = -1;
                transcriptAdapter.notifyDataSetChanged();
            }
        });
        Button copyTranscriptButton = button("Copy transcript");
        copyTranscriptButton.setOnClickListener(ignored -> copyTranscript());
        addWeighted(readingActions, readingModeButton, 1f, 0);
        addWeighted(readingActions, followButton, 1f, dp(8));
        addWeighted(readingActions, copyTranscriptButton, 1f, dp(8));
        LinearLayout.LayoutParams readingParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        readingParams.setMargins(0, 0, 0, dp(8));
        content.addView(readingActions, readingParams);

        LinearLayout actions = horizontalLayout();
        summaryOneButton = button("Summary One");
        summaryTwoButton = button("Summary Two");
        copySummaryButton = button("Copy summary");
        saveSummaryButton = button("Save summary");
        shareSummaryButton = button("Share summary");
        summaryOneButton.setEnabled(false);
        summaryTwoButton.setEnabled(false);
        summaryOneButton.setOnClickListener(ignored ->
                summarize(settings.getSummaryOnePrompt(), "Summary One"));
        summaryTwoButton.setOnClickListener(ignored ->
                summarize(settings.getSummaryTwoPrompt(), "Summary Two"));
        copySummaryButton.setOnClickListener(ignored -> copySummary());
        saveSummaryButton.setOnClickListener(ignored -> saveSummary());
        shareSummaryButton.setOnClickListener(ignored -> TextShare.showChooser(
                activity,
                videoTitle,
                currentSummaryLabel,
                currentSummaryText,
                videoUrl
        ));
        addWeighted(actions, summaryOneButton, 1f, 0);
        addWeighted(actions, summaryTwoButton, 1f, dp(8));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionParams.setMargins(0, dp(8), 0, 0);
        content.addView(actions, actionParams);

        FrameLayout body = new FrameLayout(activity);
        transcriptList = new ListView(activity);
        transcriptList.setDivider(new ColorDrawable(Color.rgb(50, 50, 50)));
        transcriptList.setDividerHeight(dp(1));
        transcriptAdapter = new TranscriptAdapter();
        transcriptList.setAdapter(transcriptAdapter);
        transcriptList.setOnItemClickListener((parent, view, position, id) -> {
            TranscriptEntry entry = transcriptAdapter.getItem(position);
            transcriptSeekOnDismiss = true;
            host.seekTo(entry.startSeconds);
            dialog.dismiss();
        });
        body.addView(transcriptList, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        summaryOutput = text("", 14, Color.WHITE);
        summaryOutput.setTextIsSelectable(true);
        summaryOutput.setMovementMethod(LinkMovementMethod.getInstance());
        summaryOutput.setLinkTextColor(Color.rgb(90, 180, 255));
        summaryOutput.setLineSpacing(0, 1.18f);
        summaryOutput.setPadding(dp(10), dp(10), dp(10), dp(10));
        summaryContent = new LinearLayout(activity);
        summaryContent.setOrientation(LinearLayout.VERTICAL);
        summaryContent.addView(summaryOutput, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        summaryScroll = new ScrollView(activity);
        summaryScroll.addView(summaryContent);
        summaryScroll.setVisibility(View.GONE);
        body.addView(summaryScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        watchPathContent = buildWatchPathContent();
        watchPathScroll = new ScrollView(activity);
        watchPathScroll.addView(watchPathContent);
        watchPathScroll.setVisibility(View.GONE);
        body.addView(watchPathScroll, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        );
        bodyParams.setMargins(0, dp(8), 0, dp(8));
        content.addView(body, bodyParams);
        chatTitle = text("Continue with a question", 13, Color.WHITE);
        chatTitle.setTypeface(chatTitle.getTypeface(), android.graphics.Typeface.BOLD);
        chatTitle.setPadding(0, 0, 0, dp(8));
        chatTitle.setVisibility(View.GONE);
        content.addView(chatTitle);
        chatRow = horizontalLayout();
        chatInput = new EditText(activity);
        chatInput.setSingleLine(true);
        chatInput.setHint("Ask about this video...");
        chatInput.setTextColor(Color.WHITE);
        chatInput.setHintTextColor(Color.rgb(175, 175, 175));
        chatInput.setTextSize(14);
        chatInput.setPadding(dp(10), 0, dp(10), 0);
        chatInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(2000)});
        chatInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        chatInput.setBackground(panelBackground(PANEL, Color.rgb(85, 85, 85)));
        chatInput.setOnEditorActionListener((view, actionId, event) -> {
            askFollowUp();
            return true;
        });
        chatRow.addView(chatInput, new LinearLayout.LayoutParams(0, dp(44), 1f));
        sendChatButton = button("Send");
        sendChatButton.setContentDescription("Send transcript question");
        sendChatButton.setOnClickListener(ignored -> askFollowUp());
        LinearLayout.LayoutParams sendParams = new LinearLayout.LayoutParams(dp(78), dp(44));
        sendParams.setMarginStart(dp(8));
        chatRow.addView(sendChatButton, sendParams);
        chatRow.setVisibility(View.GONE);
        content.addView(chatRow);


        LinearLayout summaryActions = horizontalLayout();
        copySummaryButton.setVisibility(View.GONE);
        saveSummaryButton.setVisibility(View.GONE);
        addWeighted(summaryActions, copySummaryButton, 1f, 0);
        addWeighted(summaryActions, saveSummaryButton, 1f, dp(8));
        shareSummaryButton.setVisibility(View.GONE);
        addWeighted(summaryActions, shareSummaryButton, 1f, dp(8));
        LinearLayout.LayoutParams summaryActionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        summaryActionParams.setMargins(0, dp(8), 0, 0);
        content.addView(summaryActions, summaryActionParams);

        search.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                filterHandler.removeCallbacks(filterTick);
                filterHandler.postDelayed(filterTick, 150);
            }
        });
        return content;
    }
    private LinearLayout buildWatchPathContent() {
        LinearLayout content = new LinearLayout(activity);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(2), dp(2), dp(2), dp(8));

        TextView intro = text(
                "Choose what you need from this video and how long you have.",
                13,
                MUTED
        );
        content.addView(intro);

        TextView goalLabel = text("Goal", 14, Color.WHITE);
        goalLabel.setTypeface(goalLabel.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams goalLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        goalLabelParams.setMargins(0, dp(12), 0, dp(6));
        content.addView(goalLabel, goalLabelParams);

        watchPathGoal = new EditText(activity);
        watchPathGoal.setSingleLine(true);
        watchPathGoal.setHint("Example: Show me the implementation steps");
        watchPathGoal.setTextColor(Color.WHITE);
        watchPathGoal.setHintTextColor(Color.rgb(175, 175, 175));
        watchPathGoal.setTextSize(14);
        watchPathGoal.setPadding(dp(10), 0, dp(10), 0);
        watchPathGoal.setFilters(new InputFilter[]{new InputFilter.LengthFilter(500)});
        watchPathGoal.setInputType(
                InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        watchPathGoal.setBackground(panelBackground(PANEL, Color.rgb(85, 85, 85)));
        watchPathGoal.setOnEditorActionListener((view, actionId, event) -> {
            generateWatchPath();
            return true;
        });
        content.addView(watchPathGoal, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        ));

        TextView budgetLabel = text("Time", 14, Color.WHITE);
        budgetLabel.setTypeface(budgetLabel.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams budgetLabelParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        budgetLabelParams.setMargins(0, dp(12), 0, dp(6));
        content.addView(budgetLabel, budgetLabelParams);

        LinearLayout budgets = horizontalLayout();
        for (int index = 0; index < WATCH_PATH_BUDGETS.length; index++) {
            int budget = WATCH_PATH_BUDGETS[index];
            Button budgetButton = button(budget + " min");
            budgetButton.setContentDescription(budget + " minute WatchPath");
            budgetButton.setOnClickListener(ignored -> {
                selectedWatchPathBudget = budget;
                updateWatchPathBudgetButtons();
            });
            watchPathBudgetButtons.add(budgetButton);
            addWeighted(budgets, budgetButton, 1f, index == 0 ? 0 : dp(8));
        }
        updateWatchPathBudgetButtons();
        content.addView(budgets);

        createWatchPathButton = button("Create WatchPath");
        createWatchPathButton.setBackground(panelBackground(ACTIVE, ACTIVE));
        createWatchPathButton.setOnClickListener(ignored -> generateWatchPath());
        LinearLayout.LayoutParams createParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        createParams.setMargins(0, dp(12), 0, 0);
        content.addView(createWatchPathButton, createParams);

        watchPathResults = new LinearLayout(activity);
        watchPathResults.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams resultsParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        resultsParams.setMargins(0, dp(8), 0, 0);
        content.addView(watchPathResults, resultsParams);
        return content;
    }

    private void updateWatchPathBudgetButtons() {
        for (int index = 0; index < watchPathBudgetButtons.size(); index++) {
            int budget = WATCH_PATH_BUDGETS[index];
            boolean selected = budget == selectedWatchPathBudget;
            watchPathBudgetButtons.get(index).setBackground(panelBackground(
                    selected ? ACTIVE : BUTTON,
                    selected ? ACTIVE : Color.rgb(85, 85, 85)
            ));
        }
    }

    private void updateModeButtons(int selectedMode) {
        boolean transcriptSelected = selectedMode == 0;
        boolean watchPathSelected = selectedMode == 1;
        transcriptButton.setBackground(panelBackground(
                transcriptSelected ? ACTIVE : BUTTON,
                transcriptSelected ? ACTIVE : Color.rgb(85, 85, 85)
        ));
        watchPathButton.setBackground(panelBackground(
                watchPathSelected ? ACTIVE : BUTTON,
                watchPathSelected ? ACTIVE : Color.rgb(85, 85, 85)
        ));
    }

    private void applyTranscriptFilter() {
        transcriptAdapter.filter(search.getText().toString());
        updateTranscriptStatus();
    }
    private void generateWatchPath() {
        if (entries.isEmpty()) {
            Toast.makeText(activity, "No subtitles found", Toast.LENGTH_SHORT).show();
            return;
        }
        String goal = watchPathGoal.getText().toString().trim();
        if (goal.isEmpty()) {
            Toast.makeText(activity, "Enter what you need from this video", Toast.LENGTH_SHORT).show();
            return;
        }
        String prompt = settings.getWatchPathPrompt();
        if (prompt == null || prompt.trim().isEmpty()) {
            Toast.makeText(activity, "WatchPath prompt is empty", Toast.LENGTH_LONG).show();
            return;
        }
        String modelId = settings.getModelId();
        if (modelId == null || modelId.trim().isEmpty()) {
            Toast.makeText(activity, "Configure OpenRouter in Settings first", Toast.LENGTH_LONG).show();
            return;
        }

        final int budgetMinutes = selectedWatchPathBudget;
        final double transcriptDuration;
        final String userMessage;
        try {
            transcriptDuration = WatchPathPlan.transcriptDuration(entries);
            userMessage = WatchPathPlan.buildUserMessage(
                    host.sourceLabel(),
                    videoTitle,
                    videoUrl,
                    goal,
                    budgetMinutes,
                    entries
            );
        } catch (IllegalArgumentException error) {
            Toast.makeText(activity, error.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        String cacheKey = summaryCacheKey(
                "WatchPath",
                prompt,
                modelId,
                videoUrl,
                userMessage
        );
        currentWatchPath = null;
        watchPathResults.removeAllViews();

        try {
            String cached = savedSummaryStore.loadCachedSummary(cacheKey);
            if (cached != null) {
                WatchPathPlan plan = WatchPathPlan.parse(
                        cached,
                        videoUrl,
                        goal,
                        budgetMinutes,
                        transcriptDuration
                );
                renderWatchPathPlan(plan);
                status.setText("WatchPath | " + modelId + " | cached");
                return;
            }
        } catch (RuntimeException ignored) {
            // Invalid or unavailable cache data falls through to a fresh request.
        }

        final String apiKey;
        try {
            apiKey = settings.getApiKey();
        } catch (GeneralSecurityException error) {
            Toast.makeText(activity, "Stored API key could not be decrypted", Toast.LENGTH_LONG).show();
            return;
        }
        if (apiKey.trim().isEmpty()) {
            Toast.makeText(activity, "Configure OpenRouter in Settings first", Toast.LENGTH_LONG).show();
            return;
        }

        setWatchPathBusy(true);
        status.setText("Creating WatchPath with " + modelId);
        executor.execute(() -> {
            try {
                String response = client.summarize(apiKey, modelId, prompt, userMessage);
                WatchPathPlan plan = WatchPathPlan.parse(
                        response,
                        videoUrl,
                        goal,
                        budgetMinutes,
                        transcriptDuration
                );
                boolean cacheStored;
                try {
                    savedSummaryStore.cacheSummary(cacheKey, response);
                    cacheStored = true;
                } catch (RuntimeException ignored) {
                    cacheStored = false;
                }
                boolean finalCacheStored = cacheStored;
                activity.runOnUiThread(() -> {
                    if (dialog != null && dialog.isShowing()) {
                        setWatchPathBusy(false);
                        renderWatchPathPlan(plan);
                        status.setText("WatchPath | "
                                + modelId
                                + (finalCacheStored ? " | saved for reuse" : ""));
                    }
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (dialog != null && dialog.isShowing()) {
                        setWatchPathBusy(false);
                        String message = safeMessage(error, "WatchPath failed");
                        status.setText("WatchPath failed: " + message);
                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void setWatchPathBusy(boolean busy) {
        createWatchPathButton.setEnabled(!busy);
        watchPathGoal.setEnabled(!busy);
        for (Button budgetButton : watchPathBudgetButtons) {
            budgetButton.setEnabled(!busy);
        }
        summaryOneButton.setEnabled(!busy && !entries.isEmpty());
        summaryTwoButton.setEnabled(!busy && !entries.isEmpty());
    }

    private void renderWatchPathPlan(WatchPathPlan plan) {
        currentWatchPath = plan;
        watchPathResults.removeAllViews();

        TextView routeTitle = text(
                "Proposed route • "
                        + WatchPathPlan.timestamp(plan.selectedDurationSeconds())
                        + " total",
                16,
                Color.WHITE
        );
        routeTitle.setTypeface(routeTitle.getTypeface(), android.graphics.Typeface.BOLD);
        watchPathResults.addView(routeTitle);

        for (WatchPathPlan.Segment segment : plan.segments) {
            LinearLayout row = horizontalLayout();
            row.setPadding(dp(8), dp(8), dp(8), dp(8));
            row.setBackground(panelBackground(PANEL, Color.rgb(70, 70, 70)));

            LinearLayout details = new LinearLayout(activity);
            details.setOrientation(LinearLayout.VERTICAL);
            TextView heading = text(
                    WatchPathPlan.timestamp(segment.startSeconds)
                            + "–"
                            + WatchPathPlan.timestamp(segment.endSeconds)
                            + "  "
                            + segment.title,
                    14,
                    Color.WHITE
            );
            heading.setTypeface(heading.getTypeface(), android.graphics.Typeface.BOLD);
            details.addView(heading);
            TextView reason = text(segment.reason, 12, MUTED);
            reason.setPadding(0, dp(4), 0, 0);
            details.addView(reason);
            row.addView(details, new LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
            ));

            Button play = button("Play");
            play.setContentDescription("Play " + segment.title);
            play.setOnClickListener(ignored -> {
                transcriptSeekOnDismiss = true;
                restorePlaybackOnDismiss = false;
                host.seekTo(segment.startSeconds);
                dialog.dismiss();
            });
            LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(dp(72), dp(44));
            playParams.setMarginStart(dp(8));
            row.addView(play, playParams);

            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            rowParams.setMargins(0, dp(8), 0, 0);
            watchPathResults.addView(row, rowParams);
        }

        LinearLayout actions = horizontalLayout();
        Button skipped = button("What I skipped");
        skipped.setOnClickListener(ignored -> showSkippedWatchPathRanges());
        addWeighted(actions, skipped, 1f, 0);
        Button start = button("Start WatchPath");
        start.setBackground(panelBackground(ACTIVE, ACTIVE));
        start.setOnClickListener(ignored -> {
            if (currentWatchPath == null) {
                return;
            }
            transcriptSeekOnDismiss = true;
            restorePlaybackOnDismiss = false;
            host.startWatchPath(currentWatchPath);
            dialog.dismiss();
        });
        addWeighted(actions, start, 1f, dp(8));
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        actionParams.setMargins(0, dp(10), 0, 0);
        watchPathResults.addView(actions, actionParams);
    }

    private void showSkippedWatchPathRanges() {
        if (currentWatchPath == null) {
            return;
        }
        List<WatchPathPlan.Gap> gaps = currentWatchPath.skippedRanges();
        if (gaps.isEmpty()) {
            Toast.makeText(activity, "This route covers the full transcript", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] labels = new String[gaps.size()];
        for (int index = 0; index < gaps.size(); index++) {
            WatchPathPlan.Gap gap = gaps.get(index);
            labels[index] = WatchPathPlan.timestamp(gap.startSeconds)
                    + "–"
                    + WatchPathPlan.timestamp(gap.endSeconds)
                    + "  ("
                    + WatchPathPlan.timestamp(gap.endSeconds - gap.startSeconds)
                    + ")";
        }
        new AlertDialog.Builder(activity)
                .setTitle("Skipped sections")
                .setItems(labels, (picker, index) -> {
                    WatchPathPlan.Gap gap = gaps.get(index);
                    transcriptSeekOnDismiss = true;
                    restorePlaybackOnDismiss = false;
                    host.seekTo(gap.startSeconds);
                    picker.dismiss();
                    dialog.dismiss();
                })
                .setNegativeButton("Close", null)
                .show();
    }



    private void loadTranscript() {
        loadTranscript("");
    }

    private void loadTranscript(String languageCode) {
        status.setText("Loading subtitles...");
        selectedLanguageCode = languageCode == null ? "" : languageCode;
        host.loadTranscript(selectedLanguageCode, new TranscriptCallback() {
            @Override
            public void onLoaded(List<TranscriptEntry> loaded, String title, String url) {
                if (dialog == null || !dialog.isShowing()) {
                    return;
                }
                entries.clear();
                entries.addAll(loaded);
                videoTitle = title == null || title.trim().isEmpty() ? "Video" : title;
                videoUrl = url == null ? "" : url;
                transcriptAdapter.setEntries(entries);
                summaryOneButton.setEnabled(!entries.isEmpty());
                summaryTwoButton.setEnabled(!entries.isEmpty());
                updateTranscriptStatus();
                transcriptButton.setEnabled(!entries.isEmpty());
                watchPathButton.setEnabled(!entries.isEmpty());
                currentWatchPath = null;
                if (watchPathResults != null) {
                    watchPathResults.removeAllViews();
                }
                if (captionOptions.isEmpty()) {
                    loadCaptionOptions();
                }
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

    private void loadCaptionOptions() {
        host.loadCaptionOptions(new CaptionOptionsCallback() {
            @Override
            public void onLoaded(List<CaptionOption> options) {
                if (dialog == null || !dialog.isShowing()) {
                    return;
                }
                captionOptions.clear();
                captionOptions.addAll(options);
                languageButton.setEnabled(!captionOptions.isEmpty());
            }

            @Override
            public void onError(String message) {
                // The active caption remains available when the language list cannot load.
            }
        });
    }

    private void showLanguagePicker() {
        List<CaptionOption> choices = new ArrayList<>();
        choices.add(new CaptionOption("", "Auto"));
        choices.addAll(captionOptions);
        String[] labels = new String[choices.size()];
        int selectedIndex = 0;
        for (int index = 0; index < choices.size(); index++) {
            CaptionOption option = choices.get(index);
            labels[index] = option.label;
            if (option.languageCode.equals(selectedLanguageCode)) {
                selectedIndex = index;
            }
        }
        new AlertDialog.Builder(activity)
                .setTitle("Caption language")
                .setSingleChoiceItems(labels, selectedIndex, (picker, index) -> {
                    CaptionOption option = choices.get(index);
                    languageButton.setText("Language: " + option.label);
                    picker.dismiss();
                    loadTranscript(option.languageCode);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void summarize(String prompt, String summaryName) {
        if (entries.isEmpty()) {
            Toast.makeText(activity, "No subtitles found", Toast.LENGTH_SHORT).show();
            return;
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            Toast.makeText(activity, summaryName + " prompt is empty", Toast.LENGTH_LONG).show();
            return;
        }

        String modelId = settings.getModelId();
        if (modelId == null || modelId.trim().isEmpty()) {
            Toast.makeText(activity, "Configure OpenRouter in Settings first", Toast.LENGTH_LONG).show();
            return;
        }

        String userMessage = buildUserMessage();
        String cacheKey = summaryCacheKey(summaryName, prompt, modelId, videoUrl, userMessage);
        currentSummaryText = "";
        currentSummaryLabel = "";
        currentSummaryPrompt = "";
        chatTurns.clear();
        if (chatInput != null) {
            chatInput.setText("");
        }

        try {
            String cachedSummary = savedSummaryStore.loadCachedSummary(cacheKey);
            if (cachedSummary != null) {
                summaryOneButton.setEnabled(false);
                summaryTwoButton.setEnabled(false);
                status.setText("Opening cached " + summaryName);
                capturePlaybackPosition(() -> {
                    if (dialog != null && dialog.isShowing()) {
                        restorePlaybackOnDismiss = playbackPositionBeforeSummary > 0;
                        useSummary(cachedSummary, summaryName, prompt);
                        status.setText(summaryName + " | " + modelId + " | cached | ask below");
                    }
                });
                return;
            }
        } catch (RuntimeException ignored) {
            // A cache read failure must not prevent a fresh summary.
        }

        final String apiKey;
        try {
            apiKey = settings.getApiKey();
        } catch (GeneralSecurityException error) {
            Toast.makeText(activity, "Stored API key could not be decrypted", Toast.LENGTH_LONG).show();
            return;
        }
        if (apiKey.trim().isEmpty()) {
            Toast.makeText(activity, "Configure OpenRouter in Settings first", Toast.LENGTH_LONG).show();
            return;
        }
        summaryOneButton.setEnabled(false);
        summaryTwoButton.setEnabled(false);
        status.setText("Preparing " + summaryName);
        capturePlaybackPosition(() -> {
            if (dialog == null || !dialog.isShowing()) {
                return;
            }
            restorePlaybackOnDismiss = playbackPositionBeforeSummary > 0;
            status.setText("Sending transcript to " + modelId);

            executor.execute(() -> {
                try {
                    String result = client.summarize(apiKey, modelId, prompt, userMessage);
                    boolean cacheStored;
                    try {
                        savedSummaryStore.cacheSummary(cacheKey, result);
                        cacheStored = true;
                    } catch (RuntimeException ignored) {
                        cacheStored = false;
                    }
                    boolean finalCacheStored = cacheStored;
                    activity.runOnUiThread(() -> {
                        if (dialog != null && dialog.isShowing()) {
                            useSummary(result, summaryName, prompt);
                            status.setText(summaryName
                                    + " | "
                                    + modelId
                                    + (finalCacheStored ? " | saved for reuse | ask below" : " | ask below"));
                            if (!finalCacheStored) {
                                Toast.makeText(
                                        activity,
                                        "Summary generated but could not be cached",
                                        Toast.LENGTH_LONG
                                ).show();
                            }
                        }
                    });
                } catch (Exception error) {
                    activity.runOnUiThread(() -> {
                        if (dialog != null && dialog.isShowing()) {
                            String message = safeMessage(error, "Summary failed");
                            currentSummaryText = "";
                            currentSummaryLabel = "";
                            showSummary(message, false);
                            status.setText("Summary failed");
                            summaryOneButton.setEnabled(true);
                            summaryTwoButton.setEnabled(true);
                            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                        }
                    });
                }
            });
        });
    }

    private void useSummary(String result, String summaryName, String prompt) {
        currentSummaryText = result;
        currentSummaryLabel = summaryName;
        currentSummaryPrompt = prompt;
        saveSummaryButton.setText("Save summary");
        saveSummaryButton.setEnabled(true);
        showSummary(result, true);
        summaryOneButton.setEnabled(true);
        summaryTwoButton.setEnabled(true);
    }


    private void askFollowUp() {
        String question = chatInput.getText().toString().trim();
        if (question.isEmpty() || currentSummaryText.isEmpty()) {
            return;
        }
        if (currentSummaryPrompt.trim().isEmpty()) {
            Toast.makeText(activity, currentSummaryLabel + " prompt is empty", Toast.LENGTH_LONG).show();
            return;
        }

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

        List<OpenRouterClient.Message> messages = new ArrayList<>();
        messages.add(new OpenRouterClient.Message("system", currentSummaryPrompt));
        messages.add(new OpenRouterClient.Message("user", buildUserMessage()));
        messages.add(new OpenRouterClient.Message("assistant", currentSummaryText));
        for (ChatTurn turn : chatTurns) {
            messages.add(new OpenRouterClient.Message("user", "Question:\n" + turn.question));
            messages.add(new OpenRouterClient.Message("assistant", turn.answer));
        }
        messages.add(new OpenRouterClient.Message("user", "Question:\n" + question));
        setChatEnabled(false);
        status.setText("Asking " + modelId + "...");
        executor.execute(() -> {
            try {
                String answer = client.generate(apiKey, modelId, messages);
                activity.runOnUiThread(() -> {
                    if (dialog != null && dialog.isShowing()) {
                        chatTurns.add(new ChatTurn(question, answer));
                        chatInput.setText("");
                        renderConversation();
                        status.setText(currentSummaryLabel + " chat | " + modelId);
                        setChatEnabled(true);
                    }
                });
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (dialog != null && dialog.isShowing()) {
                        String message = safeMessage(error, "Question failed");
                        status.setText("Question failed");
                        setChatEnabled(true);
                        Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void setChatEnabled(boolean enabled) {
        chatInput.setEnabled(enabled);
        sendChatButton.setEnabled(enabled);
        summaryOneButton.setEnabled(enabled);
        summaryTwoButton.setEnabled(enabled);
    }

    private void renderConversation() {
        float density = activity.getResources().getDisplayMetrics().density;
        summaryOutput.setText(MarkdownRenderer.render(currentSummaryText, density));
        if (summaryContent.getChildCount() > 1) {
            summaryContent.removeViews(1, summaryContent.getChildCount() - 1);
        }
        for (ChatTurn turn : chatTurns) {
            TextView userMessage = text("", 14, Color.WHITE);
            userMessage.setTextIsSelectable(true);
            userMessage.setMovementMethod(LinkMovementMethod.getInstance());
            userMessage.setLinkTextColor(Color.rgb(90, 180, 255));
            userMessage.setLineSpacing(0, 1.18f);
            userMessage.setPadding(dp(8), dp(8), dp(8), dp(8));
            userMessage.setBackground(panelBackground(
                    Color.rgb(58, 18, 28),
                    Color.rgb(96, 35, 50)
            ));
            userMessage.setText(MarkdownRenderer.render(
                    "**You**\n\n" + turn.question,
                    density
            ));
            LinearLayout.LayoutParams userParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            userParams.setMargins(dp(10), dp(10), dp(10), dp(4));
            summaryContent.addView(userMessage, userParams);

            TextView aiMessage = text("", 14, Color.WHITE);
            aiMessage.setTextIsSelectable(true);
            aiMessage.setMovementMethod(LinkMovementMethod.getInstance());
            aiMessage.setLinkTextColor(Color.rgb(90, 180, 255));
            aiMessage.setLineSpacing(0, 1.18f);
            aiMessage.setPadding(dp(8), dp(4), dp(8), dp(4));
            aiMessage.setText(MarkdownRenderer.render(
                    "**AI**\n\n" + turn.answer,
                    density
            ));
            LinearLayout.LayoutParams aiParams = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            aiParams.setMargins(dp(2), dp(4), dp(2), 0);
            summaryContent.addView(aiMessage, aiParams);
        }
        summaryScroll.post(() -> summaryScroll.fullScroll(View.FOCUS_DOWN));
    }

    private String buildUserMessage() {
        StringBuilder transcript = new StringBuilder();
        for (TranscriptEntry entry : entries) {
            transcript.append(entry.timestamp()).append(' ').append(entry.text).append('\n');
        }
        return "Source: " + host.sourceLabel() + "\nTitle: "
                + videoTitle
                + "\nURL: "
                + videoUrl
                + "\n\nTranscript:\n"
                + transcript;
    }

    private static String summaryCacheKey(
            String summaryName,
            String prompt,
            String modelId,
            String sourceUrl,
            String userMessage
    ) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException error) {
            throw new IllegalStateException("SHA-256 is unavailable", error);
        }
        updateDigest(digest, summaryName);
        updateDigest(digest, prompt);
        updateDigest(digest, modelId);
        updateDigest(digest, sourceUrl);
        updateDigest(digest, userMessage);

        byte[] hash = digest.digest();
        char[] encoded = new char[hash.length * 2];
        char[] hexadecimal = "0123456789abcdef".toCharArray();
        for (int index = 0; index < hash.length; index++) {
            int value = hash[index] & 0xff;
            encoded[index * 2] = hexadecimal[value >>> 4];
            encoded[index * 2 + 1] = hexadecimal[value & 0x0f];
        }
        return new String(encoded);
    }

    private static void updateDigest(MessageDigest digest, String value) {
        byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
        digest.update((byte) (bytes.length >>> 24));
        digest.update((byte) (bytes.length >>> 16));
        digest.update((byte) (bytes.length >>> 8));
        digest.update((byte) bytes.length);
        digest.update(bytes);
    }



    private void showSummary(String value, boolean actionsAvailable) {
        transcriptList.setVisibility(View.GONE);
        search.setVisibility(View.GONE);
        languageButton.setVisibility(View.GONE);
        readingActions.setVisibility(View.GONE);
        watchPathScroll.setVisibility(View.GONE);
        updateModeButtons(-1);
        summaryOutput.setText(MarkdownRenderer.render(
                value,
                activity.getResources().getDisplayMetrics().density
        ));
        if (summaryContent.getChildCount() > 1) {
            summaryContent.removeViews(1, summaryContent.getChildCount() - 1);
        }
        summaryScroll.setVisibility(View.VISIBLE);
        chatTitle.setVisibility(actionsAvailable ? View.VISIBLE : View.GONE);
        chatRow.setVisibility(actionsAvailable ? View.VISIBLE : View.GONE);
        copySummaryButton.setVisibility(actionsAvailable ? View.VISIBLE : View.GONE);
        saveSummaryButton.setVisibility(actionsAvailable ? View.VISIBLE : View.GONE);
        shareSummaryButton.setVisibility(actionsAvailable ? View.VISIBLE : View.GONE);
    }

    private void updateTranscriptStatus() {
        String unit = transcriptAdapter.isParagraphMode() ? "paragraphs" : "subtitles";
        status.setText(transcriptAdapter.getCount()
                + " of "
                + transcriptAdapter.totalCount()
                + " "
                + unit
                + " | tap a line to seek");
    }

    private void copyTranscript() {
        if (entries.isEmpty()) {
            return;
        }
        StringBuilder output = new StringBuilder();
        for (TranscriptEntry entry : entries) {
            if (output.length() > 0) {
                output.append('\n');
            }
            output.append(entry.timestamp()).append(' ').append(entry.text);
        }
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("SpeedyWatch transcript", output.toString()));
        Toast.makeText(activity, "Transcript copied", Toast.LENGTH_SHORT).show();
    }

    private void updateFollowPosition() {
        followHandler.removeCallbacks(followTick);
        if (!followPlayback || dialog == null || !dialog.isShowing()) {
            return;
        }
        host.currentTime(seconds -> {
            if (!followPlayback || dialog == null || !dialog.isShowing()) {
                return;
            }
            currentPlaybackTime = seconds;
            int active = transcriptAdapter.activePosition(seconds);
            transcriptAdapter.notifyDataSetChanged();
            if (active >= 0 && search.getText().toString().trim().isEmpty()) {
                transcriptList.smoothScrollToPosition(active);
            }
            followHandler.postDelayed(followTick, 750);
        });
    }

    private void showTranscript() {
        summaryScroll.setVisibility(View.GONE);
        watchPathScroll.setVisibility(View.GONE);
        chatTitle.setVisibility(View.GONE);
        chatRow.setVisibility(View.GONE);
        copySummaryButton.setVisibility(View.GONE);
        saveSummaryButton.setVisibility(View.GONE);
        shareSummaryButton.setVisibility(View.GONE);
        transcriptList.setVisibility(View.VISIBLE);
        search.setVisibility(View.VISIBLE);
        languageButton.setVisibility(View.VISIBLE);
        readingActions.setVisibility(View.VISIBLE);
        updateModeButtons(0);
        updateTranscriptStatus();
    }

    private void showWatchPath() {
        if (entries.isEmpty()) {
            Toast.makeText(activity, "No subtitles found", Toast.LENGTH_SHORT).show();
            return;
        }
        transcriptList.setVisibility(View.GONE);
        search.setVisibility(View.GONE);
        languageButton.setVisibility(View.GONE);
        readingActions.setVisibility(View.GONE);
        summaryScroll.setVisibility(View.GONE);
        chatTitle.setVisibility(View.GONE);
        chatRow.setVisibility(View.GONE);
        copySummaryButton.setVisibility(View.GONE);
        saveSummaryButton.setVisibility(View.GONE);
        shareSummaryButton.setVisibility(View.GONE);
        watchPathScroll.setVisibility(View.VISIBLE);
        updateModeButtons(1);
        status.setText(currentWatchPath == null
                ? "Create a route from this transcript"
                : "WatchPath ready");
    }

    private void copySummary() {
        String value = currentSummaryText;
        if (value.trim().isEmpty()) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(ClipData.newPlainText("SpeedyWatch summary", value));
        Toast.makeText(activity, "Summary copied", Toast.LENGTH_SHORT).show();
    }

    private void saveSummary() {
        if (currentSummaryText.trim().isEmpty() || currentSummaryLabel.trim().isEmpty()) {
            return;
        }
        try {
            savedSummaryStore.save(
                    videoTitle,
                    currentSummaryLabel,
                    currentSummaryText,
                    videoUrl
            );
            Toast.makeText(activity, "Summary saved", Toast.LENGTH_SHORT).show();
        } catch (IllegalArgumentException error) {
            Toast.makeText(activity, safeMessage(error, "Summary could not be saved"), Toast.LENGTH_LONG).show();
        } catch (RuntimeException error) {
            Toast.makeText(activity, "Summary could not be saved", Toast.LENGTH_LONG).show();
        }
    }

    private void addWeighted(LinearLayout row, Button button, float weight, int marginStart) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), weight);
        params.setMarginStart(marginStart);
        row.addView(button, params);
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
        button.setTextSize(12);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(8), 0, dp(8), 0);
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

    private static final class ChatTurn {
        final String question;
        final String answer;

        ChatTurn(String question, String answer) {
            this.question = question;
            this.answer = answer;
        }
    }

    static final class CaptionOption {
        final String languageCode;
        final String label;

        CaptionOption(String languageCode, String label) {
            this.languageCode = languageCode;
            this.label = label;
        }
    }

    private final class TranscriptAdapter extends BaseAdapter {

        private final List<TranscriptEntry> all = new ArrayList<>();
        private final List<TranscriptEntry> visible = new ArrayList<>();
        private final List<TranscriptEntry> source = new ArrayList<>();
        private boolean paragraphMode;

        void setEntries(List<TranscriptEntry> loaded) {
            source.clear();
            source.addAll(loaded);
            rebuild();
        }

        void setParagraphMode(boolean enabled) {
            if (paragraphMode == enabled) {
                return;
            }
            paragraphMode = enabled;
            rebuild();
        }

        boolean isParagraphMode() {
            return paragraphMode;
        }

        int totalCount() {
            return all.size();
        }

        int activePosition(double seconds) {
            for (int index = 0; index < visible.size(); index++) {
                TranscriptEntry entry = visible.get(index);
                double end = index + 1 < visible.size()
                        ? visible.get(index + 1).startSeconds
                        : entry.startSeconds + Math.max(entry.durationSeconds, 5);
                if (seconds >= entry.startSeconds && seconds < end) {
                    return index;
                }
            }
            return -1;
        }

        private void rebuild() {
            all.clear();
            if (!paragraphMode) {
                all.addAll(source);
            } else if (!source.isEmpty()) {
                double start = source.get(0).startSeconds;
                double end = start + source.get(0).durationSeconds;
                StringBuilder text = new StringBuilder(source.get(0).text);
                int count = 1;
                for (int index = 1; index < source.size(); index++) {
                    TranscriptEntry entry = source.get(index);
                    double gap = entry.startSeconds - end;
                    if (count >= 4 || gap > 2.5 || entry.startSeconds - start > 30) {
                        all.add(new TranscriptEntry(start, Math.max(0, end - start), text.toString()));
                        start = entry.startSeconds;
                        text.setLength(0);
                        text.append(entry.text);
                        count = 1;
                    } else {
                        text.append(' ').append(entry.text);
                        count++;
                    }
                    end = Math.max(end, entry.startSeconds + entry.durationSeconds);
                }
                all.add(new TranscriptEntry(start, Math.max(0, end - start), text.toString()));
            }
            filter(search == null ? "" : search.getText().toString());
        }

        void filter(String query) {
            String normalized = query == null ? "" : query.toLowerCase(Locale.US).trim();
            visible.clear();
            for (TranscriptEntry entry : all) {
                String searchable = (entry.timestamp() + " " + entry.text).toLowerCase(Locale.US);
                if (normalized.isEmpty() || searchable.contains(normalized)) {
                    visible.add(entry);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public TranscriptEntry getItem(int position) {
            return visible.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView timestamp;
            TextView line;
            if (convertView instanceof LinearLayout existing) {
                row = existing;
                timestamp = (TextView) row.getChildAt(0);
                line = (TextView) row.getChildAt(1);
            } else {
                row = horizontalLayout();
                row.setGravity(Gravity.TOP);
                row.setPadding(dp(8), dp(9), dp(8), dp(9));
                timestamp = text("", 12, ACTIVE);
                timestamp.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
                row.addView(timestamp, new LinearLayout.LayoutParams(dp(62), ViewGroup.LayoutParams.WRAP_CONTENT));
                line = text("", 14, Color.rgb(225, 225, 225));
                line.setLineSpacing(0, 1.1f);
                row.addView(line, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            }
            TranscriptEntry entry = getItem(position);
            timestamp.setText(entry.timestamp());
            line.setText(entry.text);
            row.setBackgroundColor(position == activePosition(currentPlaybackTime)
                    ? Color.rgb(58, 24, 31) : BACKGROUND);
            return row;
        }
    }

    private abstract static class SimpleTextWatcher implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence value, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence value, int start, int before, int count) {
        }
    }
}
