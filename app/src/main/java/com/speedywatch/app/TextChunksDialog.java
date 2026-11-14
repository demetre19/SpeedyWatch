package com.speedywatch.app;

import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;

final class TextChunksDialog {
    private static final int DONE = UiTheme.rgb(46, 160, 67);

    private static final String SPLIT_SYSTEM_PROMPT =
            "You split the user's text into a sequence of chunks that each fit a "
                    + "character limit. Reply ONLY with a JSON array of strings — "
                    + "the chunks in order. No numbering, no commentary, no code "
                    + "fences. Every chunk MUST be a verbatim contiguous excerpt of "
                    + "the source text: never paraphrase, summarize, reorder, "
                    + "reformat, or correct anything. Prefer breaks at sentence, "
                    + "paragraph, or list boundaries so each chunk reads naturally.";

    private final Activity activity;
    private final SpeedyWatchSettings settings;
    private final OpenRouterClient client;
    private final ExecutorService executor;
    private final List<String> bodies = new ArrayList<>();

    private Dialog dialog;
    private EditText input;
    private EditText limitInput;
    private Button numberingButton;
    private Button smartButton;
    private Button splitButton;
    private Button copyNextButton;
    private ListView chunkList;
    private ChunkAdapter chunkAdapter;
    private TextView status;
    private boolean numbering = true;
    private int nextIndex;
    private boolean autoCopyFirst;
    private volatile long splitGeneration;

    TextChunksDialog(
            Activity activity,
            SpeedyWatchSettings settings,
            OpenRouterClient client,
            ExecutorService executor
    ) {
        this.activity = activity;
        this.settings = settings;
        this.client = client;
        this.executor = executor;
    }

    void show() {
        numbering = settings.isChunkNumberingEnabled();
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(buildContent());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
        }
        dialog.show();
        prefillFromClipboard();
    }

    private View buildContent() {
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(UiTheme.background());
        root.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout header = horizontalLayout();
        TextView title = text("Text chunks", 20, UiTheme.white());
        header.addView(title, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        ImageButton close = new ImageButton(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setContentDescription("Close text chunks");
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setOnClickListener(ignored -> dialog.dismiss());
        header.addView(close);
        root.addView(header);

        status = text("", 12, UiTheme.muted());
        root.addView(status, params(0, dp(4), 0, dp(4)));

        input = new EditText(activity);
        input.setHint("Paste the text to split…");
        input.setTextColor(UiTheme.white());
        input.setHintTextColor(UiTheme.muted());
        input.setTextSize(14);
        input.setMinLines(3);
        input.setMaxLines(6);
        input.setGravity(Gravity.START | Gravity.TOP);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(outlinedBackground(UiTheme.panel(), UiTheme.rgb(105, 105, 105), 1));
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        root.addView(input, params(0, 0, 0, dp(8)));

        LinearLayout toggles = horizontalLayout();
        numberingButton = button(numberingLabel(), ignored -> {
            numbering = !numbering;
            settings.setChunkNumberingEnabled(numbering);
            numberingButton.setText(numberingLabel());
            refreshChunks();
        });
        toggles.addView(numberingButton, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        smartButton = button(smartLabel(), ignored -> {
            boolean enabled = !settings.isChunkSmartSplitEnabled();
            settings.setChunkSmartSplitEnabled(enabled);
            smartButton.setText(smartLabel());
        });
        LinearLayout.LayoutParams smartParams = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        smartParams.setMargins(dp(8), 0, 0, 0);
        toggles.addView(smartButton, smartParams);
        root.addView(toggles, params(0, 0, 0, dp(8)));

        LinearLayout controls = horizontalLayout();
        TextView limitLabel = text("Limit", 13, UiTheme.muted());
        controls.addView(limitLabel);
        limitInput = new EditText(activity);
        limitInput.setSingleLine(true);
        limitInput.setText(String.valueOf(settings.getChunkLimit()));
        limitInput.setTextColor(UiTheme.white());
        limitInput.setHintTextColor(UiTheme.muted());
        limitInput.setTextSize(14);
        limitInput.setGravity(Gravity.CENTER);
        limitInput.setSelectAllOnFocus(true);
        limitInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        limitInput.setBackground(outlinedBackground(UiTheme.button(), UiTheme.rgb(105, 105, 105), 1));
        LinearLayout.LayoutParams limitParams =
                new LinearLayout.LayoutParams(dp(96), dp(40));
        limitParams.setMargins(dp(6), 0, dp(6), 0);
        controls.addView(limitInput, limitParams);
        View spacer = new View(activity);
        controls.addView(spacer, new LinearLayout.LayoutParams(
                0, dp(1), 1f));
        splitButton = button("Split", ignored -> split());
        splitButton.setBackground(outlinedBackground(UiTheme.active(), UiTheme.active(), 1));
        splitButton.setTextColor(UiTheme.onAccent());
        controls.addView(splitButton);
        root.addView(controls, params(0, 0, 0, dp(8)));

        chunkList = new ListView(activity);
        chunkList.setDivider(new ColorDrawable(Color.TRANSPARENT));
        chunkList.setDividerHeight(dp(6));
        chunkAdapter = new ChunkAdapter();
        chunkList.setAdapter(chunkAdapter);
        chunkList.setOnItemClickListener((parent, view, position, id) -> {
            copyChunk(position);
            nextIndex = position + 1;
            refreshChunks();
        });
        root.addView(chunkList, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        LinearLayout footer = horizontalLayout();
        copyNextButton = button("Copy next", ignored -> copyNext());
        copyNextButton.setEnabled(false);
        LinearLayout.LayoutParams nextParams =
                new LinearLayout.LayoutParams(0, dp(44), 1f);
        nextParams.setMargins(0, 0, dp(4), 0);
        footer.addView(copyNextButton, nextParams);
        Button copyAll = button("Copy all", ignored -> copyAll());
        LinearLayout.LayoutParams allParams =
                new LinearLayout.LayoutParams(0, dp(44), 1f);
        allParams.setMargins(dp(4), 0, 0, 0);
        footer.addView(copyAll, allParams);
        root.addView(footer, params(0, dp(8), 0, 0));
        return root;
    }

    private void prefillFromClipboard() {
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) {
            return;
        }
        CharSequence text = clip.getItemAt(0).coerceToText(activity);
        if (text == null || text.toString().trim().isEmpty()) {
            return;
        }
        input.setText(text);
        input.setSelection(input.getText().length());
        autoCopyFirst = true;
        status.setText("Clipboard loaded — splitting…");
        split();
        // Copy chunk 1 once deterministic chunks exist; a smart-split
        // refinement copies the refined chunk 1 again before it clears the flag.
        if (autoCopyFirst && !bodies.isEmpty()) {
            copyChunk(0);
            nextIndex = 1;
            refreshChunks();
        }
        if (!settings.isChunkSmartSplitEnabled()) {
            autoCopyFirst = false;
        }
    }

    private void split() {
        String source = input.getText() == null ? "" : input.getText().toString();
        if (source.trim().isEmpty()) {
            bodies.clear();
            refreshChunks();
            status.setText("Paste some text first");
            return;
        }
        int limit = readLimit();
        if (limit < 0) {
            return;
        }
        settings.setChunkLimit(limit);
        int bodyLimit = TextChunker.bodyLimit(source, limit, numbering);
        applyBodies(TextChunker.split(source, bodyLimit));
        status.setText(bodies.size() + " chunks, each ≤ " + limit
                + (settings.isChunkSmartSplitEnabled() ? " · refining…" : ""));
        if (!settings.isChunkSmartSplitEnabled()) {
            return;
        }
        refineWithAi(source, bodyLimit, limit);
    }

    private void refineWithAi(String source, int bodyLimit, int limit) {
        if (source.length() > TextChunker.AI_MAX_INPUT_CHARS) {
            status.setText(bodies.size() + " chunks ready (text too long for AI — local split)");
            return;
        }
        String problem = settings.aiConfigurationError();
        if (problem != null) {
            status.setText(bodies.size() + " chunks ready (local split — " + problem + ")");
            return;
        }
        String modelId = settings.getModelId();
        if (modelId == null || modelId.trim().isEmpty()) {
            status.setText(bodies.size() + " chunks ready (local split — no AI model chosen)");
            return;
        }
        final AiEndpoint endpoint;
        try {
            endpoint = settings.aiEndpoint();
        } catch (Exception error) {
            status.setText(bodies.size() + " chunks ready (local split)");
            return;
        }
        if (endpoint == null) {
            status.setText(bodies.size() + " chunks ready (local split)");
            return;
        }
        final long generation = ++splitGeneration;
        final String request = "Character limit per chunk: " + bodyLimit
                + "\nText to split verbatim:\n<<<\n" + source + "\n>>>";
        executor.execute(() -> {
            List<String> aiBodies = null;
            try {
                String reply = client.summarize(
                        endpoint, modelId, SPLIT_SYSTEM_PROMPT, request);
                List<String> parsed = TextChunker.parseAiChunks(reply);
                if (parsed != null
                        && TextChunker.acceptable(source, parsed, bodyLimit)) {
                    aiBodies = parsed;
                }
            } catch (Exception ignored) {
                // Any AI failure keeps the deterministic chunks.
            }
            final List<String> refined = aiBodies;
            activity.runOnUiThread(() -> {
                if (dialog == null || !dialog.isShowing() || generation != splitGeneration) {
                    return;
                }
                if (refined != null) {
                    applyBodies(refined);
                    status.setText(refined.size() + " chunks ready (AI-split verbatim)");
                    if (autoCopyFirst && !bodies.isEmpty()) {
                        copyChunk(0);
                        nextIndex = 1;
                        refreshChunks();
                    }
                } else {
                    status.setText(bodies.size() + " chunks ready (local split)");
                }
                autoCopyFirst = false;
            });
        });
    }

    private int readLimit() {
        String raw = limitInput.getText() == null ? "" : limitInput.getText().toString().trim();
        int limit;
        try {
            limit = Integer.parseInt(raw);
        } catch (NumberFormatException error) {
            limitInput.setError("Enter " + TextChunker.MIN_LIMIT
                    + " to " + TextChunker.MAX_LIMIT);
            return -1;
        }
        if (limit < TextChunker.MIN_LIMIT || limit > TextChunker.MAX_LIMIT) {
            limitInput.setError("Enter " + TextChunker.MIN_LIMIT
                    + " to " + TextChunker.MAX_LIMIT);
            return -1;
        }
        limitInput.setError(null);
        return limit;
    }

    private void applyBodies(List<String> newBodies) {
        bodies.clear();
        bodies.addAll(newBodies);
        nextIndex = 0;
        refreshChunks();
    }

    private void refreshChunks() {
        if (chunkAdapter != null) {
            chunkAdapter.notifyDataSetChanged();
        }
        if (copyNextButton != null) {
            copyNextButton.setEnabled(!bodies.isEmpty());
            copyNextButton.setText(nextIndex < bodies.size()
                    ? "Copy next (" + (nextIndex + 1) + "/" + bodies.size() + ")"
                    : "Copy next");
        }
    }

    private String displayChunk(int index) {
        String body = bodies.get(index);
        return numbering
                ? (index + 1) + "/" + bodies.size() + " " + body
                : body;
    }

    private void copyChunk(int index) {
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) {
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText(
                "SpeedyWatch chunk", displayChunk(index)));
        Toast.makeText(
                activity,
                "Copied " + (index + 1) + "/" + bodies.size(),
                Toast.LENGTH_SHORT
        ).show();
    }

    private void copyNext() {
        if (bodies.isEmpty()) {
            return;
        }
        if (nextIndex >= bodies.size()) {
            Toast.makeText(activity, "All chunks copied", Toast.LENGTH_SHORT).show();
            return;
        }
        copyChunk(nextIndex);
        nextIndex++;
        refreshChunks();
        if (nextIndex >= bodies.size()) {
            status.setText(bodies.size() + " chunks ready · all copied ✓");
        }
    }

    private void copyAll() {
        if (bodies.isEmpty()) {
            return;
        }
        StringBuilder all = new StringBuilder();
        for (int index = 0; index < bodies.size(); index++) {
            if (index > 0) {
                all.append("\n\n");
            }
            all.append(displayChunk(index));
        }
        ClipboardManager clipboard =
                (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(
                    "SpeedyWatch chunks", all.toString()));
            Toast.makeText(
                    activity,
                    "Copied all " + bodies.size() + " chunks",
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private String numberingLabel() {
        return "Numbering: " + (numbering ? "On" : "Off");
    }

    private String smartLabel() {
        return "Smart split: " + (settings.isChunkSmartSplitEnabled() ? "On" : "Off");
    }

    private final class ChunkAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return bodies.size();
        }

        @Override
        public Object getItem(int position) {
            return bodies.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row = convertView instanceof LinearLayout
                    ? (LinearLayout) convertView : null;
            TextView bodyView;
            TextView metaView;
            if (row == null) {
                row = new LinearLayout(activity);
                row.setOrientation(LinearLayout.VERTICAL);
                row.setPadding(dp(10), dp(8), dp(10), dp(8));
                row.setBackground(outlinedBackground(UiTheme.panel(), UiTheme.rgb(80, 80, 80), 1));
                metaView = new TextView(activity);
                metaView.setTextSize(12);
                row.addView(metaView);
                bodyView = new TextView(activity);
                bodyView.setTextSize(14);
                bodyView.setTextColor(UiTheme.white());
                bodyView.setPadding(0, dp(4), 0, 0);
                row.addView(bodyView);
            } else {
                metaView = (TextView) row.getChildAt(0);
                bodyView = (TextView) row.getChildAt(1);
            }
            String body = bodies.get(position);
            metaView.setText((position + 1) + "/" + bodies.size()
                    + " · " + TextChunker.weightedLength(displayChunk(position))
                    + " chars"
                    + (position == nextIndex ? "  ·  next" : ""));
            metaView.setTextColor(position < nextIndex
                    ? DONE
                    : position == nextIndex ? UiTheme.active() : UiTheme.muted());
            bodyView.setText(body);
            return row;
        }
    }

    private LinearLayout horizontalLayout() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams params(int left, int top, int right, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(left, top, right, bottom);
        return params;
    }

    private TextView text(String value, int size, int color) {
        TextView view = new TextView(activity);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        return view;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(activity);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextColor(UiTheme.white());
        button.setTextSize(13);
        button.setBackground(outlinedBackground(UiTheme.button(), UiTheme.rgb(105, 105, 105), 1));
        button.setOnClickListener(listener);
        return button;
    }

    private GradientDrawable outlinedBackground(int fill, int stroke, int width) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(8));
        if (width > 0) {
            drawable.setStroke(dp(width), stroke);
        }
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
