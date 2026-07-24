package com.speedywatch.app;

import android.app.AlertDialog;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.TextWatcher;
import android.text.method.PasswordTransformationMethod;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
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
    private static final String SEO_TIME_MACHINES_URL = "https://seotimemachines.com";
    private static final String UPDATE_PREFERENCES = "speedywatch_updates";
    private static final String UPDATE_LAST_CHECK = "last_check_ms";
    private static final String UPDATE_LAST_STATUS = "last_status";

    private final Activity activity;
    private final SpeedyWatchSettings settings;
    private final OpenRouterClient client;
    private final ExecutorService executor;
    private final List<OpenRouterClient.Model> models = new ArrayList<>();
    private final Runnable onSettingsSaved;
    private final String installedVersionName;
    private final long installedVersionCode;

    private Dialog dialog;
    private EditText apiKeyInput;
    private TextView apiKeyPreview;
    private ImageButton apiKeyVisibilityButton;
    private boolean apiKeyVisible;
    private Button modelButton;
    private TextView modelStatus;
    private EditText defaultSpeedInput;
    private Button lockIconToggleButton;
    private boolean lockIconEnabled;
    private EditText summaryOneInput;
    private EditText summaryTwoInput;
    private EditText quizInput;
    private String selectedModelId;
    private TextView updateStatus;
    private Button checkUpdatesButton;
    private Button downloadUpdateButton;
    private boolean updateBusy;

    SettingsDialog(
            Activity activity,
            SpeedyWatchSettings settings,
            OpenRouterClient client,
            ExecutorService executor,
            Runnable onSettingsSaved
    ) {
        this.activity = activity;
        this.settings = settings;
        this.client = client;
        this.executor = executor;
        this.onSettingsSaved = onSettingsSaved;
        try {
            PackageInfo packageInfo = activity.getPackageManager().getPackageInfo(
                    activity.getPackageName(),
                    0
            );
            installedVersionName = packageInfo.versionName == null
                    ? "unknown"
                    : packageInfo.versionName;
            installedVersionCode = packageInfo.getLongVersionCode();
        } catch (PackageManager.NameNotFoundException error) {
            throw new IllegalStateException("Installed package metadata is unavailable", error);
        }
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
        refreshModels();
        initializeUpdateCheck();
    }

    private View buildContent() {
        LinearLayout root = verticalLayout();
        root.setPadding(dp(18), dp(14), dp(18), dp(14));
        root.setBackground(panelBackground(BACKGROUND, Color.rgb(70, 70, 70)));

        LinearLayout header = horizontalLayout();
        TextView title = text("Settings", 22, Color.WHITE);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
        ));
        ImageButton close = new ImageButton(activity);
        close.setImageResource(R.drawable.ic_close);
        close.setContentDescription("Close Settings");
        close.setPadding(dp(9), dp(9), dp(9), dp(9));
        close.setBackground(panelBackground(PANEL, BUTTON));
        close.setOnClickListener(ignored -> dialog.dismiss());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(42), dp(42));
        closeParams.setMarginStart(dp(8));
        header.addView(close, closeParams);
        root.addView(header);

        LinearLayout content = verticalLayout();
        content.addView(text("Playback", 13, MUTED), matchWrap(dp(2), dp(12)));
        LinearLayout defaultSpeedRow = horizontalLayout();
        TextView defaultSpeedLabel = label("Default playback speed");
        defaultSpeedLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        defaultSpeedRow.addView(defaultSpeedLabel, new LinearLayout.LayoutParams(
                0,
                dp(44),
                1f
        ));
        defaultSpeedInput = input(false, 1);
        defaultSpeedInput.setHint("2.5");
        defaultSpeedInput.setGravity(Gravity.CENTER);
        defaultSpeedInput.setSelectAllOnFocus(true);
        defaultSpeedInput.setInputType(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        defaultSpeedInput.setText(formatSpeed(settings.getDefaultPlaybackSpeed()));
        LinearLayout.LayoutParams speedParams = new LinearLayout.LayoutParams(dp(76), dp(44));
        speedParams.setMarginStart(dp(8));
        defaultSpeedRow.addView(defaultSpeedInput, speedParams);
        content.addView(defaultSpeedRow, matchWrap(0, dp(12)));
        lockIconEnabled = settings.isLockIconEnabled();
        lockIconToggleButton = button("");
        lockIconToggleButton.setOnClickListener(ignored -> {
            lockIconEnabled = !lockIconEnabled;
            updateLockIconButton();
        });
        updateLockIconButton();
        content.addView(lockIconToggleButton, matchWrap(0, dp(6)));
        content.addView(
                text("Shown bottom-right above the speed controls.", 12, MUTED),
                matchWrap(dp(2), dp(12))
        );

        content.addView(text("Updates", 13, MUTED), matchWrap(dp(2), dp(8)));
        TextView currentVersion = text(
                "Current version " + installedVersionName
                        + " (version code " + installedVersionCode + ")",
                13,
                Color.WHITE
        );
        content.addView(currentVersion, matchWrap(0, dp(8)));
        SharedPreferences updatePreferences = updatePreferences();
        updateStatus = text(
                updatePreferences.getString(UPDATE_LAST_STATUS, "Not checked yet"),
                12,
                MUTED
        );
        content.addView(updateStatus);
        LinearLayout updateActions = horizontalLayout();
        checkUpdatesButton = button("Check for updates");
        checkUpdatesButton.setOnClickListener(ignored -> startUpdateCheck(false, true));
        updateActions.addView(
                checkUpdatesButton,
                new LinearLayout.LayoutParams(0, dp(44), 1f)
        );
        downloadUpdateButton = button("Download latest APK");
        downloadUpdateButton.setOnClickListener(ignored -> startUpdateCheck(true, true));
        LinearLayout.LayoutParams downloadParams =
                new LinearLayout.LayoutParams(0, dp(44), 1f);
        downloadParams.setMarginStart(dp(8));
        updateActions.addView(downloadUpdateButton, downloadParams);
        content.addView(updateActions, matchWrap(dp(8), dp(14)));

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
        content.addView(apiKeyRow, matchWrap(dp(8), 0));

        apiKeyPreview = text("", 12, MUTED);
        content.addView(apiKeyPreview, matchWrap(dp(8), dp(10)));
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
        content.addView(modelButton, matchWrap(dp(8), 0));

        LinearLayout modelActions = horizontalLayout();
        Button refresh = button("Refresh models");
        refresh.setOnClickListener(ignored -> refreshModels());
        modelActions.addView(refresh, new LinearLayout.LayoutParams(0, dp(42), 1f));
        modelStatus = text("Live OpenRouter catalog", 12, MUTED);
        modelStatus.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams modelStatusParams =
                new LinearLayout.LayoutParams(0, dp(42), 1f);
        modelStatusParams.setMarginStart(dp(8));
        modelActions.addView(modelStatus, modelStatusParams);
        content.addView(modelActions, matchWrap(dp(8), dp(14)));

        content.addView(label("Summary One prompt"));
        summaryOneInput = input(true, 7);
        summaryOneInput.setText(promptFieldValue(
                settings.getSummaryOnePrompt(), R.string.summary_one_prompt_default));
        content.addView(summaryOneInput, matchWrap(dp(8), dp(14)));

        content.addView(label("Summary Two prompt"));
        summaryTwoInput = input(true, 8);
        summaryTwoInput.setText(promptFieldValue(
                settings.getSummaryTwoPrompt(), R.string.summary_two_prompt_default));
        content.addView(summaryTwoInput, matchWrap(dp(8), dp(14)));

        content.addView(label("Quiz prompt"));
        quizInput = input(true, 7);
        quizInput.setText(promptFieldValue(
                settings.getQuizPrompt(), R.string.quiz_prompt_default));
        content.addView(quizInput, matchWrap(dp(8), dp(14)));

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

        String attributionPrefix = "Brought to you by the team from ";
        String attributionBrand = "SEO Time Machines";
        SpannableString attributionText = new SpannableString(attributionPrefix + attributionBrand);
        attributionText.setSpan(new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                openSeoTimeMachines();
            }

            @Override
            public void updateDrawState(TextPaint drawState) {
                drawState.setColor(Color.rgb(90, 180, 255));
                drawState.setUnderlineText(true);
                drawState.setTypeface(Typeface.DEFAULT_BOLD);
            }
        }, attributionPrefix.length(), attributionText.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TextView attribution = text("", 13, Color.WHITE);
        attribution.setText(attributionText);
        attribution.setGravity(Gravity.CENTER);
        attribution.setMinHeight(dp(44));
        attribution.setMovementMethod(LinkMovementMethod.getInstance());
        attribution.setHighlightColor(Color.TRANSPARENT);
        content.addView(attribution, matchWrap(dp(12), dp(2)));

        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));
        return root;
    }

    private SharedPreferences updatePreferences() {
        return activity.getSharedPreferences(UPDATE_PREFERENCES, Activity.MODE_PRIVATE);
    }

    private void initializeUpdateCheck() {
        long lastCheck = updatePreferences().getLong(UPDATE_LAST_CHECK, 0);
        if (System.currentTimeMillis() - lastCheck >= GitHubUpdateChecker.AUTO_CHECK_INTERVAL_MS) {
            startUpdateCheck(false, false);
        }
    }

    private void startUpdateCheck(boolean downloadLatest, boolean manual) {
        if (updateBusy) {
            if (manual) {
                Toast.makeText(activity, "An update check is already running", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        updateBusy = true;
        setUpdateControlsEnabled(false);
        updateStatus.setText("Checking official GitHub release...");
        executor.execute(() -> {
            try {
                GitHubUpdateChecker.Release release = GitHubUpdateChecker.fetchLatest();
                activity.runOnUiThread(() -> applyUpdateCheck(release, downloadLatest, manual));
            } catch (Exception error) {
                activity.runOnUiThread(() -> applyUpdateFailure(manual));
            }
        });
    }

    private void applyUpdateCheck(
            GitHubUpdateChecker.Release release,
            boolean downloadLatest,
            boolean manual
    ) {
        if (!isDialogActive()) {
            return;
        }
        updateBusy = false;
        setUpdateControlsEnabled(true);
        int comparison;
        try {
            comparison = release.compareToInstalled(installedVersionName);
        } catch (GitHubUpdateChecker.UpdateException error) {
            applyUpdateFailure(manual);
            return;
        }

        String message;
        if (comparison > 0) {
            message = "Update v" + release.versionName + " is available";
        } else if (comparison == 0) {
            message = "Up to date with published v" + release.versionName;
        } else {
            message = "Installed v" + installedVersionName
                    + " is newer than published v" + release.versionName;
        }
        saveUpdateStatus(message);
        if (downloadLatest) {
            showDownloadConfirmation(release, comparison);
        } else if (comparison > 0) {
            showUpdateAvailable(release);
        } else if (manual) {
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        }
    }

    private void applyUpdateFailure(boolean manual) {
        if (!isDialogActive()) {
            return;
        }
        updateBusy = false;
        setUpdateControlsEnabled(true);
        saveUpdateStatus("Could not check GitHub. Try again later.");
        if (manual) {
            Toast.makeText(activity, "Could not check for updates", Toast.LENGTH_LONG).show();
        }
    }

    private void showUpdateAvailable(GitHubUpdateChecker.Release release) {
        String notes = release.changelog.trim().isEmpty()
                ? "No release notes were provided."
                : shorten(release.changelog.trim(), 4_000);
        new AlertDialog.Builder(activity)
                .setTitle("SpeedyWatch v" + release.versionName + " is available")
                .setMessage(notes)
                .setNegativeButton("Not now", null)
                .setPositiveButton(
                        "Download update",
                        (alert, which) -> enqueueUpdateDownload(release)
                )
                .show();
    }

    private void showDownloadConfirmation(
            GitHubUpdateChecker.Release release,
            int comparison
    ) {
        String message = comparison > 0
                ? "Download the official SpeedyWatch v" + release.versionName
                        + " APK to your Downloads folder?"
                : "The latest published APK is v" + release.versionName
                        + ", which is not newer than installed v" + installedVersionName
                        + ". Download it anyway?";
        new AlertDialog.Builder(activity)
                .setTitle("Download latest published APK?")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        comparison > 0 ? "Download APK" : "Download anyway",
                        (alert, which) -> enqueueUpdateDownload(release)
                )
                .show();
    }

    private void enqueueUpdateDownload(GitHubUpdateChecker.Release release) {
        try {
            GitHubUpdateChecker.enqueueDownload(activity, release);
            String message = "Downloading SpeedyWatch v" + release.versionName
                    + " to Downloads";
            saveUpdateStatus(message);
            Toast.makeText(activity, message, Toast.LENGTH_LONG).show();
        } catch (GitHubUpdateChecker.UpdateException error) {
            saveUpdateStatus("Could not start the update download");
            Toast.makeText(activity, "Could not download the update", Toast.LENGTH_LONG).show();
        }
    }

    private void saveUpdateStatus(String message) {
        if (isDialogActive()) {
            updateStatus.setText(message);
        }
        updatePreferences().edit()
                .putLong(UPDATE_LAST_CHECK, System.currentTimeMillis())
                .putString(UPDATE_LAST_STATUS, message)
                .apply();
    }

    private void setUpdateControlsEnabled(boolean enabled) {
        checkUpdatesButton.setEnabled(enabled);
        downloadUpdateButton.setEnabled(enabled);
    }

    private boolean isDialogActive() {
        return dialog != null && dialog.isShowing() && !activity.isFinishing();
    }

    private static String shorten(String value, int maximum) {
        return value.length() <= maximum ? value : value.substring(0, maximum) + "...";
    }

    private void openSeoTimeMachines() {
        Uri uri = Uri.parse(SEO_TIME_MACHINES_URL);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            activity.startActivity(intent);
        } catch (RuntimeException error) {
            Toast.makeText(activity, "No app can open this link", Toast.LENGTH_SHORT).show();
        }
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

    private String promptFieldValue(String savedPrompt, int defaultResource) {
        return savedPrompt.trim().isEmpty() ? activity.getString(defaultResource) : savedPrompt;
    }

    private void updateLockIconButton() {
        lockIconToggleButton.setText(lockIconEnabled ? "Lock icon: On" : "Lock icon: Off");
    }

    private Double readDefaultSpeed() {
        try {
            double speed = Double.parseDouble(defaultSpeedInput.getText().toString().trim());
            if (speed < 0.25 || speed > 4.0) {
                throw new NumberFormatException();
            }
            return speed;
        } catch (NumberFormatException error) {
            Toast.makeText(activity, "Enter a default speed from 0.25 to 4", Toast.LENGTH_SHORT).show();
            return null;
        }
    }


    private void save() {
        Double defaultSpeed = readDefaultSpeed();
        if (defaultSpeed == null) {
            return;
        }
        settings.setDefaultPlaybackSpeed(defaultSpeed);
        settings.setLockIconEnabled(lockIconEnabled);
        onSettingsSaved.run();
        if (selectedModelId == null || selectedModelId.isEmpty()) {
            Toast.makeText(
                    activity,
                    "Default speed saved; choose an OpenRouter model",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        String summaryOne = summaryOneInput.getText().toString();
        String summaryTwo = summaryTwoInput.getText().toString();
        String quiz = quizInput.getText().toString();
        if (summaryOne.trim().isEmpty() || summaryTwo.trim().isEmpty() || quiz.trim().isEmpty()) {
            Toast.makeText(activity, "Prompt fields cannot be empty", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            settings.setApiKey(apiKeyInput.getText().toString());
            settings.setModelId(selectedModelId);
            settings.setPrompts(summaryOne, summaryTwo, quiz);
            Toast.makeText(activity, "Settings saved", Toast.LENGTH_SHORT).show();
            dialog.dismiss();
        } catch (GeneralSecurityException error) {
            Toast.makeText(activity, "API key could not be stored securely", Toast.LENGTH_LONG).show();
        }
    }

    private static String formatSpeed(double speed) {
        return speed == Math.rint(speed)
                ? String.format(java.util.Locale.US, "%.0f", speed)
                : String.format(java.util.Locale.US, "%.2f", speed)
                        .replaceAll("0+$", "")
                        .replaceAll("\\.$", "");
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
