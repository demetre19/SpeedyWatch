package com.speedywatch.app;

import android.app.Activity;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
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
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;

final class SettingsDialog {
    private static final int BACKGROUND = Color.rgb(15, 15, 15);
    private static final int PANEL = Color.rgb(30, 30, 30);
    private static final int BUTTON = Color.rgb(48, 48, 48);
    private static final int ACTIVE = Color.rgb(255, 0, 51);
    private static final int MUTED = Color.rgb(180, 180, 180);

    private final Activity activity;
    private final SpeedyWatchSettings settings;
    private final OpenRouterClient client;
    private final ExecutorService executor;
    private final List<OpenRouterClient.Model> models = new ArrayList<>();

    private Dialog dialog;
    private EditText apiKeyInput;
    private TextView apiKeyPreview;
    private ImageButton apiKeyVisibilityButton;
    private boolean apiKeyVisible;
    private Button modelButton;
    private TextView modelStatus;
    private EditText summaryOneInput;
    private EditText summaryTwoInput;
    private EditText quizInput;
    private String selectedModelId;

    SettingsDialog(
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
        dialog = new Dialog(activity);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setContentView(buildContent());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        dialog.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
        refreshModels();
    }

    private View buildContent() {
        LinearLayout content = verticalLayout();
        content.setPadding(dp(18), dp(14), dp(18), dp(14));
        content.setBackground(panelBackground(BACKGROUND, Color.rgb(70, 70, 70)));

        TextView title = text("Settings", 22, Color.WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(title);
        content.addView(text("OpenRouter", 13, MUTED), matchWrap(dp(2), dp(12)));

        content.addView(label("API key"));
        LinearLayout apiKeyRow = horizontalLayout();
        apiKeyInput = input(false, 1);
        apiKeyInput.setHint("sk-or-v1-...");
        apiKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKeyInput.setTransformationMethod(PasswordTransformationMethod.getInstance());
        try {
            apiKeyInput.setText(settings.getApiKey());
        } catch (GeneralSecurityException error) {
            Toast.makeText(activity, "Stored API key could not be decrypted", Toast.LENGTH_LONG).show();
        }
        apiKeyRow.addView(apiKeyInput, new LinearLayout.LayoutParams(0, dp(48), 1f));
        apiKeyVisibilityButton = new ImageButton(activity);
        apiKeyVisibilityButton.setImageResource(R.drawable.ic_visibility);
        apiKeyVisibilityButton.setContentDescription("Show API key");
        apiKeyVisibilityButton.setPadding(dp(10), dp(10), dp(10), dp(10));
        apiKeyVisibilityButton.setBackground(panelBackground(BUTTON, BUTTON));
        apiKeyVisibilityButton.setOnClickListener(ignored -> toggleApiKeyVisibility());
        LinearLayout.LayoutParams visibilityParams = new LinearLayout.LayoutParams(dp(48), dp(48));
        visibilityParams.setMarginStart(dp(8));
        apiKeyRow.addView(apiKeyVisibilityButton, visibilityParams);
        content.addView(apiKeyRow, matchWrap(dp(4), 0));

        apiKeyPreview = text("", 12, MUTED);
        content.addView(apiKeyPreview, matchWrap(dp(4), dp(10)));
        apiKeyInput.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                updateApiKeyPreview();
            }
        });
        updateApiKeyPreview();

        content.addView(label("Model"));
        selectedModelId = settings.getModelId();
        modelButton = button(selectedModelId.isEmpty() ? "Loading models..." : selectedModelId);
        modelButton.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        modelButton.setOnClickListener(ignored -> openModelPicker());
        content.addView(modelButton, matchWrap(dp(4), 0));

        LinearLayout modelActions = horizontalLayout();
        Button refresh = button("Refresh models");
        refresh.setOnClickListener(ignored -> refreshModels());
        modelActions.addView(refresh, new LinearLayout.LayoutParams(0, dp(42), 1f));
        modelStatus = text("Live OpenRouter catalog", 12, MUTED);
        modelStatus.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        modelActions.addView(modelStatus, new LinearLayout.LayoutParams(0, dp(42), 1f));
        content.addView(modelActions, matchWrap(dp(4), dp(14)));

        content.addView(label("Summary One prompt"));
        summaryOneInput = input(true, 7);
        summaryOneInput.setText(settings.getSummaryOnePrompt());
        content.addView(summaryOneInput, matchWrap(dp(4), dp(14)));

        content.addView(label("Summary Two prompt"));
        summaryTwoInput = input(true, 8);
        summaryTwoInput.setText(settings.getSummaryTwoPrompt());
        content.addView(summaryTwoInput, matchWrap(dp(4), dp(14)));

        content.addView(label("Quiz prompt"));
        quizInput = input(true, 7);
        quizInput.setText(settings.getQuizPrompt());
        content.addView(quizInput, matchWrap(dp(4), dp(14)));

        LinearLayout actions = horizontalLayout();
        Button cancel = button("Cancel");
        cancel.setOnClickListener(ignored -> dialog.dismiss());
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button save = button("Save");
        save.setBackground(panelBackground(ACTIVE, ACTIVE));
        save.setOnClickListener(ignored -> save());
        LinearLayout.LayoutParams saveParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        saveParams.setMarginStart(dp(8));
        actions.addView(save, saveParams);
        content.addView(actions);

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(content);
        return scroll;
    }

    private void toggleApiKeyVisibility() {
        apiKeyVisible = !apiKeyVisible;
        apiKeyInput.setTransformationMethod(
                apiKeyVisible ? null : PasswordTransformationMethod.getInstance()
        );
        apiKeyVisibilityButton.setImageResource(
                apiKeyVisible ? R.drawable.ic_visibility_off : R.drawable.ic_visibility
        );
        apiKeyVisibilityButton.setContentDescription(apiKeyVisible ? "Hide API key" : "Show API key");
        apiKeyInput.setSelection(apiKeyInput.length());
    }

    private void updateApiKeyPreview() {
        String key = apiKeyInput.getText().toString().trim();
        if (key.isEmpty()) {
            apiKeyPreview.setText("Key check: Not set");
            return;
        }
        int suffixLength = Math.min(5, Math.max(1, key.length() - 1));
        int prefixLength = key.length() > 13 ? 8 : 1;
        apiKeyPreview.setText(
                "Key check: "
                        + key.substring(0, prefixLength)
                        + "..."
                        + key.substring(key.length() - suffixLength)
        );
    }

    private void refreshModels() {
        if (modelStatus == null) {
            return;
        }
        modelStatus.setText("Loading...");
        modelButton.setEnabled(false);
        String apiKey = apiKeyInput.getText().toString().trim();
        executor.execute(() -> {
            try {
                List<OpenRouterClient.Model> loaded = client.fetchModels(apiKey);
                activity.runOnUiThread(() -> applyModels(loaded));
            } catch (Exception error) {
                activity.runOnUiThread(() -> {
                    if (dialog != null && dialog.isShowing()) {
                        modelStatus.setText("Load failed");
                        modelButton.setEnabled(!models.isEmpty());
                        Toast.makeText(activity, safeMessage(error, "Could not load models"), Toast.LENGTH_LONG).show();
                    }
                });
            }
        });
    }

    private void applyModels(List<OpenRouterClient.Model> loaded) {
        if (dialog == null || !dialog.isShowing()) {
            return;
        }
        models.clear();
        models.addAll(loaded);
        boolean selectedExists = findModel(selectedModelId) != null;
        if (!selectedExists) {
            OpenRouterClient.Model preferred = findModel(SpeedyWatchSettings.PREFERRED_MODEL_ID);
            selectedModelId = preferred == null ? "" : preferred.id;
        }
        updateModelButton();
        modelButton.setEnabled(!models.isEmpty());
        modelStatus.setText(models.size() + " text models");
    }

    private OpenRouterClient.Model findModel(String id) {
        if (id == null || id.isEmpty()) {
            return null;
        }
        for (OpenRouterClient.Model model : models) {
            if (model.id.equals(id)) {
                return model;
            }
        }
        return null;
    }

    private void updateModelButton() {
        OpenRouterClient.Model model = findModel(selectedModelId);
        modelButton.setText(model == null ? "Choose a model" : model.name + "\n" + model.id);
    }

    private void openModelPicker() {
        if (models.isEmpty()) {
            Toast.makeText(activity, "Load the model catalog first", Toast.LENGTH_SHORT).show();
            return;
        }
        Dialog picker = new Dialog(activity);
        picker.requestWindowFeature(Window.FEATURE_NO_TITLE);

        LinearLayout content = verticalLayout();
        content.setPadding(dp(14), dp(14), dp(14), dp(14));
        content.setBackground(panelBackground(BACKGROUND, Color.rgb(70, 70, 70)));
        TextView title = text("Choose OpenRouter model", 19, Color.WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        content.addView(title);
        EditText search = input(false, 1);
        search.setHint("Search name or model ID");
        content.addView(search, matchWrap(dp(10), dp(8)));

        ModelAdapter adapter = new ModelAdapter(models);
        ListView list = new ListView(activity);
        list.setDivider(new ColorDrawable(Color.rgb(55, 55, 55)));
        list.setDividerHeight(dp(1));
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            OpenRouterClient.Model selected = adapter.getItem(position);
            selectedModelId = selected.id;
            updateModelButton();
            picker.dismiss();
        });
        content.addView(list, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(460)
        ));
        search.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                adapter.filter(editable.toString());
            }
        });

        picker.setContentView(content);
        Window window = picker.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        picker.show();
        if (window != null) {
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.CENTER);
        }
        search.requestFocus();
    }

    private void save() {
        if (selectedModelId == null || selectedModelId.isEmpty()) {
            Toast.makeText(activity, "Choose an OpenRouter model", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            settings.setApiKey(apiKeyInput.getText().toString());
            settings.setModelId(selectedModelId);
            settings.setPrompts(
                    summaryOneInput.getText().toString(),
                    summaryTwoInput.getText().toString(),
                    quizInput.getText().toString()
            );
            Toast.makeText(activity, "Settings saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        } catch (GeneralSecurityException error) {
            Toast.makeText(activity, "API key could not be stored securely", Toast.LENGTH_LONG).show();
        }
    }

    private EditText input(boolean multiline, int lines) {
        EditText input = new EditText(activity);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(130, 130, 130));
        input.setTextSize(14);
        input.setPadding(dp(10), dp(8), dp(10), dp(8));
        input.setBackground(panelBackground(PANEL, Color.rgb(85, 85, 85)));
        input.setSingleLine(!multiline);
        if (multiline) {
            input.setGravity(Gravity.TOP | Gravity.START);
            input.setMinLines(lines);
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }
        return input;
    }

    private TextView label(String value) {
        TextView label = text(value, 13, Color.WHITE);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        return label;
    }

    private Button button(String value) {
        Button button = new Button(activity);
        button.setText(value);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(12), 0, dp(12), 0);
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

    private LinearLayout verticalLayout() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontalLayout() {
        LinearLayout layout = new LinearLayout(activity);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout.LayoutParams matchWrap(int top, int bottom) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(0, top, 0, bottom);
        return params;
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

    private final class ModelAdapter extends BaseAdapter {
        private final List<OpenRouterClient.Model> all;
        private final List<OpenRouterClient.Model> visible = new ArrayList<>();

        ModelAdapter(List<OpenRouterClient.Model> models) {
            all = new ArrayList<>(models);
            visible.addAll(models);
        }

        void filter(String query) {
            String normalized = query == null ? "" : query.toLowerCase(Locale.US).trim();
            visible.clear();
            for (OpenRouterClient.Model model : all) {
                if (normalized.isEmpty() || model.searchText().contains(normalized)) {
                    visible.add(model);
                }
            }
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return visible.size();
        }

        @Override
        public OpenRouterClient.Model getItem(int position) {
            return visible.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LinearLayout row;
            TextView name;
            TextView id;
            if (convertView instanceof LinearLayout existing) {
                row = existing;
                name = (TextView) row.getChildAt(0);
                id = (TextView) row.getChildAt(1);
            } else {
                row = verticalLayout();
                row.setPadding(dp(12), dp(9), dp(12), dp(9));
                name = text("", 14, Color.WHITE);
                id = text("", 11, MUTED);
                row.addView(name);
                row.addView(id);
            }
            OpenRouterClient.Model model = getItem(position);
            name.setText(model.name);
            id.setText(model.id);
            row.setBackgroundColor(model.id.equals(selectedModelId) ? Color.rgb(52, 25, 31) : BACKGROUND);
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
