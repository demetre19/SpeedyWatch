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
import android.os.Handler;
import android.os.Looper;
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
import java.util.EnumMap;
import java.util.Map;
import java.util.function.IntConsumer;
import java.util.concurrent.ExecutorService;

final class SettingsDialog {
    private static final int BACKGROUND = Color.rgb(15, 15, 15);
    private static final int PANEL = Color.rgb(30, 30, 30);
    private static final int BUTTON = Color.rgb(48, 48, 48);
    private static final int ACTIVE = Color.rgb(255, 0, 51);
    private static final int MUTED = Color.rgb(180, 180, 180);
    private static final String SEO_TIME_MACHINES_URL = "https://seotimemachines.com";
    private static final long AUTO_SAVE_DELAY_MILLIS = 650L;

    private enum OmniScope {
        WEB("Web"),
        YOUTUBE("YouTube"),
        X("X");

        final String label;

        OmniScope(String label) {
            this.label = label;
        }
    }

    private final MainActivity activity;
    private final SpeedyWatchSettings settings;
    private final OpenRouterClient client;
    private final ExecutorService executor;
    private final List<OpenRouterClient.Model> models = new ArrayList<>();
    private final Runnable onSettingsSaved;
    private final Runnable onDefaultSpeedSaved;
    private final Runnable onExportBackup;
    private final Runnable onImportBackup;
    private final String installedVersionName;
    private final long installedVersionCode;

    private Dialog dialog;
    private TextView autoSaveStatus;
    private final Handler autoSaveHandler = new Handler(Looper.getMainLooper());
    private final Runnable pendingEditableSave = this::saveEditableSettings;
    private final Runnable hideAutoSaveStatus = () -> {
        if (autoSaveStatus != null) {
            autoSaveStatus.setVisibility(View.INVISIBLE);
        }
    };
    private boolean autoSaveReady;
    private EditText apiKeyInput;
    private TextView apiKeyPreview;
    private ImageButton apiKeyVisibilityButton;
    private boolean apiKeyVisible;
    private Button modelButton;
    private TextView modelStatus;
    private EditText defaultSpeedInput;
    private Button defaultMp3QualityButton;
    private String defaultMp3Quality;
    private Button savedThumbnailsButton;
    private boolean savedThumbnailsEnabled;
    private Button scrapedLinksBackupButton;
    private boolean scrapedLinksBackupEnabled;
    private Button autoScrapeXLinksButton;
    private boolean autoScrapeXLinksEnabled;
    private Button startPageButton;
    private String startPage;
    private Button hideShortsButton;
    private boolean hideShortsEnabled;
    private Button shortsAsVideosButton;
    private boolean shortsAsVideosEnabled;
    private Button lockIconToggleButton;
    private boolean lockIconEnabled;
    private Button pictureInPictureControlButton;
    private String pictureInPictureControl;
    private Button omniButtonToggleButton;
    private boolean omniButtonEnabled;
    private Button omniButtonConfigureButton;
    private TextView omniButtonSummary;
    private int omniButtonColor;
    private int omniIconColor;
    private float omniOpacity;
    private EditText omniOpacityInput;
    private Button omniOpacityButton;
    private LinearLayout omniButtonSwatches;
    private LinearLayout omniIconSwatches;
    private EditText omniButtonHexInput;
    private EditText omniIconHexInput;
    private boolean omniAppearanceUpdating;
    private final EnumMap<OmniButtonGesture.Direction, OmniButtonAction> omniActions =
            new EnumMap<>(OmniButtonGesture.Direction.class);
    private final EnumMap<OmniButtonGesture.Direction, Double> omniAmounts =
            new EnumMap<>(OmniButtonGesture.Direction.class);
    private final EnumMap<OmniButtonGesture.Direction, EditText> omniAmountInputs =
            new EnumMap<>(OmniButtonGesture.Direction.class);
    private Dialog omniEditorDialog;
    private LinearLayout omniWebEditorRows;
    private LinearLayout omniEditorRows;
    private LinearLayout omniXEditorRows;
    private Button omniWebSectionButton;
    private Button omniGeneralSectionButton;
    private Button omniXSectionButton;
    private boolean omniWebExpanded = true;
    private boolean omniGeneralExpanded = false;
    private boolean omniXExpanded = false;
    private final EnumMap<OmniButtonGesture.Direction, OmniButtonAction> omniWebActions =
            new EnumMap<>(OmniButtonGesture.Direction.class);
    private final EnumMap<OmniButtonGesture.Direction, Double> omniWebAmounts =
            new EnumMap<>(OmniButtonGesture.Direction.class);
    private final EnumMap<OmniButtonGesture.Direction, EditText> omniWebAmountInputs =
            new EnumMap<>(OmniButtonGesture.Direction.class);
    private final EnumMap<OmniButtonGesture.Direction, OmniButtonAction> omniXActions =
            new EnumMap<>(OmniButtonGesture.Direction.class);
    private final EnumMap<OmniButtonGesture.Direction, Double> omniXAmounts =
            new EnumMap<>(OmniButtonGesture.Direction.class);
    private final EnumMap<OmniButtonGesture.Direction, EditText> omniXAmountInputs =
            new EnumMap<>(OmniButtonGesture.Direction.class);
    private Button playbackProfileButton;
    private Button adaptiveSpeedButton;
    private String playbackProfile;
    private boolean adaptiveSpeedEnabled;
    private Button sponsorBlockButton;
    private Button sponsorCategoryButton;
    private Button selfPromotionCategoryButton;
    private Button interactionCategoryButton;
    private boolean sponsorBlockEnabled;
    private boolean sponsorCategoryEnabled;
    private boolean selfPromotionCategoryEnabled;
    private boolean interactionCategoryEnabled;
    private EditText summaryOneInput;
    private EditText summaryTwoInput;
    private EditText quizInput;
    private EditText watchPathInput;
    private String selectedModelId;
    private TextView updateStatus;
    private Button checkUpdatesButton;
    private Button downloadUpdateButton;
    private boolean updateBusy;

    SettingsDialog(
            MainActivity activity,
            SpeedyWatchSettings settings,
            OpenRouterClient client,
            ExecutorService executor,
            Runnable onSettingsSaved,
            Runnable onDefaultSpeedSaved,
            Runnable onExportBackup,
            Runnable onImportBackup
    ) {
        this.activity = activity;
        this.settings = settings;
        this.client = client;
        this.executor = executor;
        this.onSettingsSaved = onSettingsSaved;
        this.onDefaultSpeedSaved = onDefaultSpeedSaved;
        this.onExportBackup = onExportBackup;
        this.onImportBackup = onImportBackup;
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
        dialog.setOnDismissListener(ignored -> flushPendingAutoSave());
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
        autoSaveStatus = text("Saved", 12, MUTED);
        autoSaveStatus.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        autoSaveStatus.setVisibility(View.INVISIBLE);
        autoSaveStatus.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        LinearLayout.LayoutParams savedParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(42)
        );
        savedParams.setMarginStart(dp(8));
        header.addView(autoSaveStatus, savedParams);
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
        content.addView(text("Startup", 15, Color.WHITE), matchWrap(dp(2), dp(12)));
        startPage = settings.getStartPage();
        startPageButton = button("");
        startPageButton.setOnClickListener(ignored -> {
            startPage = nextStartPage(startPage);
            updateStartPageButton();
            saveImmediateSettings();
        });
        updateStartPageButton();
        content.addView(startPageButton, matchWrap(0, dp(8)));
        content.addView(
                text(
                        "Choose where SpeedyWatch opens. Resume last page keeps your place; a site always starts fresh. Pages that failed to load never reopen.",
                        12,
                        MUTED
                ),
                matchWrap(dp(2), dp(10))
        );

        content.addView(text("Playback", 15, Color.WHITE), matchWrap(dp(2), dp(12)));
        playbackProfile = settings.getPlaybackProfile();

        playbackProfileButton = button("");
        playbackProfileButton.setOnClickListener(ignored -> {
            playbackProfile = nextPlaybackProfile(playbackProfile);
            defaultSpeedInput.setText(formatSpeed(SpeedyWatchSettings.speedForProfile(playbackProfile)));
            updatePlaybackButtons();
            saveImmediateSettings();
            saveEditableSettings();
        });
        content.addView(playbackProfileButton, matchWrap(0, dp(8)));

        adaptiveSpeedEnabled = settings.isAdaptiveSpeedEnabled();
        adaptiveSpeedButton = button("");
        adaptiveSpeedButton.setOnClickListener(ignored -> {
            adaptiveSpeedEnabled = !adaptiveSpeedEnabled;
            updatePlaybackButtons();
            saveImmediateSettings();
        });
        content.addView(adaptiveSpeedButton, matchWrap(0, dp(6)));
        content.addView(
                text("Adaptive speed adds 0.5x only during caption gaps, then returns to your chosen rate.", 12, MUTED),
                matchWrap(dp(2), dp(10))
        );
        sponsorBlockEnabled = settings.isSponsorBlockEnabled();
        sponsorCategoryEnabled = settings.skipsSponsorSegments();
        selfPromotionCategoryEnabled = settings.skipsSelfPromotionSegments();
        interactionCategoryEnabled = settings.skipsInteractionSegments();
        sponsorBlockButton = button("");
        sponsorBlockButton.setOnClickListener(ignored -> {
            sponsorBlockEnabled = !sponsorBlockEnabled;
            updateSponsorBlockButtons();
            saveImmediateSettings();
        });
        content.addView(sponsorBlockButton, matchWrap(0, dp(6)));
        sponsorCategoryButton = categoryButton("Sponsors", () -> sponsorCategoryEnabled = !sponsorCategoryEnabled);
        selfPromotionCategoryButton = categoryButton(
                "Self-promotion", () -> selfPromotionCategoryEnabled = !selfPromotionCategoryEnabled);
        interactionCategoryButton = categoryButton(
                "Interaction reminders", () -> interactionCategoryEnabled = !interactionCategoryEnabled);
        content.addView(sponsorCategoryButton, matchWrap(0, dp(6)));
        content.addView(selfPromotionCategoryButton, matchWrap(0, dp(6)));
        content.addView(interactionCategoryButton, matchWrap(0, dp(6)));
        content.addView(
                text("Optional community-submitted segments from SponsorBlock. Lookup uses only a four-character video-ID hash prefix.", 12, MUTED),
                matchWrap(dp(2), dp(10))
        );
        updateSponsorBlockButtons();
        shortsAsVideosEnabled = settings.isYouTubeShortsAsVideosEnabled();
        shortsAsVideosButton = button("");
        shortsAsVideosButton.setOnClickListener(ignored -> {
            shortsAsVideosEnabled = !shortsAsVideosEnabled;
            updateShortsAsVideosButton();
            saveImmediateSettings();
        });
        updateShortsAsVideosButton();
        content.addView(shortsAsVideosButton, matchWrap(0, dp(6)));
        content.addView(
                text(
                        "YouTube Shorts open in the regular player instead of the Shorts feed, so speed controls, captions, summaries, and downloads work on them.",
                        12,
                        MUTED
                ),
                matchWrap(dp(2), dp(10))
        );
        hideShortsEnabled = settings.isYouTubeHideShortsEnabled();
        hideShortsButton = button("");
        hideShortsButton.setOnClickListener(ignored -> {
            hideShortsEnabled = !hideShortsEnabled;
            updateHideShortsButton();
            saveImmediateSettings();
        });
        updateHideShortsButton();
        content.addView(hideShortsButton, matchWrap(0, dp(6)));
        content.addView(
                text(
                        "Removes Shorts shelves, thumbnails, and tab entries from YouTube pages. Shorts links still open in the regular player.",
                        12,
                        MUTED
                ),
                matchWrap(dp(2), dp(10))
        );



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
        updatePlaybackButtons();
        lockIconEnabled = settings.isLockIconEnabled();
        lockIconToggleButton = button("");
        lockIconToggleButton.setOnClickListener(ignored -> {
            lockIconEnabled = !lockIconEnabled;
            updateLockIconButton();
            saveImmediateSettings();
        });
        updateLockIconButton();
        content.addView(lockIconToggleButton, matchWrap(0, 0));
        content.addView(
                text("Shown bottom-right above the speed controls unless Omnibutton is on.", 12, MUTED),
                matchWrap(dp(8), 0)
        );
        pictureInPictureControl = settings.getPictureInPictureControl();
        pictureInPictureControlButton = button("");
        pictureInPictureControlButton.setOnClickListener(ignored -> {
            pictureInPictureControl = SpeedyWatchSettings.PIP_CONTROL_BUTTON.equals(
                    pictureInPictureControl
            )
                    ? SpeedyWatchSettings.PIP_CONTROL_PINCH
                    : SpeedyWatchSettings.PIP_CONTROL_BUTTON;
            updatePictureInPictureControlButton();
            saveImmediateSettings();
        });
        updatePictureInPictureControlButton();
        content.addView(pictureInPictureControlButton, matchWrap(dp(8), 0));
        content.addView(
                text(
                        "Choose the draggable button or pinch inward with two fingers over an active video.",
                        12,
                        MUTED
                ),
                matchWrap(dp(8), dp(12))
        );
        content.addView(text("Omnibutton", 15, Color.WHITE), matchWrap(dp(8), dp(8)));
        omniButtonEnabled = settings.isOmniButtonEnabled();
        loadOmniButtonBindings();
        omniButtonToggleButton = button("");
        omniButtonToggleButton.setOnClickListener(ignored -> {
            omniButtonEnabled = !omniButtonEnabled;
            updateOmniButtonToggle();
            saveImmediateSettings();
        });
        content.addView(omniButtonToggleButton, matchWrap(0, dp(8)));
        omniButtonConfigureButton = button("Configure 8 gestures");
        omniButtonConfigureButton.setOnClickListener(ignored -> showOmniButtonEditor());
        content.addView(omniButtonConfigureButton, matchWrap(0, 0));
        omniButtonSummary = text("", 12, MUTED);
        content.addView(omniButtonSummary, matchWrap(dp(8), 0));
        omniButtonColor = settings.getOmniButtonColor();
        omniIconColor = settings.getOmniIconColor();
        omniOpacity = settings.getOmniButtonOpacity();
        content.addView(label("Button color"), matchWrap(0, dp(6)));
        omniButtonSwatches = horizontalLayout();
        content.addView(omniButtonSwatches, matchWrap(0, dp(6)));
        omniButtonHexInput = hexColorInput("Button color hex (e.g. #303030)");
        bindHexInput(omniButtonHexInput, color -> {
            omniButtonColor = color;
            refreshOmniAppearanceUi();
            saveImmediateSettings();
        });
        content.addView(omniButtonHexInput, matchWrap(0, dp(8)));
        content.addView(label("Icon color"), matchWrap(0, dp(6)));
        omniIconSwatches = horizontalLayout();
        content.addView(omniIconSwatches, matchWrap(0, dp(6)));
        omniIconHexInput = hexColorInput("Icon color hex (e.g. #FFFFFF)");
        bindHexInput(omniIconHexInput, color -> {
            omniIconColor = color;
            refreshOmniAppearanceUi();
            saveImmediateSettings();
        });
        content.addView(omniIconHexInput, matchWrap(0, dp(8)));
        omniOpacityButton = button("");
        omniOpacityButton.setOnClickListener(ignored -> {
            omniOpacity = nextOmniOpacity(omniOpacity);
            updateOmniOpacityButton();
            saveImmediateSettings();
        });
        updateOmniOpacityButton();
        content.addView(omniOpacityButton, matchWrap(0, dp(8)));
        omniOpacityInput = hexColorInput("Opacity % (20-100)");
        omniOpacityInput.setInputType(InputType.TYPE_CLASS_NUMBER);
        omniOpacityInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (omniAppearanceUpdating) {
                    return;
                }
                try {
                    int percent = Integer.parseInt(editable.toString().trim());
                    if (percent < 20 || percent > 100) {
                        omniOpacityInput.setError("Enter 20 to 100");
                        return;
                    }
                    omniOpacityInput.setError(null);
                    omniOpacity = percent / 100f;
                    updateOmniOpacityButton();
                    saveImmediateSettings();
                } catch (NumberFormatException error) {
                    omniOpacityInput.setError("Enter 20 to 100");
                }
            }
        });
        content.addView(omniOpacityInput, matchWrap(0, dp(8)));
        refreshOmniAppearanceUi();
        content.addView(
                text(
                        "Swipe in any of eight directions. Hold for 350 ms before dragging; triple-tap still locks the screen.",
                        12,
                        MUTED
                ),
                matchWrap(dp(8), dp(12))
        );
        updateOmniButtonToggle();

        content.addView(text("Downloads", 15, Color.WHITE), matchWrap(dp(2), dp(8)));
        defaultMp3Quality = settings.getDefaultMp3Quality();
        defaultMp3QualityButton = button("");
        defaultMp3QualityButton.setOnClickListener(ignored -> {
            defaultMp3Quality = SpeedyWatchSettings.nextMp3Quality(defaultMp3Quality);
            updateDefaultMp3QualityButton();
            saveImmediateSettings();
        });
        updateDefaultMp3QualityButton();
        content.addView(defaultMp3QualityButton, matchWrap(0, dp(6)));
        content.addView(
                text(
                        "Used first in the Download dialog; each queued MP3 keeps the quality you chose.",
                        12,
                        MUTED
                ),
                matchWrap(dp(2), 0)
        );

        content.addView(text("Saved", 15, Color.WHITE), matchWrap(dp(8), dp(8)));
        savedThumbnailsEnabled = settings.areSavedThumbnailsEnabled();
        savedThumbnailsButton = button("");
        savedThumbnailsButton.setOnClickListener(ignored -> {
            savedThumbnailsEnabled = !savedThumbnailsEnabled;
            updateSavedThumbnailsButton();
            saveImmediateSettings();
        });
        updateSavedThumbnailsButton();
        content.addView(savedThumbnailsButton, matchWrap(0, 0));
        content.addView(
                text(
                        "Adds a small YouTube preview to newly saved summaries and quizzes. Existing items are unchanged.",
                        12,
                        MUTED
                ),
                matchWrap(dp(8), 0)
        );
        scrapedLinksBackupEnabled = settings.isScrapedLinksBackupEnabled();
        scrapedLinksBackupButton = button("");
        scrapedLinksBackupButton.setOnClickListener(ignored -> {
            scrapedLinksBackupEnabled = !scrapedLinksBackupEnabled;
            updateScrapedLinksBackupButton();
            saveImmediateSettings();
        });
        updateScrapedLinksBackupButton();
        content.addView(scrapedLinksBackupButton, matchWrap(0, 0));
        content.addView(
                text(
                        "Includes saved page links and their optional previews in JSON backups. Off by default because X threads and chats can be private.",
                        12,
                        MUTED
                ),
                matchWrap(dp(8), 0)
        );
        autoScrapeXLinksEnabled = settings.isAutoScrapeXLinksEnabled();
        autoScrapeXLinksButton = button("");
        autoScrapeXLinksButton.setOnClickListener(ignored -> {
            autoScrapeXLinksEnabled = !autoScrapeXLinksEnabled;
            updateAutoScrapeXLinksButton();
            saveImmediateSettings();
        });
        updateAutoScrapeXLinksButton();
        content.addView(autoScrapeXLinksButton, matchWrap(0, 0));
        content.addView(
                text(
                        "While you browse X, automatically saves links from whatever is on your screen into Saved. Off means links are saved only when you run Links from the Omnibutton.",
                        12,
                        MUTED
                ),
                matchWrap(dp(8), 0)
        );
        content.addView(text("Updates", 15, Color.WHITE), matchWrap(dp(8), dp(8)));
        TextView currentVersion = text(
                "Current version " + installedVersionName
                        + " (version code " + installedVersionCode + ")",
                13,
                Color.WHITE
        );
        content.addView(currentVersion, matchWrap(0, dp(8)));
        SharedPreferences updatePreferences = updatePreferences();
        updateStatus = text(
                updatePreferences.getString(
                        GitHubUpdateChecker.UPDATE_LAST_STATUS,
                        "Not checked yet"
                ),
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
        downloadUpdateButton = button("Download and install");
        downloadUpdateButton.setOnClickListener(ignored -> startUpdateCheck(true, true));
        LinearLayout.LayoutParams downloadParams =
                new LinearLayout.LayoutParams(0, dp(44), 1f);
        downloadParams.setMarginStart(dp(8));
        updateActions.addView(downloadUpdateButton, downloadParams);
        content.addView(updateActions, matchWrap(dp(8), dp(14)));

        content.addView(text("Backup", 15, Color.WHITE), matchWrap(dp(2), dp(8)));
        content.addView(
                text(
                        "Exports settings and saved summaries or quizzes. OpenRouter and MEGA access keys are never included.",
                        12,
                        MUTED
                ),
                matchWrap(0, dp(8))
        );
        LinearLayout backupActions = horizontalLayout();
        Button exportBackup = button("Export backup");
        exportBackup.setOnClickListener(ignored -> {
            dialog.dismiss();
            onExportBackup.run();
        });
        backupActions.addView(exportBackup, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button importBackup = button("Restore backup");
        importBackup.setOnClickListener(ignored -> new AlertDialog.Builder(activity)
                .setTitle("Restore backup?")
                .setMessage("This replaces saved summaries, quizzes, prompts, and app preferences. Protected API and MEGA bookmark keys stay unchanged.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Choose backup", (alert, which) -> {
                    dialog.dismiss();
                    onImportBackup.run();
                })
                .show());
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        importParams.setMarginStart(dp(8));
        backupActions.addView(importBackup, importParams);
        content.addView(backupActions, matchWrap(0, dp(14)));

        content.addView(text("OpenRouter", 15, Color.WHITE), matchWrap(dp(2), dp(12)));

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
                scheduleEditableAutoSave();
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
        content.addView(label("WatchPath prompt"));
        watchPathInput = input(true, 8);
        watchPathInput.setText(promptFieldValue(
                settings.getWatchPathPrompt(), R.string.watch_path_prompt_default));
        content.addView(watchPathInput, matchWrap(dp(8), dp(14)));
        attachEditableAutoSave(defaultSpeedInput);
        attachEditableAutoSave(summaryOneInput);
        attachEditableAutoSave(summaryTwoInput);
        attachEditableAutoSave(quizInput);
        attachEditableAutoSave(watchPathInput);
        autoSaveReady = true;


        Button closeSettings = button("Close");
        closeSettings.setOnClickListener(ignored -> dialog.dismiss());
        content.addView(
                closeSettings,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(44)
                )
        );

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
        return activity.getSharedPreferences(
                GitHubUpdateChecker.UPDATE_PREFERENCES,
                Activity.MODE_PRIVATE
        );
    }

    private void initializeUpdateCheck() {
        long lastCheck = updatePreferences().getLong(
                GitHubUpdateChecker.UPDATE_LAST_CHECK,
                0
        );
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
            message = "SpeedyWatch is up to date (v" + release.versionName + ")";
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
                        "Download and install",
                        (alert, which) -> enqueueUpdateDownload(release)
                )
                .show();
    }

    private void showDownloadConfirmation(
            GitHubUpdateChecker.Release release,
            int comparison
    ) {
        String message = comparison > 0
                ? "Download and verify the official SpeedyWatch v" + release.versionName
                        + " APK, then open Android's installer?"
                : "The latest published APK is v" + release.versionName
                        + ", which is not newer than installed v" + installedVersionName
                        + ". Download, verify, and open the installer anyway?";
        new AlertDialog.Builder(activity)
                .setTitle("Download latest published APK?")
                .setMessage(message)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(
                        comparison > 0 ? "Download and install" : "Download anyway",
                        (alert, which) -> enqueueUpdateDownload(release)
                )
                .show();
    }

    private void enqueueUpdateDownload(GitHubUpdateChecker.Release release) {
        activity.runAfterNotificationPermissionDecision(
                () -> startUpdateDownload(release)
        );
    }

    private void startUpdateDownload(GitHubUpdateChecker.Release release) {
        try {
            GitHubUpdateChecker.enqueueDownload(activity, release);
            String message = "Downloading SpeedyWatch v" + release.versionName
                    + "; Android's installer will open when it is verified";
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
                .putLong(GitHubUpdateChecker.UPDATE_LAST_CHECK, System.currentTimeMillis())
                .putString(GitHubUpdateChecker.UPDATE_LAST_STATUS, message)
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
                        Toast.makeText(
                                activity,
                                safeMessage(error, "Could not load models"),
                                Toast.LENGTH_LONG
                        ).show();
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
        String previousModelId = selectedModelId;
        boolean selectedExists = findModel(selectedModelId) != null;
        if (!selectedExists) {
            OpenRouterClient.Model preferred = findModel(SpeedyWatchSettings.PREFERRED_MODEL_ID);
            selectedModelId = preferred == null ? "" : preferred.id;
        }
        if (!selectedModelId.isEmpty() && !selectedModelId.equals(previousModelId)) {
            settings.setModelId(selectedModelId);
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
        modelButton.setText(model == null
                ? "Choose a model"
                : model.name + "\n" + model.id + "\n" + model.guidance());
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
        Button filter = button("Showing: All models");
        filter.setOnClickListener(ignored -> {
            ModelAdapter adapter = (ModelAdapter) filter.getTag();
            adapter.cycleMode();
            filter.setText(adapter.modeLabel());
        });
        content.addView(filter, matchWrap(0, dp(8)));


        ModelAdapter adapter = new ModelAdapter(models);
        filter.setTag(adapter);
        ListView list = new ListView(activity);
        list.setDivider(new ColorDrawable(Color.rgb(55, 55, 55)));
        list.setDividerHeight(dp(1));
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            OpenRouterClient.Model selected = adapter.getItem(position);
            selectedModelId = selected.id;
            updateModelButton();
            settings.setModelId(selectedModelId);
            showAutoSaved();
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

    private void updatePlaybackButtons() {
        if (playbackProfileButton != null) {
            playbackProfileButton.setText(
                    "Playback profile: " + SpeedyWatchSettings.profileLabel(playbackProfile));
        }
        if (adaptiveSpeedButton != null) {
            adaptiveSpeedButton.setText(
                    adaptiveSpeedEnabled ? "Adaptive caption-gap speed: On" : "Adaptive caption-gap speed: Off");
        }
    }

    private static String nextPlaybackProfile(String current) {
        if (SpeedyWatchSettings.PROFILE_NORMAL.equals(current)) {
            return SpeedyWatchSettings.PROFILE_CAREFUL;
        }
        if (SpeedyWatchSettings.PROFILE_CAREFUL.equals(current)) {
            return SpeedyWatchSettings.PROFILE_LECTURE;
        }
        if (SpeedyWatchSettings.PROFILE_LECTURE.equals(current)) {
            return SpeedyWatchSettings.PROFILE_PODCAST;
        }
        return SpeedyWatchSettings.PROFILE_NORMAL;
    }

    private Button categoryButton(String label, Runnable toggle) {
        Button result = button(label);
        result.setOnClickListener(ignored -> {
            toggle.run();
            updateSponsorBlockButtons();
            saveImmediateSettings();
        });
        return result;
    }

    private void updateSponsorBlockButtons() {
        if (sponsorBlockButton == null) {
            return;
        }
        sponsorBlockButton.setText(
                sponsorBlockEnabled ? "SponsorBlock community skips: On" : "SponsorBlock community skips: Off");
        sponsorCategoryButton.setText("Sponsors: " + (sponsorCategoryEnabled ? "On" : "Off"));
        selfPromotionCategoryButton.setText(
                "Self-promotion: " + (selfPromotionCategoryEnabled ? "On" : "Off"));
        interactionCategoryButton.setText(
                "Interaction reminders: " + (interactionCategoryEnabled ? "On" : "Off"));
    }

    private void updateLockIconButton() {
        lockIconToggleButton.setText(lockIconEnabled ? "Lock icon: On" : "Lock icon: Off");
    }

    private void updatePictureInPictureControlButton() {
        pictureInPictureControlButton.setText(
                SpeedyWatchSettings.PIP_CONTROL_PINCH.equals(pictureInPictureControl)
                        ? "Picture-in-Picture control: Pinch gesture"
                        : "Picture-in-Picture control: Floating button"
        );
    }

    private void loadOmniButtonBindings() {
        omniActions.clear();
        omniAmounts.clear();
        omniWebActions.clear();
        omniWebAmounts.clear();
        omniXActions.clear();
        omniXAmounts.clear();
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            omniActions.put(direction, settings.getOmniButtonAction(direction));
            omniAmounts.put(direction, settings.getOmniButtonAmount(direction));
            omniWebActions.put(direction, settings.getOmniWebButtonAction(direction));
            omniWebAmounts.put(direction, settings.getOmniWebButtonAmount(direction));
            omniXActions.put(direction, settings.getOmniXButtonAction(direction));
            omniXAmounts.put(direction, settings.getOmniXButtonAmount(direction));
        }
    }

    private void showOmniButtonEditor() {
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> originalActions =
                new EnumMap<>(omniActions);
        EnumMap<OmniButtonGesture.Direction, Double> originalAmounts =
                new EnumMap<>(omniAmounts);

        omniEditorDialog = new Dialog(activity);
        omniEditorDialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout root = verticalLayout();
        root.setPadding(dp(18), dp(14), dp(18), dp(14));
        root.setBackground(panelBackground(BACKGROUND, Color.rgb(70, 70, 70)));

        TextView title = text("Omnibutton gestures", 20, Color.WHITE);
        title.setTypeface(title.getTypeface(), Typeface.BOLD);
        root.addView(title);
        root.addView(
                text(
                        "Assign any SpeedyWatch action to each swipe direction. Amount fields appear only where needed.",
                        12,
                        MUTED
                ),
                matchWrap(dp(6), dp(8))
        );

        LinearLayout sections = verticalLayout();
        omniWebSectionButton = button("▾ Web");
        omniWebSectionButton.setContentDescription(
                "Omnibutton actions used while browsing Web pages. Tap to collapse or expand"
        );
        omniWebSectionButton.setOnClickListener(ignored -> {
            omniWebExpanded = !omniWebExpanded;
            applyOmniAccordionState();
        });
        sections.addView(omniWebSectionButton, matchWrap(dp(8), dp(4)));
        omniWebEditorRows = verticalLayout();
        sections.addView(omniWebEditorRows, matchWrap(0, 0));
        omniGeneralSectionButton = button("▸ YouTube");
        omniGeneralSectionButton.setContentDescription(
                "Omnibutton actions used while viewing YouTube. Tap to collapse or expand"
        );
        omniGeneralSectionButton.setOnClickListener(ignored -> {
            omniGeneralExpanded = !omniGeneralExpanded;
            applyOmniAccordionState();
        });
        sections.addView(omniGeneralSectionButton, matchWrap(dp(8), dp(4)));
        omniEditorRows = verticalLayout();
        sections.addView(omniEditorRows, matchWrap(0, 0));
        omniXSectionButton = button("▸ X");
        omniXSectionButton.setContentDescription(
                "Omnibutton actions used while viewing X. Tap to collapse or expand"
        );
        omniXSectionButton.setOnClickListener(ignored -> {
            omniXExpanded = !omniXExpanded;
            applyOmniAccordionState();
        });
        sections.addView(omniXSectionButton, matchWrap(dp(8), dp(4)));
        omniXEditorRows = verticalLayout();
        sections.addView(omniXEditorRows, matchWrap(0, 0));
        applyOmniAccordionState();
        rebuildOmniButtonEditorRows();
        ScrollView scroll = new ScrollView(activity);
        scroll.setFillViewport(true);
        scroll.addView(sections);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        Button reset = button("Reset defaults");
        reset.setOnClickListener(ignored -> {
            omniWebActions.clear();
            omniWebActions.putAll(SpeedyWatchSettings.defaultOmniWebButtonActions());
            omniWebAmounts.clear();
            omniWebAmounts.putAll(SpeedyWatchSettings.defaultOmniWebButtonAmounts());
            omniActions.clear();
            omniActions.putAll(SpeedyWatchSettings.defaultOmniButtonActions());
            omniAmounts.clear();
            omniAmounts.putAll(SpeedyWatchSettings.defaultOmniButtonAmounts());
            omniXActions.clear();
            omniXActions.putAll(SpeedyWatchSettings.defaultOmniXButtonActions());
            omniXAmounts.clear();
            for (OmniButtonGesture.Direction direction
                    : OmniButtonGesture.Direction.configurableValues()) {
                OmniButtonAction action = omniXActions.get(direction);
                omniXAmounts.put(direction, OmniButtonAction.defaultAmount(action));
            }
            rebuildOmniButtonEditorRows();
        });
        root.addView(reset, matchWrap(dp(8), dp(8)));

        LinearLayout actions = horizontalLayout();
        Button cancel = button("Cancel");
        cancel.setOnClickListener(ignored -> {
            omniActions.clear();
            omniActions.putAll(originalActions);
            omniAmounts.clear();
            omniAmounts.putAll(originalAmounts);
            loadOmniButtonBindings();
            omniEditorDialog.dismiss();
        });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(44), 1f));
        Button done = button("Done");
        done.setBackground(panelBackground(ACTIVE, ACTIVE));
        done.setOnClickListener(ignored -> {
            if (!readOmniButtonAmounts(true)) {
                return;
            }
            updateOmniButtonSummary();
            saveImmediateSettings();
            omniEditorDialog.dismiss();
        });
        LinearLayout.LayoutParams doneParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        doneParams.setMarginStart(dp(8));
        actions.addView(done, doneParams);
        root.addView(actions);

        omniEditorDialog.setOnCancelListener(ignored -> {
            omniActions.clear();
            omniActions.putAll(originalActions);
            omniAmounts.clear();
            omniAmounts.putAll(originalAmounts);
            loadOmniButtonBindings();
        });
        omniEditorDialog.setContentView(root);
        Window window = omniEditorDialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        omniEditorDialog.show();
        if (window != null) {
            window.setLayout(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            );
            window.setGravity(Gravity.CENTER);
        }
    }

    private void rebuildOmniButtonEditorRows() {
        buildOmniDirectionRows(
                omniWebEditorRows,
                omniWebActions,
                omniWebAmounts,
                omniWebAmountInputs,
                OmniScope.WEB
        );
        buildOmniDirectionRows(
                omniEditorRows,
                omniActions,
                omniAmounts,
                omniAmountInputs,
                OmniScope.YOUTUBE
        );
        buildOmniDirectionRows(
                omniXEditorRows,
                omniXActions,
                omniXAmounts,
                omniXAmountInputs,
                OmniScope.X
        );
    }

    private void applyOmniAccordionState() {
        if (omniWebSectionButton == null
                || omniGeneralSectionButton == null
                || omniXSectionButton == null) {
            return;
        }
        omniWebSectionButton.setText((omniWebExpanded ? "▾ " : "▸ ") + "Web");
        omniWebEditorRows.setVisibility(omniWebExpanded ? View.VISIBLE : View.GONE);
        omniGeneralSectionButton.setText(
                (omniGeneralExpanded ? "▾ " : "▸ ") + "YouTube");
        omniEditorRows.setVisibility(omniGeneralExpanded ? View.VISIBLE : View.GONE);
        omniXSectionButton.setText((omniXExpanded ? "▾ " : "▸ ") + "X");
        omniXEditorRows.setVisibility(omniXExpanded ? View.VISIBLE : View.GONE);
    }

    private void buildOmniDirectionRows(
            LinearLayout container,
            EnumMap<OmniButtonGesture.Direction, OmniButtonAction> actions,
            EnumMap<OmniButtonGesture.Direction, Double> amounts,
            EnumMap<OmniButtonGesture.Direction, EditText> amountInputs,
            OmniScope omniScope
    ) {
        container.removeAllViews();
        amountInputs.clear();
        String scope = " (" + omniScope.label + ")";
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            OmniButtonAction action = actions.get(direction);
            LinearLayout group = verticalLayout();

            LinearLayout row = horizontalLayout();
            TextView directionLabel = label(direction.label);
            directionLabel.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.addView(directionLabel, new LinearLayout.LayoutParams(dp(92), dp(44)));
            Button actionButton = button(action.label);
            actionButton.setContentDescription(
                    direction.label + scope + " swipe action: " + action.label
            );
            actionButton.setOnClickListener(ignored ->
                    showOmniButtonActionPicker(direction, omniScope));
            row.addView(actionButton, new LinearLayout.LayoutParams(0, dp(44), 1f));
            group.addView(row);

            if (action.usesAmount()) {
                LinearLayout amountRow = horizontalLayout();
                String amountLabel = action.amountType == OmniButtonAction.AmountType.SPEED
                        ? "Amount (x)"
                        : "Seconds";
                TextView label = text(amountLabel, 12, MUTED);
                label.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
                amountRow.addView(label, new LinearLayout.LayoutParams(0, dp(44), 1f));
                EditText input = input(false, 1);
                input.setGravity(Gravity.CENTER);
                input.setSelectAllOnFocus(true);
                input.setInputType(
                        InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                );
                double amount = amounts.getOrDefault(
                        direction,
                        OmniButtonAction.defaultAmount(action)
                );
                if (!action.acceptsAmount(amount)) {
                    amount = OmniButtonAction.defaultAmount(action);
                    amounts.put(direction, amount);
                }
                input.setText(formatSpeed(amount));
                input.setContentDescription(direction.label + scope + " " + amountLabel);
                amountInputs.put(direction, input);
                LinearLayout.LayoutParams amountParams =
                        new LinearLayout.LayoutParams(dp(100), dp(44));
                amountParams.setMarginStart(dp(8));
                amountRow.addView(input, amountParams);
                group.addView(amountRow);
            }
            container.addView(group, matchWrap(dp(8), 0));
        }
    }

    private void showOmniButtonActionPicker(
            OmniButtonGesture.Direction direction,
            OmniScope omniScope
    ) {
        captureValidOmniButtonAmounts();
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> actions =
                omniScope == OmniScope.WEB
                        ? omniWebActions
                        : omniScope == OmniScope.X ? omniXActions : omniActions;
        EnumMap<OmniButtonGesture.Direction, Double> amounts =
                omniScope == OmniScope.WEB
                        ? omniWebAmounts
                        : omniScope == OmniScope.X ? omniXAmounts : omniAmounts;
        OmniButtonAction[] options = OmniButtonAction.values();
        String[] labels = new String[options.length];
        int selected = 0;
        for (int index = 0; index < options.length; index++) {
            labels[index] = options[index].label;
            if (options[index] == actions.get(direction)) {
                selected = index;
            }
        }
        new AlertDialog.Builder(activity)
                .setTitle(direction.label + " swipe (" + omniScope.label + ")")
                .setSingleChoiceItems(labels, selected, (dialog, which) -> {
                    OmniButtonAction action = options[which];
                    actions.put(direction, action);
                    Double amount = amounts.get(direction);
                    if (action.usesAmount()
                            && (amount == null || !action.acceptsAmount(amount))) {
                        amounts.put(direction, OmniButtonAction.defaultAmount(action));
                    }
                    dialog.dismiss();
                    rebuildOmniButtonEditorRows();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void captureValidOmniButtonAmounts() {
        captureValidOmniButtonAmounts(
                omniWebAmountInputs, omniWebActions, omniWebAmounts);
        captureValidOmniButtonAmounts(omniAmountInputs, omniActions, omniAmounts);
        captureValidOmniButtonAmounts(omniXAmountInputs, omniXActions, omniXAmounts);
    }

    private void captureValidOmniButtonAmounts(
            EnumMap<OmniButtonGesture.Direction, EditText> inputs,
            EnumMap<OmniButtonGesture.Direction, OmniButtonAction> actions,
            EnumMap<OmniButtonGesture.Direction, Double> amounts
    ) {
        for (Map.Entry<OmniButtonGesture.Direction, EditText> entry : inputs.entrySet()) {
            OmniButtonAction action = actions.get(entry.getKey());
            try {
                double amount = Double.parseDouble(entry.getValue().getText().toString().trim());
                if (action.acceptsAmount(amount)) {
                    amounts.put(entry.getKey(), amount);
                }
            } catch (NumberFormatException ignored) {
                // The value is validated when the user taps Done.
            }
        }
    }

    private boolean readOmniButtonAmounts(boolean showErrors) {
        boolean valid = readOmniButtonAmounts(
                showErrors, omniWebAmountInputs, omniWebActions, omniWebAmounts);
        valid &= readOmniButtonAmounts(
                showErrors, omniAmountInputs, omniActions, omniAmounts);
        valid &= readOmniButtonAmounts(
                showErrors, omniXAmountInputs, omniXActions, omniXAmounts);
        return valid;
    }

    private boolean readOmniButtonAmounts(
            boolean showErrors,
            EnumMap<OmniButtonGesture.Direction, EditText> inputs,
            EnumMap<OmniButtonGesture.Direction, OmniButtonAction> actions,
            EnumMap<OmniButtonGesture.Direction, Double> amounts
    ) {
        boolean valid = true;
        for (Map.Entry<OmniButtonGesture.Direction, EditText> entry : inputs.entrySet()) {
            OmniButtonAction action = actions.get(entry.getKey());
            EditText input = entry.getValue();
            try {
                double amount = Double.parseDouble(input.getText().toString().trim());
                if (!action.acceptsAmount(amount)) {
                    throw new NumberFormatException();
                }
                amounts.put(entry.getKey(), amount);
                input.setError(null);
            } catch (NumberFormatException error) {
                valid = false;
                if (showErrors) {
                    input.setError(action.amountType == OmniButtonAction.AmountType.SPEED
                            ? "Enter 0.01 to 3.75"
                            : "Enter 1 to 600");
                }
            }
        }
        return valid;
    }

    private void updateOmniButtonSummary() {
        if (omniButtonSummary == null) {
            return;
        }
        String summary = "Separate Web, YouTube, and X gesture maps";
        omniButtonSummary.setText(summary);
        omniButtonSummary.setContentDescription(
                "Configured Omnibutton gestures. " + summary);
    }

    private void updateOmniButtonToggle() {
        omniButtonToggleButton.setText(
                omniButtonEnabled ? "Omnibutton: On" : "Omnibutton: Off"
        );
        updateOmniButtonSummary();
    }

    private void updateDefaultMp3QualityButton() {
        defaultMp3QualityButton.setText(
                "Default MP3 quality: "
                        + SpeedyWatchSettings.mp3QualityLabel(defaultMp3Quality)
        );
    }

    private void updateSavedThumbnailsButton() {
        savedThumbnailsButton.setText(
                savedThumbnailsEnabled
                        ? "Save YouTube thumbnails: On"
                        : "Save YouTube thumbnails: Off"
        );
    }

    private void updateScrapedLinksBackupButton() {
        scrapedLinksBackupButton.setText(
                scrapedLinksBackupEnabled
                        ? "Include saved links in backups: On"
                        : "Include saved links in backups: Off"
        );
    }

    private void updateAutoScrapeXLinksButton() {
        autoScrapeXLinksButton.setText(
                autoScrapeXLinksEnabled
                        ? "Auto-scrape X links: On"
                        : "Auto-scrape X links: Off"
        );
    }

    private String nextStartPage(String current) {
        if (SpeedyWatchSettings.START_PAGE_YOUTUBE.equals(current)) {
            return SpeedyWatchSettings.START_PAGE_X;
        }
        if (SpeedyWatchSettings.START_PAGE_X.equals(current)) {
            return SpeedyWatchSettings.START_PAGE_RESUME;
        }
        return SpeedyWatchSettings.START_PAGE_YOUTUBE;
    }

    private String startPageLabel(String value) {
        if (SpeedyWatchSettings.START_PAGE_YOUTUBE.equals(value)) {
            return "YouTube";
        }
        if (SpeedyWatchSettings.START_PAGE_X.equals(value)) {
            return "X";
        }
        return "Resume last page";
    }

    private void updateStartPageButton() {
        startPageButton.setText("Start page: " + startPageLabel(startPage));
    }


    private void updateShortsAsVideosButton() {
        shortsAsVideosButton.setText(
                shortsAsVideosEnabled
                        ? "Play Shorts as regular videos: On"
                        : "Play Shorts as regular videos: Off"
        );
    }

    private void updateHideShortsButton() {
        hideShortsButton.setText(
                hideShortsEnabled ? "Hide Shorts: On" : "Hide Shorts: Off"
        );
    }

    private Double readDefaultSpeed() {
        try {
            double speed = Double.parseDouble(defaultSpeedInput.getText().toString().trim());
            if (speed < 0.25 || speed > 4.0) {
                throw new NumberFormatException();
            }
            defaultSpeedInput.setError(null);
            return speed;
        } catch (NumberFormatException error) {
            defaultSpeedInput.setError("Enter 0.25 to 4");
            return null;
        }
    }

    private void saveImmediateSettings() {
        settings.setDefaultMp3Quality(defaultMp3Quality);
        settings.setLockIconEnabled(lockIconEnabled);
        settings.setPictureInPictureControl(pictureInPictureControl);
        settings.setYouTubeShortsAsVideosEnabled(shortsAsVideosEnabled);
        settings.setYouTubeHideShortsEnabled(hideShortsEnabled);
        settings.setOmniButtonEnabled(omniButtonEnabled);
        settings.setStartPage(startPage);
        settings.setOmniButtonAppearance(omniButtonColor, omniIconColor, omniOpacity);
        if (!settings.setOmniWebButtonBindings(omniWebActions, omniWebAmounts)) {
            Toast.makeText(
                    activity,
                    "Finish the Omnibutton gesture amounts",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        if (!settings.setOmniButtonBindings(omniActions, omniAmounts)) {
            Toast.makeText(
                    activity,
                    "Finish the Omnibutton gesture amounts",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        if (!settings.setOmniXButtonBindings(omniXActions, omniXAmounts)) {
            Toast.makeText(
                    activity,
                    "Finish the Omnibutton gesture amounts",
                    Toast.LENGTH_SHORT
            ).show();
            return;
        }
        settings.setSavedThumbnailsEnabled(savedThumbnailsEnabled);
        settings.setScrapedLinksBackupEnabled(scrapedLinksBackupEnabled);
        settings.setAutoScrapeXLinksEnabled(autoScrapeXLinksEnabled);
        settings.setPlaybackPreferences(playbackProfile, adaptiveSpeedEnabled, 0.5);
        settings.setSponsorBlockPreferences(
                sponsorBlockEnabled,
                sponsorCategoryEnabled,
                selfPromotionCategoryEnabled,
                interactionCategoryEnabled
        );
        onSettingsSaved.run();
        showAutoSaved();
    }

    private static final int[] OMNI_COLOR_PRESETS = {
            0xFF303030, 0xFF000000, 0xFFFFFFFF, 0xFFFF0033, 0xFF1565C0, 0xFF2E7D32
    };

    private EditText hexColorInput(String hint) {
        EditText input = new EditText(activity);
        input.setHint(hint);
        input.setHintTextColor(MUTED);
        input.setTextColor(Color.WHITE);
        input.setTextSize(14);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setPadding(dp(12), 0, dp(12), 0);
        input.setBackground(panelBackground(PANEL, Color.rgb(85, 85, 85)));
        input.setMinHeight(dp(44));
        return input;
    }

    private void refreshOmniAppearanceUi() {
        omniAppearanceUpdating = true;
        try {
            omniButtonHexInput.setText(formatHexColor(omniButtonColor));
            omniButtonHexInput.setError(null);
            omniIconHexInput.setText(formatHexColor(omniIconColor));
            omniIconHexInput.setError(null);
            omniOpacityInput.setText(String.valueOf(Math.round(omniOpacity * 100f)));
        } finally {
            omniAppearanceUpdating = false;
        }
        populateSwatches(omniButtonSwatches, omniButtonColor, color -> {
            omniButtonColor = color;
            refreshOmniAppearanceUi();
            saveImmediateSettings();
        });
        populateSwatches(omniIconSwatches, omniIconColor, color -> {
            omniIconColor = color;
            refreshOmniAppearanceUi();
            saveImmediateSettings();
        });
    }

    private void populateSwatches(LinearLayout row, int selected, IntConsumer onPick) {
        row.removeAllViews();
        for (int color : OMNI_COLOR_PRESETS) {
            View swatch = new View(activity);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.OVAL);
            shape.setColor(color);
            shape.setStroke(dp(2), color == selected ? ACTIVE : Color.rgb(90, 90, 90));
            swatch.setBackground(shape);
            swatch.setContentDescription("Color " + formatHexColor(color));
            swatch.setOnClickListener(ignored -> onPick.accept(color));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(36), dp(36));
            params.setMarginEnd(dp(8));
            row.addView(swatch, params);
        }
    }

    private void bindHexInput(EditText input, IntConsumer onColor) {
        input.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override
            public void afterTextChanged(Editable editable) {
                if (omniAppearanceUpdating) {
                    return;
                }
                Integer color = parseHexColor(editable.toString());
                if (color == null) {
                    input.setError("Use #RRGGBB or #AARRGGBB");
                    return;
                }
                input.setError(null);
                onColor.accept(color);
            }
        });
    }

    private static Integer parseHexColor(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (!trimmed.startsWith("#")) {
            trimmed = "#" + trimmed;
        }
        if (trimmed.length() != 7 && trimmed.length() != 9) {
            return null;
        }
        try {
            return Color.parseColor(trimmed);
        } catch (IllegalArgumentException error) {
            return null;
        }
    }

    private static String formatHexColor(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }

    private float nextOmniOpacity(float current) {
        int percent = Math.round(current * 100f);
        if (percent < 40) {
            return 0.5f;
        }
        if (percent < 65) {
            return 0.75f;
        }
        if (percent < 90) {
            return 1.0f;
        }
        return 0.25f;
    }

    private void updateOmniOpacityButton() {
        omniOpacityButton.setText(
                "Opacity: " + Math.round(omniOpacity * 100f) + "%"
        );
    }


    private void attachEditableAutoSave(EditText input) {
        input.addTextChangedListener(new SimpleTextWatcher() {
            @Override
            public void afterTextChanged(Editable editable) {
                scheduleEditableAutoSave();
            }
        });
    }

    private void scheduleEditableAutoSave() {
        if (!autoSaveReady) {
            return;
        }
        autoSaveHandler.removeCallbacks(pendingEditableSave);
        autoSaveHandler.postDelayed(pendingEditableSave, AUTO_SAVE_DELAY_MILLIS);
    }

    private void flushPendingAutoSave() {
        if (!autoSaveReady) {
            return;
        }
        autoSaveHandler.removeCallbacks(pendingEditableSave);
        saveEditableSettings();
    }

    private void saveEditableSettings() {
        if (!autoSaveReady) {
            return;
        }
        boolean changed = false;
        Double defaultSpeed = readDefaultSpeed();
        if (defaultSpeed != null
                && Double.compare(defaultSpeed, settings.getDefaultPlaybackSpeed()) != 0) {
            settings.setDefaultPlaybackSpeed(defaultSpeed);
            onDefaultSpeedSaved.run();
            changed = true;
        }

        String summaryOne = validPromptValue(
                summaryOneInput,
                settings.getSummaryOnePrompt()
        );
        String summaryTwo = validPromptValue(
                summaryTwoInput,
                settings.getSummaryTwoPrompt()
        );
        String quiz = validPromptValue(quizInput, settings.getQuizPrompt());
        String watchPath = validPromptValue(watchPathInput, settings.getWatchPathPrompt());
        if (!summaryOne.equals(settings.getSummaryOnePrompt())
                || !summaryTwo.equals(settings.getSummaryTwoPrompt())
                || !quiz.equals(settings.getQuizPrompt())
                || !watchPath.equals(settings.getWatchPathPrompt())) {
            settings.setPrompts(summaryOne, summaryTwo, quiz, watchPath);
            changed = true;
        }

        try {
            String apiKey = apiKeyInput.getText().toString();
            if (!apiKey.equals(settings.getApiKey())) {
                settings.setApiKey(apiKey);
                changed = true;
            }
            apiKeyInput.setError(null);
        } catch (GeneralSecurityException error) {
            apiKeyInput.setError("Could not store this key securely");
        }

        if (changed) {
            showAutoSaved();
        }
    }

    private String validPromptValue(EditText input, String savedValue) {
        String value = input.getText().toString();
        if (value.trim().isEmpty()) {
            input.setError("Cannot be empty");
            return savedValue;
        }
        input.setError(null);
        return value;
    }

    private void showAutoSaved() {
        if (autoSaveStatus == null) {
            return;
        }
        autoSaveHandler.removeCallbacks(hideAutoSaveStatus);
        autoSaveStatus.setText("Saved");
        autoSaveStatus.setVisibility(View.VISIBLE);
        autoSaveStatus.announceForAccessibility("Settings saved");
        autoSaveHandler.postDelayed(hideAutoSaveStatus, 1_200L);
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
        private String query = "";
        private int mode;

        ModelAdapter(List<OpenRouterClient.Model> models) {
            all = new ArrayList<>(models);
            applyFilter();
        }

        void filter(String value) {
            query = value == null ? "" : value.toLowerCase(Locale.US).trim();
            applyFilter();
        }

        void cycleMode() {
            mode = (mode + 1) % 3;
            applyFilter();
        }

        String modeLabel() {
            if (mode == 1) {
                return "Showing: Free models";
            }
            if (mode == 2) {
                return "Showing: Long context";
            }
            return "Showing: All models";
        }

        private void applyFilter() {
            visible.clear();
            for (OpenRouterClient.Model model : all) {
                boolean matchesMode = mode == 0
                        || (mode == 1 && model.isFree())
                        || (mode == 2 && model.hasLongContext());
                if (matchesMode && (query.isEmpty() || model.searchText().contains(query))) {
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
            id.setText(model.id + "\n" + model.guidance());
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
