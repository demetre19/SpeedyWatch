package com.speedywatch.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
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

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

final class YouTubeSubsDialog {
    interface TranscriptHost {
        void loadTranscript(TranscriptCallback callback);
        void seekTo(double seconds);
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

    private final Activity activity;
    private final TranscriptHost host;
    private final SpeedyWatchSettings settings;
    private final OpenRouterClient client;
    private final ExecutorService executor;
    private final SavedSummaryStore savedSummaryStore;
    private final List<TranscriptEntry> entries = new ArrayList<>();

    private Dialog dialog;
    private TextView status;
    private EditText search;
    private ListView transcriptList;
    private TranscriptAdapter transcriptAdapter;
    private ScrollView summaryScroll;
    private TextView summaryOutput;
    private Button summaryOneButton;
    private Button summaryTwoButton;
    private Button transcriptButton;
    private Button copySummaryButton;
    private Button saveSummaryButton;
    private String videoTitle = "YouTube Video";
    private String videoUrl = "";
    private String currentSummaryText = "";
    private String currentSummaryLabel = "";

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
        TextView title = text("YouTube Subs", 21, Color.WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        headerText.addView(title);
        status = text("Loading subtitles...", 12, MUTED);
        headerText.addView(status);
        header.addView(headerText, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton close = new ImageButton(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setContentDescription("Close YouTube Subs");
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setBackground(panelBackground(PANEL, BUTTON));
        close.setOnClickListener(ignored -> dialog.dismiss());
        header.addView(close, new LinearLayout.LayoutParams(dp(42), dp(42)));
        content.addView(header);

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
        searchParams.setMargins(0, dp(10), 0, dp(8));
        content.addView(search, searchParams);

        LinearLayout actions = horizontalLayout();
        summaryOneButton = button("Summary One");
        summaryTwoButton = button("Summary Two");
        transcriptButton = button("Transcript");
        copySummaryButton = button("Copy summary");
        saveSummaryButton = button("Save summary");
        summaryOneButton.setEnabled(false);
        summaryTwoButton.setEnabled(false);
        summaryOneButton.setOnClickListener(ignored ->
                summarize(settings.getSummaryOnePrompt(), "Summary One"));
        summaryTwoButton.setOnClickListener(ignored ->
                summarize(settings.getSummaryTwoPrompt(), "Summary Two"));
        transcriptButton.setOnClickListener(ignored -> showTranscript());
        copySummaryButton.setOnClickListener(ignored -> copySummary());
        saveSummaryButton.setOnClickListener(ignored -> saveSummary());
        addWeighted(actions, summaryOneButton, 1f, 0);
        addWeighted(actions, summaryTwoButton, 1f, dp(6));
        addWeighted(actions, transcriptButton, 1f, dp(6));
        content.addView(actions);

        FrameLayout body = new FrameLayout(activity);
        transcriptList = new ListView(activity);
        transcriptList.setDivider(new ColorDrawable(Color.rgb(50, 50, 50)));
        transcriptList.setDividerHeight(dp(1));
        transcriptAdapter = new TranscriptAdapter();
        transcriptList.setAdapter(transcriptAdapter);
        transcriptList.setOnItemClickListener((parent, view, position, id) -> {
            TranscriptEntry entry = transcriptAdapter.getItem(position);
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
        summaryScroll = new ScrollView(activity);
        summaryScroll.addView(summaryOutput);
        summaryScroll.setVisibility(View.GONE);
        body.addView(summaryScroll, new FrameLayout.LayoutParams(
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

        LinearLayout summaryActions = horizontalLayout();
        copySummaryButton.setVisibility(View.GONE);
        saveSummaryButton.setVisibility(View.GONE);
        addWeighted(summaryActions, copySummaryButton, 1f, 0);
        addWeighted(summaryActions, saveSummaryButton, 1f, dp(6));
        content.addView(summaryActions);

        search.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                transcriptAdapter.filter(editable.toString());
                status.setText(transcriptAdapter.getCount() + " of " + entries.size() + " subtitles");
            }
        });
        return content;
    }

    private void loadTranscript() {
        status.setText("Loading subtitles...");
        host.loadTranscript(new TranscriptCallback() {
            @Override
            public void onLoaded(List<TranscriptEntry> loaded, String title, String url) {
                if (dialog == null || !dialog.isShowing()) {
                    return;
                }
                entries.clear();
                entries.addAll(loaded);
                videoTitle = title == null || title.trim().isEmpty() ? "YouTube Video" : title;
                videoUrl = url == null ? "" : url;
                transcriptAdapter.setEntries(entries);
                summaryOneButton.setEnabled(!entries.isEmpty());
                summaryTwoButton.setEnabled(!entries.isEmpty());
                status.setText(entries.size() + " subtitles | tap a line to seek");
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

    private void summarize(String prompt, String summaryName) {
        if (entries.isEmpty()) {
            Toast.makeText(activity, "No subtitles found", Toast.LENGTH_SHORT).show();
            return;
        }
        if (prompt == null || prompt.trim().isEmpty()) {
            Toast.makeText(activity, summaryName + " prompt is empty", Toast.LENGTH_LONG).show();
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

        String userMessage = buildUserMessage();
        summaryOneButton.setEnabled(false);
        summaryTwoButton.setEnabled(false);
        currentSummaryText = "";
        currentSummaryLabel = "";
        showSummary("Creating " + summaryName + "...", false);
        status.setText("Sending transcript to " + modelId);

        executor.execute(() -> {
            try {
                String result = client.summarize(apiKey, modelId, prompt, userMessage);
                activity.runOnUiThread(() -> {
                    if (dialog != null && dialog.isShowing()) {
                        currentSummaryText = result;
                        currentSummaryLabel = summaryName;
                        saveSummaryButton.setText("Save summary");
                        saveSummaryButton.setEnabled(true);
                        showSummary(result, true);
                        status.setText(summaryName + " | " + modelId);
                        summaryOneButton.setEnabled(true);
                        summaryTwoButton.setEnabled(true);
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
    }

    private String buildUserMessage() {
        StringBuilder transcript = new StringBuilder();
        for (TranscriptEntry entry : entries) {
            transcript.append(entry.timestamp()).append(' ').append(entry.text).append('\n');
        }
        return "Source: YouTube Subtitles\nTitle: "
                + videoTitle
                + "\nURL: "
                + videoUrl
                + "\n\nTranscript:\n"
                + transcript;
    }

    private void showSummary(String value, boolean actionsAvailable) {
        transcriptList.setVisibility(View.GONE);
        search.setVisibility(View.GONE);
        summaryOutput.setText(MarkdownRenderer.render(
                value,
                activity.getResources().getDisplayMetrics().density
        ));
        summaryScroll.setVisibility(View.VISIBLE);
        transcriptButton.setVisibility(View.VISIBLE);
        copySummaryButton.setVisibility(actionsAvailable ? View.VISIBLE : View.GONE);
        saveSummaryButton.setVisibility(actionsAvailable ? View.VISIBLE : View.GONE);
    }

    private void showTranscript() {
        summaryScroll.setVisibility(View.GONE);
        copySummaryButton.setVisibility(View.GONE);
        saveSummaryButton.setVisibility(View.GONE);
        transcriptList.setVisibility(View.VISIBLE);
        search.setVisibility(View.VISIBLE);
        status.setText(transcriptAdapter.getCount() + " of " + entries.size() + " subtitles");
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

    private final class TranscriptAdapter extends BaseAdapter {
        private final List<TranscriptEntry> all = new ArrayList<>();
        private final List<TranscriptEntry> visible = new ArrayList<>();

        void setEntries(List<TranscriptEntry> loaded) {
            all.clear();
            all.addAll(loaded);
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
            row.setBackgroundColor(BACKGROUND);
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
