package com.speedywatch.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import android.webkit.WebResourceResponse;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import java.io.IOException;
import java.io.InputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class MainActivity extends Activity {
    private static final String HOME_URL = "https://www.youtube.com/";
    private static final int BACKGROUND = Color.rgb(15, 15, 15);
    private static final int PANEL = Color.rgb(30, 30, 30);
    private static final int BUTTON = Color.rgb(48, 48, 48);
    private static final int ACTIVE = Color.rgb(255, 0, 51);
    private static final int REQUEST_EXPORT_BACKUP = 4101;
    private static final int REQUEST_IMPORT_BACKUP = 4102;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 4001;

    private WebView webView;
    private TextView statusText;
    private String controllerScript;
    private String chineseTranslatorScript;
    private double selectedSpeed = 1.0;
    private final Map<Double, Button> speedButtons = new LinkedHashMap<>();
    private final OpenRouterClient openRouterClient = new OpenRouterClient();
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final AtomicLong transcriptRequestCounter = new AtomicLong();
    private final SponsorBlockClient sponsorBlockClient = new SponsorBlockClient();
    private final AtomicLong sponsorBlockRequestCounter = new AtomicLong();
    private SpeedyWatchSettings appSettings;
    private SavedSummaryStore savedSummaryStore;
    private LinearLayout appRoot;
    private LinearLayout speedControls;
    private EditText customSpeedInput;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private FrameLayout screenLockShield;
    private ScreenLockButton screenLockButton;
    private boolean screenLocked;
    private int screenLockInsetRight;
    private int screenLockInsetBottom;
    private volatile YouTubeSubsDialog.TranscriptCallback activeTranscriptCallback;
    private volatile long activeTranscriptRequestId;
    private volatile boolean activeTranscriptDelivered;
    private volatile String activeTranscriptTitle = "YouTube Video";
    private volatile String activeTranscriptPageUrl = "";
    private volatile String activeVideoId = "";
    private volatile String observedCaptionRequestUrl = "";
    private volatile String observedCaptionVideoId = "";
    private volatile String activeCaptionRequestUrl = "";
    private volatile String lastSponsorLookupKey = "";
    private Runnable pendingNotificationPermissionAction;
    private SupportedSite selectedSite = SupportedSite.YOUTUBE;
    private ImageButton siteButton;
    private volatile String activeMainFrameUrl = "";
    private volatile CapturedMediaRequest capturedMediaRequest;

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        appSettings = new SpeedyWatchSettings(this);
        selectedSpeed = appSettings.getDefaultPlaybackSpeed();
        savedSummaryStore = new SavedSummaryStore(this);
        controllerScript = readAsset("speedywatch.js");
        chineseTranslatorScript = readAsset("chinese_translate.js");
        appRoot = buildUi();
        setContentView(appRoot);
        applySystemBarInsets(appRoot);
        initializeScreenLockOverlay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(
                    android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,
                    this::handleBackPressed
            );
        }

        WebView.setWebContentsDebuggingEnabled(false);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        settings.setSupportMultipleWindows(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setSafeBrowsingEnabled(true);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(webView, true);

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onShowCustomView(View view, CustomViewCallback callback) {
                showFullscreenView(view, callback);
            }

            @Override
            public void onHideCustomView() {
                hideFullscreenView();
            }
        });
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view,
                    WebResourceRequest request
            ) {
                if (request.isForMainFrame()) {
                    rememberMainFrameUrl(request.getUrl().toString());
                } else {
                    captureMediaRequest(request);
                }
                captureCaptionRequest(request.getUrl());
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) {
                    return false;
                }
                return openExternallyIfNeeded(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return openExternallyIfNeeded(Uri.parse(url));
            }
            @Override
            public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
                rememberMainFrameUrl(url);
                updateSelectedSiteForUrl(url);
                clearObservedCaptionRequestForNavigation(url);
                refreshSponsorSegments(url);
            }


            @Override
            public void onPageFinished(WebView view, String url) {
                rememberMainFrameUrl(url);
                injectController();
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                injectController();
            }


            @Override
            public void onReceivedError(
                    WebView view,
                    WebResourceRequest request,
                    WebResourceError error
            ) {
                if (request.isForMainFrame()) {
                    Toast.makeText(MainActivity.this, "This site could not be loaded", Toast.LENGTH_SHORT).show();
                }
            }
        });

        String incomingVideoUrl = incomingVideoUrl(getIntent());
        if (incomingVideoUrl != null) {
            updateSelectedSiteForUrl(incomingVideoUrl);
        }
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(incomingVideoUrl == null ? HOME_URL : incomingVideoUrl);
        } else if (incomingVideoUrl != null) {
            webView.loadUrl(incomingVideoUrl);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String videoUrl = incomingVideoUrl(intent);
        if (videoUrl != null) {
            loadSupportedUrl(videoUrl);
        } else if (Intent.ACTION_SEND.equals(intent.getAction())
                || Intent.ACTION_VIEW.equals(intent.getAction())) {
            Toast.makeText(this, "Share an HTTPS link from a supported site", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_EXPORT_BACKUP) {
            exportBackup(uri);
        } else if (requestCode == REQUEST_IMPORT_BACKUP) {
            importBackup(uri);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            String[] permissions,
            int[] grantResults
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQUEST_NOTIFICATION_PERMISSION) {
            return;
        }
        Runnable action = pendingNotificationPermissionAction;
        pendingNotificationPermissionAction = null;
        if (action == null) {
            return;
        }
        if (grantResults.length == 0
                || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(
                    this,
                    "Notifications are off; reopen SpeedyWatch after a background download to install",
                    Toast.LENGTH_LONG
            ).show();
        }
        action.run();
    }

    void runAfterNotificationPermissionDecision(Runnable action) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED) {
            action.run();
            return;
        }
        pendingNotificationPermissionAction = action;
        requestPermissions(
                new String[]{Manifest.permission.POST_NOTIFICATIONS},
                REQUEST_NOTIFICATION_PERMISSION
        );
    }

    private String incomingVideoUrl(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            return SupportedSite.supportedUrlFromText(data == null ? null : data.toString());
        }
        if (!Intent.ACTION_SEND.equals(intent.getAction())) {
            return null;
        }

        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String supported = SupportedSite.supportedUrlFromText(
                text == null ? null : text.toString()
        );
        if (supported != null) {
            return supported;
        }
        String html = intent.getStringExtra(Intent.EXTRA_HTML_TEXT);
        supported = SupportedSite.supportedUrlFromText(html);
        if (supported != null) {
            return supported;
        }
        ClipData clipData = intent.getClipData();
        if (clipData == null) {
            return null;
        }
        int itemCount = Math.min(clipData.getItemCount(), 10);
        for (int index = 0; index < itemCount; index++) {
            ClipData.Item item = clipData.getItemAt(index);
            Uri uri = item.getUri();
            supported = SupportedSite.supportedUrlFromText(
                    uri == null ? null : uri.toString()
            );
            if (supported == null && item.getText() != null) {
                supported = SupportedSite.supportedUrlFromText(item.getText().toString());
            }
            if (supported != null) {
                return supported;
            }
        }
        return null;
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout navigation = horizontalRow();
        navigation.addView(makeIconButton(
                R.drawable.ic_search,
                "Search the selected site by URL or keywords",
                ignored -> showSiteSearch()
        ));
        siteButton = makeSiteButton();
        navigation.addView(siteButton);
        navigation.addView(makeIconButton(R.drawable.ic_back, "Back", ignored -> {
            if (webView.canGoBack()) {
                webView.goBack();
            }
        }));
        navigation.addView(makeIconButton(R.drawable.ic_forward, "Forward", ignored -> {
            if (webView.canGoForward()) {
                webView.goForward();
            }
        }));
        navigation.addView(makeIconButton(
                R.drawable.ic_reload,
                "Reload",
                ignored -> webView.reload()
        ));
        navigation.addView(makeIconButton(
                R.drawable.ic_subtitles,
                "Video captions",
                ignored -> showYouTubeSubs()
        ));
        navigation.addView(makeIconButton(
                R.drawable.ic_quiz,
                "Create video quiz",
                ignored -> showQuiz()
        ));
        navigation.addView(makeIconButton(
                R.drawable.ic_download,
                "Download video or audio",
                ignored -> showDownload()
        ));
        navigation.addView(makeIconButton(
                R.drawable.ic_bookmark,
                "Saved summaries and quizzes",
                ignored -> showSavedSummaries()
        ));
        navigation.addView(makeIconButton(
                R.drawable.ic_settings,
                "Settings",
                ignored -> showSettings()
        ));
        root.addView(scrollingRow(navigation), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        webView = new WebView(this);
        webView.setBackgroundColor(BACKGROUND);
        root.addView(webView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        speedControls = new LinearLayout(this);
        speedControls.setOrientation(LinearLayout.VERTICAL);
        speedControls.setPadding(dp(8), dp(6), dp(8), dp(8));
        speedControls.setBackgroundColor(PANEL);

        LinearLayout presets = horizontalRow();
        double[] rates = {0.5, 1.0, 1.5, 2.0, 2.5, 3.0, 4.0};
        for (double rate : rates) {
            Button button = makeButton(formatRate(rate), ignored -> setSpeed(rate));
            speedButtons.put(rate, button);
            presets.addView(button);
        }

        customSpeedInput = new EditText(this);
        customSpeedInput.setSingleLine(true);
        customSpeedInput.setText(formatSpeedValue(selectedSpeed));
        customSpeedInput.setHint("2.7");
        customSpeedInput.setTextColor(Color.WHITE);
        customSpeedInput.setHintTextColor(Color.rgb(180, 180, 180));
        customSpeedInput.setTextSize(14);
        customSpeedInput.setSelectAllOnFocus(true);
        customSpeedInput.setGravity(Gravity.CENTER);
        customSpeedInput.setPadding(dp(6), 0, dp(6), 0);
        customSpeedInput.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        customSpeedInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        customSpeedInput.setBackground(outlinedBackground(BUTTON, Color.rgb(105, 105, 105), 1));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(dp(72), dp(44));
        inputParams.setMargins(dp(6), 0, dp(3), 0);
        presets.addView(customSpeedInput, inputParams);

        presets.addView(makeIconButton(
                R.drawable.ic_check,
                "Set custom speed",
                ignored -> applyCustomSpeed()
        ));
        customSpeedInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                applyCustomSpeed();
                return true;
            }
            return false;
        });
        speedControls.addView(scrollingRow(presets));

        LinearLayout actions = horizontalRow();
        actions.addView(makeButton("-0.1", ignored -> setSpeed(selectedSpeed - 0.1)));

        statusText = new TextView(this);
        statusText.setTextColor(Color.WHITE);
        statusText.setTextSize(15);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(dp(8), 0, dp(8), 0);
        statusText.setBackground(outlinedBackground(PANEL, ACTIVE, 1));
        statusText.setText(statusLabel());
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(0, dp(44), 1f);
        statusParams.setMargins(dp(3), 0, dp(3), 0);
        actions.addView(statusText, statusParams);

        actions.addView(makeButton("+0.1", ignored -> setSpeed(selectedSpeed + 0.1)));
        speedControls.addView(actions);


        root.addView(speedControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        refreshSpeedSelection();
        return root;
    }

    private LinearLayout horizontalRow() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(4), dp(4), dp(4));
        return row;
    }

    private HorizontalScrollView scrollingRow(LinearLayout row) {
        HorizontalScrollView scroll = new HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setFillViewport(true);
        scroll.addView(row, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        return scroll;
    }

    private Button makeButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(Color.WHITE);
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(12), 0, dp(12), 0);
        setButtonBackground(button, BUTTON, BUTTON, 0);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(44)
        );
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        return button;
    }
    private ImageButton makeSiteButton() {
        ImageButton button = new ImageButton(this);
        button.setScaleType(ImageButton.ScaleType.CENTER);
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(52), dp(44));
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        button.setOnClickListener(ignored -> showSitePicker());
        updateSiteButton(button);
        return button;
    }

    private ImageButton makeIconButton(
            int drawableResource,
            String description,
            View.OnClickListener listener
    ) {
        ImageButton button = new ImageButton(this);
        button.setImageResource(drawableResource);
        button.setContentDescription(description);
        button.setScaleType(ImageButton.ScaleType.CENTER);
        button.setPadding(dp(10), dp(10), dp(10), dp(10));
        GradientDrawable shape = outlinedBackground(BUTTON, BUTTON, 0);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(55, 255, 255, 255)),
                shape,
                null
        ));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(44), dp(44));
        params.setMargins(dp(3), 0, dp(3), 0);
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        return button;
    }


    private void setButtonBackground(View button, int fill, int stroke, int strokeWidthDp) {
        GradientDrawable shape = outlinedBackground(fill, stroke, strokeWidthDp);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(55, 255, 255, 255)),
                shape,
                null
        ));
    }

    private GradientDrawable outlinedBackground(int fill, int stroke, int strokeWidthDp) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(fill);
        shape.setCornerRadius(dp(4));
        if (strokeWidthDp > 0) {
            shape.setStroke(dp(strokeWidthDp), stroke);
        }
        return shape;
    }

    private void refreshSpeedSelection() {
        for (Map.Entry<Double, Button> entry : speedButtons.entrySet()) {
            boolean selected = Math.abs(entry.getKey() - selectedSpeed) < 0.001;
            setButtonBackground(
                    entry.getValue(),
                    selected ? Color.rgb(38, 38, 38) : BUTTON,
                    selected ? ACTIVE : BUTTON,
                    selected ? 2 : 0
            );
        }
    }

    private void setSpeed(double speed) {
        selectedSpeed = Math.round(Math.max(0.25, Math.min(4.0, speed)) * 100.0) / 100.0;
        if (customSpeedInput != null) {
            customSpeedInput.setText(formatSpeedValue(selectedSpeed));
        }
        refreshSpeedSelection();
        applyControllerState();
    }

    private void applyCustomSpeed() {
        String value = customSpeedInput.getText().toString().trim();
        try {
            double speed = Double.parseDouble(value);
            if (speed < 0.25 || speed > 4.0) {
                throw new NumberFormatException();
            }
            setSpeed(speed);
            customSpeedInput.clearFocus();
            InputMethodManager keyboard =
                    (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            keyboard.hideSoftInputFromWindow(customSpeedInput.getWindowToken(), 0);
        } catch (NumberFormatException error) {
            Toast.makeText(this, "Enter a speed from 0.25 to 4", Toast.LENGTH_SHORT).show();
        }
    }


    private void injectController() {
        webView.evaluateJavascript(controllerScript, ignored -> {
            applyControllerState();
            refreshSponsorSegments(webView.getUrl());
            injectChineseTranslator();
        });
    }

    private void injectChineseTranslator() {
        String currentUrl = webView.getUrl();
        if (!SupportedSite.isInAppNavigationUrl(currentUrl)
                || SupportedSite.forUrl(currentUrl) != SupportedSite.BILIBILI) {
            return;
        }
        webView.evaluateJavascript(chineseTranslatorScript, ignored -> {
        });
    }

    private void applyControllerState() {
        statusText.setText(formatRate(selectedSpeed) + " | applying");
        String script = "(() => { const c = window.__speedyWatchController; "
                + "if (!c) return 'missing'; "
                + "c.setSpeed(" + String.format(Locale.US, "%.2f", selectedSpeed) + "); "
                + "c.setAdSkipping(true); "
                + "c.setAdaptiveSpeed(" + appSettings.isAdaptiveSpeedEnabled() + ", "
                + String.format(Locale.US, "%.2f", appSettings.getAdaptiveSpeedBoost()) + "); "
                + "c.setSponsorSkipping(" + appSettings.isSponsorBlockEnabled() + "); "
                + "const media = document.querySelector('video, audio'); "
                + "return media ? 'media:' + media.playbackRate.toFixed(2) : 'ready'; })();";
        String liveResult = "\"media:" + String.format(Locale.US, "%.2f", selectedSpeed) + "\"";
        webView.evaluateJavascript(script, result -> {
            boolean ready = result != null && !result.equals("\"missing\"") && !result.equals("null");
            String label = result != null && result.equals(liveResult)
                    ? formatRate(selectedSpeed) + " live"
                            + (appSettings.isAdaptiveSpeedEnabled() ? " | adaptive" : "")
                            + siteStatusSuffix()
                    : statusLabel();
            statusText.setText(ready ? label : formatRate(selectedSpeed) + " | loading");
        });
    }

    private String statusLabel() {
        return formatRate(selectedSpeed)
                + (appSettings.isAdaptiveSpeedEnabled() ? " | adaptive" : "")
                + siteStatusSuffix();
    }

    private String siteStatusSuffix() {
        return selectedSite == SupportedSite.YOUTUBE
                ? " | ads blocked"
                : " | " + selectedSite.label;
    }

    private static String formatRate(double rate) {
        return formatSpeedValue(rate) + "x";
    }

    private static String formatSpeedValue(double rate) {
        if (rate == Math.rint(rate)) {
            return String.format(Locale.US, "%.0f", rate);
        }
        if (rate * 10 == Math.rint(rate * 10)) {
            return String.format(Locale.US, "%.1f", rate);
        }
        return String.format(Locale.US, "%.2f", rate);
    }

    private boolean openExternallyIfNeeded(Uri uri) {
        if (isAllowedNavigation(uri)) {
            return false;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (RuntimeException error) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show();
        }
        return true;
    }

    private static boolean isAllowedNavigation(Uri uri) {
        if ("about".equals(uri.getScheme())) {
            return "about:blank".equals(uri.toString());
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        if (SupportedSite.isInAppNavigationUrl(uri.toString())) {
            return true;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.US);
        return host.equals("accounts.google.com") || host.equals("consent.google.com");
    }
    private void showSitePicker() {
        SupportedSite[] sites = SupportedSite.browsableValues();
        int selectedIndex = 0;
        for (int index = 0; index < sites.length; index++) {
            if (sites[index] == selectedSite) {
                selectedIndex = index;
            }
        }
        ArrayAdapter<SupportedSite> adapter = new ArrayAdapter<SupportedSite>(
                this,
                android.R.layout.select_dialog_singlechoice,
                android.R.id.text1,
                sites
        ) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                CheckedTextView row = (CheckedTextView) super.getView(position, convertView, parent);
                SupportedSite site = getItem(position);
                if (site != null) {
                    row.setText(site.label);
                    row.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            site.iconResource,
                            0,
                            0,
                            0
                    );
                    row.setCompoundDrawablePadding(dp(12));
                    row.setMinHeight(dp(48));
                }
                return row;
            }
        };
        new AlertDialog.Builder(this)
                .setTitle("Choose site")
                .setSingleChoiceItems(adapter, selectedIndex, (dialog, which) -> {
                    selectedSite = sites[which];
                    updateSiteButton(siteButton);
                    statusText.setText(statusLabel());
                    dialog.dismiss();
                    if (selectedSite == SupportedSite.MEGA) {
                        showSiteSearch();
                    } else {
                        webView.loadUrl(selectedSite.homeUrl);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateSiteButton(ImageButton button) {
        if (button == null) {
            return;
        }
        button.setImageResource(selectedSite.iconResource);
        button.setContentDescription("Current site: " + selectedSite.label + ". Choose site");
        setButtonBackground(button, ACTIVE, ACTIVE, 0);
    }

    private void updateSelectedSiteForUrl(String url) {
        SupportedSite site = SupportedSite.forUrl(url);
        if (site == null || site == selectedSite) {
            return;
        }
        selectedSite = site;
        updateSiteButton(siteButton);
        if (statusText != null) {
            statusText.setText(statusLabel());
        }
    }

    private void loadSupportedUrl(String value) {
        String supportedUrl = SupportedSite.supportedUrlFromText(value);
        if (supportedUrl == null) {
            Toast.makeText(this, "This HTTPS site is not supported", Toast.LENGTH_SHORT).show();
            return;
        }
        updateSelectedSiteForUrl(supportedUrl);
        webView.loadUrl(supportedUrl);
    }

    private void openExternalUrl(String value) {
        String httpsUrl = SupportedSite.validatedHttpsUrl(value);
        if (httpsUrl == null) {
            Toast.makeText(this, "This link cannot be opened", Toast.LENGTH_SHORT).show();
            return;
        }
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(httpsUrl)));
        } catch (RuntimeException error) {
            Toast.makeText(this, "No app can open this link", Toast.LENGTH_SHORT).show();
        }
    }


    private void showSiteSearch() {
        boolean supportsKeywordSearch = selectedSite.supportsKeywordSearch();
        EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setHint(supportsKeywordSearch
                ? selectedSite.label + " URL or keywords"
                : "Complete " + selectedSite.label + " link");
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(180, 180, 180));
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setImeOptions(supportsKeywordSearch
                ? EditorInfo.IME_ACTION_SEARCH
                : EditorInfo.IME_ACTION_GO);
        input.setSelectAllOnFocus(true);

        String clipboardUrl = clipboardSupportedUrl();
        if (clipboardUrl != null) {
            input.setText(clipboardUrl);
            input.setSelection(input.length());
        }

        FrameLayout container = new FrameLayout(this);
        container.setPadding(dp(20), dp(8), dp(20), dp(8));
        container.addView(input, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle((supportsKeywordSearch ? "Search " : "Open ") + selectedSite.label)
                .setMessage(supportsKeywordSearch
                        ? "Enter a supported video URL or keywords."
                        : "Paste a complete MEGA folder or file link.")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(supportsKeywordSearch ? "Search" : "Open", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String value = input.getText().toString().trim();
                    String enteredUrl = SupportedSite.supportedUrlFromText(value);
                    if (enteredUrl != null) {
                        loadSupportedUrl(enteredUrl);
                        dialog.dismiss();
                        return;
                    }
                    if (!supportsKeywordSearch) {
                        input.setError("Paste a complete MEGA folder or file link");
                        return;
                    }
                    if (value.isEmpty() || value.contains("://")) {
                        input.setError("Enter a supported HTTPS URL or search words");
                        return;
                    }
                    String destination = selectedSite.searchUrl(value);
                    webView.loadUrl(destination);
                    dialog.dismiss();
                }));
        input.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH
                    || actionId == EditorInfo.IME_ACTION_GO) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                return true;
            }
            return false;
        });
        dialog.show();
    }

    private void showDownload() {
        runAfterNotificationPermissionDecision(this::showDownloadDialog);
    }

    private void showDownloadDialog() {
        String clipboardUrl = clipboardVideoUrl();
        String currentUrl = SupportedSite.isSupportedDownloadUrl(webView.getUrl())
                ? SupportedSite.validatedHttpsUrl(webView.getUrl())
                : null;
        String videoUrl = clipboardUrl != null ? clipboardUrl : currentUrl;
        String cookieHeader = videoUrl == null
                ? null
                : CookieManager.getInstance().getCookie(videoUrl);
        CapturedMediaRequest mediaRequest = clipboardUrl == null
                && capturedMediaRequest != null
                && capturedMediaRequest.matches(videoUrl)
                ? capturedMediaRequest
                : null;
        String mediaCookieHeader = mediaRequest == null ? null : mediaRequest.cookieHeader;
        if (mediaRequest != null && mediaCookieHeader == null) {
            mediaCookieHeader = CookieManager.getInstance().getCookie(mediaRequest.mediaUrl);
        }
        String initialTitle = clipboardUrl == null ? webView.getTitle() : "Video";
        new VideoDownloadDialog(
                this,
                ioExecutor,
                appSettings,
                videoUrl,
                clipboardUrl != null,
                cookieHeader,
                webView.getSettings().getUserAgentString(),
                videoUrl,
                mediaRequest,
                mediaCookieHeader,
                initialTitle,
                false
        ).show();
    }
    private void rememberMainFrameUrl(String value) {
        String valid = SupportedSite.validatedHttpsUrl(value);
        String next = valid == null ? "" : valid;
        if (!next.equals(activeMainFrameUrl)) {
            activeMainFrameUrl = next;
            capturedMediaRequest = null;
        }
    }

    private void captureMediaRequest(WebResourceRequest request) {
        CapturedMediaRequest previous = capturedMediaRequest;
        capturedMediaRequest = CapturedMediaRequest.observe(
                activeMainFrameUrl,
                request.getUrl().toString(),
                request.getMethod(),
                request.getRequestHeaders(),
                previous
        );
    }

    private String clipboardVideoUrl() {
        return clipboardUrl(true);
    }

    private String clipboardSupportedUrl() {
        return clipboardUrl(false);
    }

    private String clipboardUrl(boolean downloadOnly) {
        ClipboardManager clipboard =
                (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null || !clipboard.hasPrimaryClip()) {
            return null;
        }
        ClipData clip = clipboard.getPrimaryClip();
        if (clip == null) {
            return null;
        }
        int itemCount = Math.min(clip.getItemCount(), 10);
        for (int index = 0; index < itemCount; index++) {
            ClipData.Item item = clip.getItemAt(index);
            CharSequence text = item.getText();
            String url = clipboardUrlFromText(
                    text == null ? null : text.toString(),
                    downloadOnly
            );
            if (url == null) {
                url = clipboardUrlFromText(item.getHtmlText(), downloadOnly);
            }
            if (url == null && item.getUri() != null) {
                url = clipboardUrlFromText(item.getUri().toString(), downloadOnly);
            }
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private static String clipboardUrlFromText(String value, boolean downloadOnly) {
        return downloadOnly
                ? SupportedSite.downloadUrlFromText(value)
                : SupportedSite.supportedUrlFromText(value);
    }

    private void showSavedSummaries() {
        new SavedSummariesDialog(this, savedSummaryStore, url -> {
            if (SavedSummaryStore.isSupportedSourceUrl(url)) {
                webView.loadUrl(url);
            }
        }).show();
    }

    private void showSettings() {
        new SettingsDialog(
                this,
                appSettings,
                openRouterClient,
                ioExecutor,
                () -> {
                    setSpeed(appSettings.getDefaultPlaybackSpeed());
                    applyScreenLockSettings();
                    lastSponsorLookupKey = "";
                    refreshSponsorSegments(webView.getUrl());
                },
                this::chooseBackupDestination,
                this::chooseBackupFile
        ).show();
    }

    private void chooseBackupDestination() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json")
                .putExtra(Intent.EXTRA_TITLE, "SpeedyWatch-backup.json");
        startActivityForResult(intent, REQUEST_EXPORT_BACKUP);
    }

    private void chooseBackupFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json");
        startActivityForResult(intent, REQUEST_IMPORT_BACKUP);
    }

    private void exportBackup(Uri uri) {
        ioExecutor.execute(() -> {
            try {
                String json = AppBackup.create(appSettings, savedSummaryStore);
                try (java.io.OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) {
                        throw new IOException("Backup destination is unavailable");
                    }
                    output.write(json.getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(() ->
                        Toast.makeText(this, "Backup exported without the API key", Toast.LENGTH_LONG).show());
            } catch (Exception error) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Backup could not be exported", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void importBackup(Uri uri) {
        ioExecutor.execute(() -> {
            try {
                String json;
                try (InputStream input = getContentResolver().openInputStream(uri);
                     ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                    if (input == null) {
                        throw new IOException("Backup file is unavailable");
                    }
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        total += count;
                        if (total > AppBackup.MAXIMUM_BYTES) {
                            throw new IOException("Backup file is too large");
                        }
                        output.write(buffer, 0, count);
                    }
                    json = output.toString(StandardCharsets.UTF_8.name());
                }
                AppBackup.restore(json, appSettings, savedSummaryStore);
                runOnUiThread(() -> {
                    setSpeed(appSettings.getDefaultPlaybackSpeed());
                    applyScreenLockSettings();
                    lastSponsorLookupKey = "";
                    refreshSponsorSegments(webView.getUrl());
                    Toast.makeText(
                            this,
                            "Backup restored. The API key was left unchanged.",
                            Toast.LENGTH_LONG
                    ).show();
                });
            } catch (Exception error) {
                runOnUiThread(() ->
                        Toast.makeText(this, "Backup could not be restored", Toast.LENGTH_LONG).show());
            }
        });
    }

    private void showYouTubeSubs() {
        new YouTubeSubsDialog(
                this,
                transcriptHost(),
                appSettings,
                openRouterClient,
                ioExecutor,
                savedSummaryStore
        ).show();
    }

    private void showQuiz() {
        new VideoQuizDialog(
                this,
                transcriptHost(),
                appSettings,
                openRouterClient,
                ioExecutor,
                savedSummaryStore
        ).show();
    }

    private void refreshSponsorSegments(String pageUrl) {
        String canonical = YouTubeUrls.canonicalVideoUrl(pageUrl);
        Uri canonicalUri = canonical == null ? null : Uri.parse(canonical);
        String videoId = canonicalUri == null ? null : canonicalUri.getQueryParameter("v");
        if (!appSettings.isSponsorBlockEnabled()
                || videoId == null
                || !videoId.matches("[A-Za-z0-9_-]{11}")) {
            lastSponsorLookupKey = "";
            applySponsorSegments(false, Collections.emptyList());
            return;
        }
        Set<String> categories = new HashSet<>();
        if (appSettings.skipsSponsorSegments()) categories.add("sponsor");
        if (appSettings.skipsSelfPromotionSegments()) categories.add("selfpromo");
        if (appSettings.skipsInteractionSegments()) categories.add("interaction");
        String lookupKey = videoId + "|" + String.join(",", new java.util.TreeSet<>(categories));
        if (categories.isEmpty()) {
            lastSponsorLookupKey = "";
            applySponsorSegments(false, Collections.emptyList());
            return;
        }
        if (lookupKey.equals(lastSponsorLookupKey)) {
            return;
        }
        lastSponsorLookupKey = lookupKey;
        long requestId = sponsorBlockRequestCounter.incrementAndGet();
        applySponsorSegments(true, Collections.emptyList());
        ioExecutor.execute(() -> {
            try {
                List<SponsorBlockClient.Segment> segments = sponsorBlockClient.fetch(videoId, categories);
                runOnUiThread(() -> {
                    if (requestId == sponsorBlockRequestCounter.get()
                            && lookupKey.equals(lastSponsorLookupKey)) {
                        applySponsorSegments(true, segments);
                    }
                });
            } catch (Exception ignored) {
                runOnUiThread(() -> {
                    if (requestId == sponsorBlockRequestCounter.get()
                            && lookupKey.equals(lastSponsorLookupKey)) {
                        applySponsorSegments(true, Collections.emptyList());
                    }
                });
            }
        });
    }

    private void applySponsorSegments(boolean enabled, List<SponsorBlockClient.Segment> segments) {
        StringBuilder script = new StringBuilder(
                "(() => { const c = window.__speedyWatchController; if (!c) return false; "
                        + "c.clearSponsorSegments(); c.setSponsorSkipping(" + enabled + "); ");
        for (SponsorBlockClient.Segment segment : segments) {
            script.append("c.addSponsorSegment(")
                    .append(String.format(Locale.US, "%.3f", segment.start)).append(',')
                    .append(String.format(Locale.US, "%.3f", segment.end)).append(',')
                    .append(segment.category).append("); ");
        }
        script.append("return true; })();");
        webView.evaluateJavascript(script.toString(), null);
    }

    private YouTubeSubsDialog.TranscriptHost transcriptHost() {
        return new YouTubeSubsDialog.TranscriptHost() {
            @Override
            public void loadTranscript(
                    String languageCode,
                    YouTubeSubsDialog.TranscriptCallback callback
            ) {
                requestTranscript(languageCode, callback);
            }

            @Override
            public void loadCaptionOptions(YouTubeSubsDialog.CaptionOptionsCallback callback) {
                requestCaptionOptions(callback);
            }

            @Override
            public void seekTo(double seconds) {
                seekVideo(seconds);
            }

            @Override
            public void currentTime(YouTubeSubsDialog.CurrentTimeCallback callback) {
                String script = "window.__speedyWatchController "
                        + "? window.__speedyWatchController.currentTime() : null";
                webView.evaluateJavascript(script, result -> {
                    double seconds = Double.NaN;
                    try {
                        Object value = new JSONTokener(result == null ? "null" : result).nextValue();
                        if (value instanceof Number number) {
                            seconds = number.doubleValue();
                        }
                    } catch (Exception ignored) {
                        // The transcript remains usable when playback time is unavailable.
                    }
                    callback.onTime(seconds);
                });
            }

            @Override
            public String sourceLabel() {
                SupportedSite site = SupportedSite.forUrl(webView.getUrl());
                return (site == null ? "Video" : site.label) + " captions";
            }
        };
    }

    private void requestTranscript(
            String languageCode,
            YouTubeSubsDialog.TranscriptCallback callback
    ) {
        String pageUrl = webView.getUrl();
        Uri pageUri = pageUrl == null ? null : Uri.parse(pageUrl);
        String videoId = pageUri == null ? null : pageUri.getQueryParameter("v");
        boolean isYouTubeWatch = pageUri != null
                && pageUri.getHost() != null
                && pageUri.getHost().toLowerCase(Locale.US).endsWith("youtube.com")
                && "/watch".equals(pageUri.getPath())
                && videoId != null
                && videoId.matches("[A-Za-z0-9_-]{11}");
        if (!isYouTubeWatch) {
            if (SupportedSite.forUrl(pageUrl) != SupportedSite.YOUTUBE
                    && SupportedSite.isSupportedDownloadUrl(pageUrl)) {
                requestGenericTranscript(pageUrl, languageCode, callback);
            } else {
                callback.onError("Open a supported video first");
            }
            return;
        }

        long requestId = transcriptRequestCounter.incrementAndGet();
        activeTranscriptRequestId = requestId;
        activeTranscriptCallback = callback;
        activeTranscriptDelivered = false;
        activeTranscriptTitle = "YouTube Video";
        activeTranscriptPageUrl = pageUrl;
        activeVideoId = videoId;
        activeCaptionRequestUrl = "";
        if (!videoId.equals(observedCaptionVideoId)) {
            observedCaptionRequestUrl = "";
            observedCaptionVideoId = "";
        }

        if (languageCode != null && !languageCode.isEmpty()) {
            loadInnerTubeCaptionTrack(requestId, languageCode);
            return;
        }

        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.getCaptionTrack() : null";
        webView.evaluateJavascript(script, result -> handleCaptionTrackResult(requestId, result));
    }
    private void requestGenericTranscript(
            String pageUrl,
            String languageCode,
            YouTubeSubsDialog.TranscriptCallback callback
    ) {
        String validPageUrl = SupportedSite.validatedHttpsUrl(pageUrl);
        if (!SupportedSite.isSupportedDownloadUrl(validPageUrl)) {
            callback.onError("Open a supported video first");
            return;
        }
        long requestId = transcriptRequestCounter.incrementAndGet();
        activeTranscriptRequestId = requestId;
        activeTranscriptCallback = callback;
        activeTranscriptDelivered = false;
        activeTranscriptTitle = "Video";
        activeTranscriptPageUrl = validPageUrl;
        activeVideoId = "";
        activeCaptionRequestUrl = "";
        String cookieHeader = CookieManager.getInstance().getCookie(validPageUrl);
        String userAgent = webView.getSettings().getUserAgentString();
        ioExecutor.execute(() -> {
            try {
                MediaTranscriptEngine.Result result = MediaTranscriptEngine.loadTranscript(
                        this,
                        validPageUrl,
                        languageCode,
                        cookieHeader,
                        userAgent
                );
                activeTranscriptTitle = result.title;
                activeTranscriptPageUrl = result.pageUrl;
                deliverTranscript(requestId, result.entries);
            } catch (Exception error) {
                deliverTranscriptError(requestId, readableTranscriptError(error));
            }
        });
    }

    private static String readableTranscriptError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Captions could not be loaded";
        }
        String firstLine = message.trim().split("\\R", 2)[0];
        return firstLine.length() > 140 ? firstLine.substring(0, 140) : firstLine;
    }


    private void handleCaptionTrackResult(long requestId, String evaluationResult) {
        if (requestId != activeTranscriptRequestId || activeTranscriptDelivered) {
            return;
        }
        try {
            Object outer = new JSONTokener(evaluationResult == null ? "null" : evaluationResult).nextValue();
            if (!(outer instanceof String json)) {
                loadObservedCaptionTrackOrInnerTube(requestId);
                return;
            }
            JSONObject metadata = new JSONObject(json);
            String title = metadata.optString("title", "").trim();
            if (!title.isEmpty()) {
                activeTranscriptTitle = title;
            }
            String baseUrl = metadata.optString("baseUrl", "").trim();
            if (baseUrl.isEmpty() || !isTrustedCaptionUri(Uri.parse(baseUrl))) {
                loadObservedCaptionTrackOrInnerTube(requestId);
                return;
            }
            fetchTranscriptUrl(requestId, baseUrl, true);
        } catch (Exception error) {
            loadObservedCaptionTrackOrInnerTube(requestId);
        }
    }

    private void loadObservedCaptionTrackOrInnerTube(long requestId) {
        String url = observedCaptionRequestUrl;
        if (activeVideoId.equals(observedCaptionVideoId)
                && !url.isEmpty()
                && isTrustedCaptionUri(Uri.parse(url))) {
            activeCaptionRequestUrl = url;
            fetchTranscriptUrl(requestId, url, true);
            return;
        }
        loadInnerTubeCaptionTrack(requestId);
    }

    private void loadInnerTubeCaptionTrack(long requestId) {
        String videoId = activeVideoId;
        ioExecutor.execute(() -> {
            try {
                String baseUrl = fetchInnerTubeCaptionUrl(videoId);
                runOnUiThread(() -> fetchTranscriptUrl(requestId, baseUrl, true));
            } catch (Exception error) {
                runOnUiThread(() -> triggerCaptionNetworkRequest(requestId));
            }
        });
    }

    private void loadInnerTubeCaptionTrack(long requestId, String selectionKey) {
        String videoId = activeVideoId;
        ioExecutor.execute(() -> {
            try {
                String baseUrl = fetchInnerTubeCaptionUrl(videoId, selectionKey);
                runOnUiThread(() -> fetchTranscriptUrl(requestId, baseUrl, false));
            } catch (Exception error) {
                runOnUiThread(() ->
                        deliverTranscriptError(requestId, "The selected caption language could not be loaded"));
            }
        });
    }

    private void requestCaptionOptions(YouTubeSubsDialog.CaptionOptionsCallback callback) {
        String pageUrl = webView.getUrl();
        if (SupportedSite.forUrl(pageUrl) != SupportedSite.YOUTUBE) {
            String validPageUrl = SupportedSite.validatedHttpsUrl(pageUrl);
            if (!SupportedSite.isSupportedDownloadUrl(validPageUrl)) {
                callback.onError("Open a supported video first");
                return;
            }
            String cookieHeader = CookieManager.getInstance().getCookie(validPageUrl);
            String userAgent = webView.getSettings().getUserAgentString();
            ioExecutor.execute(() -> {
                try {
                    List<YouTubeSubsDialog.CaptionOption> options =
                            MediaTranscriptEngine.loadOptions(
                                    this,
                                    validPageUrl,
                                    cookieHeader,
                                    userAgent
                            );
                    runOnUiThread(() -> callback.onLoaded(options));
                } catch (Exception error) {
                    runOnUiThread(() ->
                            callback.onError("Caption languages could not be loaded"));
                }
            });
            return;
        }

        String videoId = activeVideoId;
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) {
            callback.onError("Open a YouTube video first");
            return;
        }
        ioExecutor.execute(() -> {
            try {
                JSONArray tracks = fetchInnerTubeCaptionTracks(videoId);
                List<YouTubeSubsDialog.CaptionOption> options = new ArrayList<>();
                for (int index = 0; index < tracks.length(); index++) {
                    JSONObject track = tracks.optJSONObject(index);
                    if (track == null) {
                        continue;
                    }
                    String key = captionTrackKey(track);
                    String label = captionTrackLabel(track);
                    if (!key.isEmpty() && !label.isEmpty()) {
                        options.add(new YouTubeSubsDialog.CaptionOption(key, label));
                    }
                }
                runOnUiThread(() -> callback.onLoaded(options));
            } catch (Exception error) {
                runOnUiThread(() -> callback.onError("Caption languages could not be loaded"));
            }
        });
    }

    private String fetchInnerTubeCaptionUrl(String videoId) throws Exception {
        return fetchInnerTubeCaptionUrl(videoId, "");
    }

    private String fetchInnerTubeCaptionUrl(String videoId, String selectionKey) throws Exception {
        JSONArray tracks = fetchInnerTubeCaptionTracks(videoId);
        JSONObject selected = selectCaptionTrack(tracks, selectionKey);
        String baseUrl = selected == null ? "" : selected.optString("baseUrl", "").trim();
        if (baseUrl.isEmpty() || !isTrustedCaptionUri(Uri.parse(baseUrl))) {
            throw new IOException("No caption track was available");
        }
        return baseUrl;
    }

    private JSONArray fetchInnerTubeCaptionTracks(String videoId) throws Exception {
        URL endpoint = new URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false");
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        try {
            JSONObject client = new JSONObject()
                    .put("clientName", "ANDROID")
                    .put("clientVersion", "21.26.364")
                    .put("androidSdkVersion", 30)
                    .put("osName", "Android")
                    .put("osVersion", "11");
            JSONObject payload = new JSONObject()
                    .put("context", new JSONObject().put("client", client))
                    .put("videoId", videoId);
            byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);

            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setUseCaches(false);
            connection.setFixedLengthStreamingMode(body.length);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty(
                    "User-Agent",
                    "com.google.android.youtube/21.26.364 (Linux; U; Android 11) gzip"
            );
            connection.setRequestProperty("X-YouTube-Client-Name", "3");
            connection.setRequestProperty("X-YouTube-Client-Version", "21.26.364");
            connection.getOutputStream().write(body);
            if (connection.getResponseCode() < 200 || connection.getResponseCode() >= 300) {
                throw new IOException("YouTube player request failed");
            }

            String responseText;
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 4 * 1024 * 1024) {
                        throw new IOException("YouTube player response was too large");
                    }
                    output.write(buffer, 0, count);
                }
                responseText = new String(output.toByteArray(), StandardCharsets.UTF_8);
            }

            JSONObject response = new JSONObject(responseText);
            JSONObject playability = response.optJSONObject("playabilityStatus");
            if (playability != null && !"OK".equals(playability.optString("status"))) {
                throw new IOException("YouTube video is unavailable");
            }
            JSONObject captions = response.optJSONObject("captions");
            JSONObject renderer = captions == null
                    ? null : captions.optJSONObject("playerCaptionsTracklistRenderer");
            JSONArray tracks = renderer == null ? null : renderer.optJSONArray("captionTracks");
            if (tracks == null || tracks.length() == 0) {
                throw new IOException("No caption track was available");
            }
            return tracks;
        } finally {
            connection.disconnect();
        }
    }

    private static JSONObject selectCaptionTrack(JSONArray tracks, String selectionKey) {
        if (tracks == null || tracks.length() == 0) {
            return null;
        }
        if (selectionKey != null && !selectionKey.isEmpty()) {
            for (int index = 0; index < tracks.length(); index++) {
                JSONObject track = tracks.optJSONObject(index);
                if (track != null && selectionKey.equals(captionTrackKey(track))) {
                    return track;
                }
            }
            return null;
        }
        String deviceLanguage = Locale.getDefault().getLanguage();
        JSONObject deviceMatch = findCaptionLanguage(tracks, deviceLanguage);
        if (deviceMatch != null) {
            return deviceMatch;
        }
        JSONObject englishMatch = findCaptionLanguage(tracks, "en");
        if (englishMatch != null) {
            return englishMatch;
        }
        for (int index = 0; index < tracks.length(); index++) {
            JSONObject track = tracks.optJSONObject(index);
            if (track != null && !"asr".equals(track.optString("kind"))) {
                return track;
            }
        }
        return tracks.optJSONObject(0);
    }

    private static JSONObject findCaptionLanguage(JSONArray tracks, String language) {
        for (int index = 0; index < tracks.length(); index++) {
            JSONObject track = tracks.optJSONObject(index);
            String code = track == null ? "" : track.optString("languageCode", "");
            if (code.equalsIgnoreCase(language) || code.toLowerCase(Locale.US)
                    .startsWith(language.toLowerCase(Locale.US) + "-")) {
                return track;
            }
        }
        return null;
    }

    private static String captionTrackKey(JSONObject track) {
        String code = track.optString("languageCode", "").trim();
        if (code.isEmpty()) {
            return "";
        }
        return code + ("asr".equals(track.optString("kind")) ? "|asr" : "|manual");
    }

    private static String captionTrackLabel(JSONObject track) {
        JSONObject name = track.optJSONObject("name");
        String label = name == null ? "" : name.optString("simpleText", "").trim();
        if (label.isEmpty() && name != null) {
            JSONArray runs = name.optJSONArray("runs");
            StringBuilder combined = new StringBuilder();
            if (runs != null) {
                for (int index = 0; index < runs.length(); index++) {
                    JSONObject run = runs.optJSONObject(index);
                    if (run != null) {
                        combined.append(run.optString("text", ""));
                    }
                }
            }
            label = combined.toString().trim();
        }
        if (label.isEmpty()) {
            label = track.optString("languageCode", "").trim();
        }
        return label + ("asr".equals(track.optString("kind")) ? " (auto-generated)" : "");
    }

    private void triggerCaptionNetworkRequest(long requestId) {
        if (requestId != activeTranscriptRequestId || activeTranscriptDelivered) {
            return;
        }
        activeCaptionRequestUrl = "";
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.requestCaptions() : 'missing'";
        webView.evaluateJavascript(script, result -> {
            if (!"\"triggered\"".equals(result)) {
                deliverTranscriptError(requestId, "No subtitles are available for this video");
                return;
            }
            webView.postDelayed(() -> {
                if (requestId == activeTranscriptRequestId && !activeTranscriptDelivered) {
                    deliverTranscriptError(requestId, "Subtitles could not be loaded");
                }
            }, 15000);
        });
    }

    private void captureCaptionRequest(Uri uri) {
        if (!isTrustedCaptionUri(uri)) {
            return;
        }
        String videoId = uri.getQueryParameter("v");
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}")) {
            return;
        }
        String url = uri.toString();
        observedCaptionRequestUrl = url;
        observedCaptionVideoId = videoId;
        if (activeTranscriptCallback == null
                || activeTranscriptDelivered
                || !videoId.equals(activeVideoId)
                || url.equals(activeCaptionRequestUrl)) {
            return;
        }
        activeCaptionRequestUrl = url;
        long requestId = activeTranscriptRequestId;
        runOnUiThread(() -> fetchTranscriptUrl(requestId, url, false));
    }

    private void clearObservedCaptionRequestForNavigation(String pageUrl) {
        Uri pageUri = pageUrl == null ? null : Uri.parse(pageUrl);
        String videoId = pageUri == null ? null : pageUri.getQueryParameter("v");
        if (videoId == null
                || !videoId.matches("[A-Za-z0-9_-]{11}")
                || !videoId.equals(observedCaptionVideoId)) {
            observedCaptionRequestUrl = "";
            observedCaptionVideoId = "";
        }
    }

    private void fetchTranscriptUrl(long requestId, String url, boolean allowClickFallback) {
        if (requestId != activeTranscriptRequestId || activeTranscriptDelivered) {
            return;
        }
        String cookie = CookieManager.getInstance().getCookie(url);
        String userAgent = webView.getSettings().getUserAgentString();
        ioExecutor.execute(() -> {
            try {
                List<TranscriptEntry> entries = downloadTranscript(url, cookie, userAgent);
                deliverTranscript(requestId, entries);
            } catch (Exception error) {
                if (allowClickFallback) {
                    runOnUiThread(() -> triggerCaptionNetworkRequest(requestId));
                } else {
                    runOnUiThread(() ->
                            deliverTranscriptError(requestId, "Subtitles could not be loaded"));
                }
            }
        });
    }

    private List<TranscriptEntry> downloadTranscript(
            String captionUrl,
            String cookie,
            String userAgent
    ) throws Exception {
        List<String> candidates = new ArrayList<>();
        String jsonUrl = captionUrl.contains("fmt=")
                ? captionUrl.replaceFirst("([?&])fmt=[^&]*", "$1fmt=json3")
                : captionUrl + (captionUrl.contains("?") ? "&" : "?") + "fmt=json3";
        candidates.add(jsonUrl);
        if (!jsonUrl.equals(captionUrl)) {
            candidates.add(captionUrl);
        }

        Exception lastError = null;
        for (String candidate : candidates) {
            try {
                String body = downloadCaptionBody(candidate, cookie, userAgent);
                if (!body.trim().isEmpty() && body.trim().startsWith("{")) {
                    List<TranscriptEntry> entries = parseTranscriptJson(body);
                    if (!entries.isEmpty()) {
                        return entries;
                    }
                }
            } catch (Exception error) {
                lastError = error;
            }
        }
        throw lastError == null
                ? new IOException("Caption response was empty") : lastError;
    }

    private String downloadCaptionBody(String url, String cookie, String userAgent) throws IOException {
        Uri uri = Uri.parse(url);
        if (!isTrustedCaptionUri(uri)) {
            throw new IOException("Untrusted caption URL");
        }
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(30000);
            connection.setUseCaches(false);
            connection.setRequestProperty("Accept", "application/json,text/plain,*/*");
            connection.setRequestProperty("Referer", "https://www.youtube.com/");
            connection.setRequestProperty("Origin", "https://www.youtube.com");
            if (userAgent != null && !userAgent.trim().isEmpty()) {
                connection.setRequestProperty("User-Agent", userAgent);
            }
            if (cookie != null && !cookie.trim().isEmpty()) {
                connection.setRequestProperty("Cookie", cookie);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                throw new IOException("Caption request failed");
            }
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int total = 0;
                int count;
                while ((count = input.read(buffer)) != -1) {
                    total += count;
                    if (total > 8 * 1024 * 1024) {
                        throw new IOException("Caption response was too large");
                    }
                    output.write(buffer, 0, count);
                }
                return new String(output.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static List<TranscriptEntry> parseTranscriptJson(String body) throws Exception {
        JSONArray events = new JSONObject(body).optJSONArray("events");
        if (events == null) {
            throw new IOException("Caption response contained no events");
        }
        List<TranscriptEntry> entries = new ArrayList<>();
        for (int eventIndex = 0; eventIndex < events.length(); eventIndex++) {
            JSONObject event = events.optJSONObject(eventIndex);
            JSONArray segments = event == null ? null : event.optJSONArray("segs");
            if (segments == null) {
                continue;
            }
            StringBuilder text = new StringBuilder();
            for (int segmentIndex = 0; segmentIndex < segments.length(); segmentIndex++) {
                JSONObject segment = segments.optJSONObject(segmentIndex);
                if (segment != null) {
                    text.append(segment.optString("utf8", ""));
                }
            }
            String normalized = text.toString().replaceAll("\\s+", " ").trim();
            if (!normalized.isEmpty()) {
                entries.add(new TranscriptEntry(
                        event.optDouble("tStartMs", 0) / 1000.0,
                        event.optDouble("dDurationMs", 0) / 1000.0,
                        normalized
                ));
            }
        }
        return entries;
    }

    private static boolean isTrustedCaptionUri(Uri uri) {
        if (uri == null || !"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        String path = uri.getPath();
        if (host == null || path == null) {
            return false;
        }
        host = host.toLowerCase(Locale.US);
        return (host.equals("youtube.com") || host.endsWith(".youtube.com"))
                && path.startsWith("/api/timedtext");
    }

    private void deliverTranscript(long requestId, List<TranscriptEntry> entries) {
        runOnUiThread(() -> {
            if (requestId != activeTranscriptRequestId || activeTranscriptDelivered) {
                return;
            }
            if (entries.isEmpty()) {
                triggerCaptionNetworkRequest(requestId);
                return;
            }
            activeTranscriptDelivered = true;
            YouTubeSubsDialog.TranscriptCallback callback = activeTranscriptCallback;
            activeTranscriptCallback = null;
            if (callback != null) {
                callback.onLoaded(entries, activeTranscriptTitle, activeTranscriptPageUrl);
            }
        });
    }

    private void deliverTranscriptError(long requestId, String message) {
        runOnUiThread(() -> {
            if (requestId != activeTranscriptRequestId || activeTranscriptDelivered) {
                return;
            }
            activeTranscriptDelivered = true;
            YouTubeSubsDialog.TranscriptCallback callback = activeTranscriptCallback;
            activeTranscriptCallback = null;
            if (callback != null) {
                callback.onError(message);
            }
        });
    }

    private void seekVideo(double seconds) {
        if (!Double.isFinite(seconds)) {
            return;
        }
        double bounded = Math.max(0, Math.min(604800, seconds));
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.seekTo("
                + String.format(Locale.US, "%.3f", bounded)
                + ") : false";
        webView.evaluateJavascript(script, ignored -> {
        });
    }

    private String readAsset(String name) {
        StringBuilder result = new StringBuilder();
        try (InputStream stream = getAssets().open(name);
             InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            char[] buffer = new char[4096];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                result.append(buffer, 0, count);
            }
        } catch (IOException error) {
            throw new IllegalStateException("Missing controller asset", error);
        }
        return result.toString();
    }






    private void initializeScreenLockOverlay() {
        screenLockShield = new FrameLayout(this);
        screenLockShield.setBackgroundColor(Color.TRANSPARENT);
        screenLockShield.setClickable(false);
        screenLockButton = new ScreenLockButton(this, new ScreenLockButton.Listener() {
            @Override
            public void onLockRequested() {
                setScreenLocked(true);
            }

            @Override
            public void onUnlockRequested() {
                setScreenLocked(false);
            }

        });
        int buttonSize = dp(52);
        screenLockShield.addView(screenLockButton, new FrameLayout.LayoutParams(
                buttonSize,
                buttonSize,
                Gravity.BOTTOM | Gravity.END
        ));
        addContentView(screenLockShield, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        screenLockShield.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets safeInsets = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                screenLockInsetRight = safeInsets.right;
                screenLockInsetBottom = safeInsets.bottom;
            } else {
                screenLockInsetRight = insets.getSystemWindowInsetRight();
                screenLockInsetBottom = insets.getSystemWindowInsetBottom();
            }
            positionScreenLockButton();
            return insets;
        });
        applyScreenLockSettings();
        screenLockShield.requestApplyInsets();
    }

    private void applyScreenLockSettings() {
        if (screenLockShield == null) {
            return;
        }
        if (!appSettings.isLockIconEnabled()) {
            setScreenLocked(false);
            screenLockShield.setVisibility(View.GONE);
            return;
        }
        screenLockShield.setVisibility(View.VISIBLE);
        positionScreenLockButton();
        screenLockShield.bringToFront();
    }

    private void positionScreenLockButton() {
        if (screenLockButton == null) {
            return;
        }
        screenLockShield.post(() -> {
            if (screenLockShield.getWidth() == 0 || screenLockShield.getHeight() == 0) {
                return;
            }
            FrameLayout.LayoutParams params =
                    (FrameLayout.LayoutParams) screenLockButton.getLayoutParams();
            params.gravity = Gravity.BOTTOM | Gravity.END;
            params.leftMargin = 0;
            params.topMargin = 0;
            params.rightMargin = screenLockInsetRight + dp(8);
            params.bottomMargin = screenLockInsetBottom
                    + (speedControls == null ? 0 : speedControls.getHeight())
                    + dp(8);
            screenLockButton.setLayoutParams(params);
        });
    }


    private void setScreenLocked(boolean locked) {
        screenLocked = locked;
        screenLockShield.setClickable(locked);
        screenLockButton.setLocked(locked);
        if (screenLockShield.getVisibility() == View.VISIBLE) {
            screenLockShield.bringToFront();
        }
    }

    private void showFullscreenView(View view, WebChromeClient.CustomViewCallback callback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden();
            return;
        }
        fullscreenView = view;
        fullscreenCallback = callback;
        fullscreenView.setBackgroundColor(Color.BLACK);
        appRoot.setVisibility(View.GONE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        addContentView(fullscreenView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        setFullscreenSystemBars(true);
        if (screenLockShield.getVisibility() == View.VISIBLE) {
            screenLockShield.bringToFront();
            screenLockShield.requestApplyInsets();
        }
    }

    private void hideFullscreenView() {
        if (fullscreenView == null) {
            return;
        }
        ViewGroup parent = (ViewGroup) fullscreenView.getParent();
        if (parent != null) {
            parent.removeView(fullscreenView);
        }
        fullscreenView = null;
        appRoot.setVisibility(View.VISIBLE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
        setFullscreenSystemBars(false);
        appRoot.requestApplyInsets();
        if (screenLockShield.getVisibility() == View.VISIBLE) {
            screenLockShield.bringToFront();
            screenLockShield.requestApplyInsets();
        }
        if (fullscreenCallback != null) {
            fullscreenCallback.onCustomViewHidden();
            fullscreenCallback = null;
        }
    }

    private void setFullscreenSystemBars(boolean fullscreen) {
        View decor = getWindow().getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                if (fullscreen) {
                    controller.hide(WindowInsets.Type.systemBars());
                    controller.setSystemBarsBehavior(
                            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    );
                } else {
                    controller.show(WindowInsets.Type.systemBars());
                }
            }
        } else {
            decor.setSystemUiVisibility(fullscreen
                    ? View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    : View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    private void applySystemBarInsets(View root) {
        root.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            } else {
                view.setPadding(
                        insets.getSystemWindowInsetLeft(),
                        insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(),
                        insets.getSystemWindowInsetBottom()
                );
            }
            return insets;
        });
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        webView.saveState(outState);
        super.onSaveInstanceState(outState);
    }

    @Override
    public void onBackPressed() {
        handleBackPressed();
    }

    private void handleBackPressed() {
        if (screenLocked) {
            return;
        }
        if (fullscreenView != null) {
            hideFullscreenView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            finish();
        }
    }

    @Override
    protected void onPause() {
        GitHubUpdateChecker.unregisterResumedActivity(this);
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
        GitHubUpdateChecker.registerResumedActivity(this);
        GitHubUpdateChecker.resumePendingInstaller(this);
    }

    @Override
    protected void onDestroy() {
        hideFullscreenView();
        ioExecutor.shutdownNow();
        savedSummaryStore.close();
        webView.loadUrl("about:blank");
        webView.removeAllViews();
        webView.destroy();
        super.onDestroy();
    }
}
