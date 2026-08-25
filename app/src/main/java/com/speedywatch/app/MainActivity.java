package com.speedywatch.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.PictureInPictureParams;
import android.app.PendingIntent;
import android.app.RemoteAction;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.BroadcastReceiver;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.Context;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Insets;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.InputType;
import android.util.Rational;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.ViewConfiguration;
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

import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

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
import java.net.URI;
import java.security.GeneralSecurityException;
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
    private static final String YOUTUBE_HISTORY_URL =
            "https://www.youtube.com/feed/history";
    private static final String YOUTUBE_WATCH_LATER_URL =
            "https://www.youtube.com/playlist?list=WL";
    private static final int BACKGROUND = Color.rgb(15, 15, 15);
    private static final int PANEL = Color.rgb(30, 30, 30);
    private static final int BUTTON = Color.rgb(48, 48, 48);
    private static final int ACTIVE = Color.rgb(255, 0, 51);
    private static final long OMNI_DRAG_HOLD_MILLIS = 350L;
    private static final long OMNI_EMPHASIS_MILLIS = 100L;
    private static final float OMNI_PRESS_SCALE = 1.08f;
    private static final float OMNI_DRAG_READY_SCALE = 1.25f;
    private static final int REQUEST_EXPORT_BACKUP = 4101;
    private static final int REQUEST_IMPORT_BACKUP = 4102;
    private static final int REQUEST_NOTIFICATION_PERMISSION = 4001;
    private static final String ACTION_TOGGLE_PICTURE_IN_PICTURE_PLAYBACK =
            "com.speedywatch.app.action.TOGGLE_PICTURE_IN_PICTURE_PLAYBACK";

    private WebView webView;
    private TextView statusText;
    private String controllerScript;
    private String chineseTranslatorScript;
    private String frameControllerScript;
    private boolean frameControllerEnabled;
    private double selectedSpeed = 1.0;
    private final Map<Double, Button> speedButtons = new LinkedHashMap<>();
    private final OpenRouterClient openRouterClient = new OpenRouterClient();
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final AtomicLong transcriptRequestCounter = new AtomicLong();
    private final AtomicLong xLinkRequestCounter = new AtomicLong();
    private final SponsorBlockClient sponsorBlockClient = new SponsorBlockClient();
    private final AtomicLong sponsorBlockRequestCounter = new AtomicLong();
    private final AtomicLong chapterRequestCounter = new AtomicLong();
    private SpeedyWatchSettings appSettings;
    private SavedSummaryStore savedSummaryStore;
    private MegaBookmarkStore megaBookmarkStore;
    private ScrapedLinkStore scrapedLinkStore;
    private LinearLayout appRoot;
    private LinearLayout speedControls;
    private View speedControlsContent;
    private View collapsedSpeedControlsRow;
    private View navigationControls;
    private LinearLayout watchPathControls;
    private TextView watchPathStatus;
    private Button watchPathPreviousButton;
    private Button watchPathNextButton;
    private Button watchPathUndoButton;
    private final Handler watchPathHandler = new Handler(Looper.getMainLooper());
    private final Runnable watchPathTick = this::pollWatchPath;
    private final Handler chapterHandler = new Handler(Looper.getMainLooper());
    private final Runnable chapterRefreshTick = this::refreshChapters;
    private final Handler pictureInPictureHandler = new Handler(Looper.getMainLooper());
    private final Runnable pictureInPictureTick = this::pollPictureInPictureState;
    private final Runnable pictureInPictureTransitionTimeout =
            this::finishPictureInPictureTransition;
    private final Runnable pictureInPictureResumePlayback =
            this::resumePictureInPicturePlayback;
    private final Handler megaResumeHandler = new Handler(Looper.getMainLooper());
    private final Runnable megaResumeTick = this::attemptPendingMegaResume;
    private final Handler megaBookmarkPositionHandler = new Handler(Looper.getMainLooper());
    private final Runnable megaBookmarkPositionTick = this::captureActiveMegaBookmarkPosition;
    private WatchPathPlayback activeWatchPath;
    private boolean watchPathForeground;
    private boolean activityResumed;
    private boolean pictureInPicturePlaybackActive;
    private boolean pictureInPictureEntryPending;
    private boolean pictureInPictureParamsActive;
    private boolean pictureInPictureUiPrepared;
    private boolean pictureInPictureParamsPlaying;
    private boolean pictureInPicturePlaybackRequested;
    private Rect pictureInPictureSourceRect = new Rect();
    private PendingMegaResume pendingMegaResume;
    private Rect pictureInPictureParamsSourceRect = new Rect();
    private PictureInPictureParams pictureInPictureParams =
            new PictureInPictureParams.Builder().build();
    private boolean watchPathVisibleBeforePictureInPicture;
    private boolean pictureInPictureReceiverRegistered;
    private final BroadcastReceiver pictureInPictureReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (ACTION_TOGGLE_PICTURE_IN_PICTURE_PLAYBACK.equals(intent.getAction())) {
                togglePictureInPicturePlayback();
            }
        }
    };
    private EditText customSpeedInput;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private FrameLayout screenLockShield;
    private ScreenLockButton screenLockButton;
    private ImageButton pictureInPictureButton;
    private ImageButton omniButton;
    private ImageButton previousChapterButton;
    private ImageButton nextChapterButton;
    private List<DescriptionChapters.Chapter> activeChapters = List.of();
    private final RectF activeVideoBounds = new RectF();
    private float pictureInPicturePinchStartSpan;
    private boolean pictureInPicturePinchEligible;
    private boolean pictureInPicturePinchTriggered;
    private boolean mediaGestureConsumed;
    private boolean chapterSwipeEligible;
    private float chapterSwipeStartX;
    private float chapterSwipeStartY;
    private boolean screenLocked;
    private int screenLockInsetLeft;
    private int screenLockInsetTop;
    private int screenLockInsetRight;
    private int screenLockInsetBottom;
    private volatile YouTubeSubsDialog.TranscriptCallback activeTranscriptCallback;
    private volatile long activeTranscriptRequestId;
    private volatile boolean activeTranscriptDelivered;
    private volatile String activeTranscriptTitle = "YouTube Video";
    private volatile String activeTranscriptChannel = "";
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
        megaBookmarkStore = new MegaBookmarkStore(this);
        scrapedLinkStore = new ScrapedLinkStore(this);
        controllerScript = readAsset("speedywatch.js");
        frameControllerScript = readAsset("speedywatch-frame.js");
        chineseTranslatorScript = readAsset("chinese_translate.js");
        appRoot = buildUi();
        setContentView(appRoot);
        applySystemBarInsets(appRoot);
        initializeScreenLockOverlay();
        initializeMediaGestures();
        registerPictureInPictureReceiver();
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
        installFrameController();

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
                scheduleChapterRefresh();
                collectXThreadLinks();
                updateOmniButtonContentDescription();
                if (activeWatchPath != null && !watchPathSourceMatches(url)) {
                    stopWatchPath(false);
                }
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

        String incomingPageUrl = incomingPageUrl(getIntent());
        if (incomingPageUrl != null) {
            updateSelectedSiteForUrl(incomingPageUrl);
        }
        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(incomingPageUrl == null ? HOME_URL : incomingPageUrl);
        } else if (incomingPageUrl != null) {
            webView.loadUrl(incomingPageUrl);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String pageUrl = incomingPageUrl(intent);
        if (pageUrl != null) {
            loadBrowsableUrl(pageUrl);
        } else if (Intent.ACTION_SEND.equals(intent.getAction())
                || Intent.ACTION_VIEW.equals(intent.getAction())) {
            Toast.makeText(this, "Share a valid public HTTPS link", Toast.LENGTH_SHORT).show();
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

    private String incomingPageUrl(Intent intent) {
        if (intent == null) {
            return null;
        }
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri data = intent.getData();
            return SupportedSite.browsableUrlFromText(data == null ? null : data.toString());
        }
        if (!Intent.ACTION_SEND.equals(intent.getAction())) {
            return null;
        }

        CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
        String browsable = SupportedSite.browsableUrlFromText(
                text == null ? null : text.toString()
        );
        if (browsable != null) {
            return browsable;
        }
        String html = intent.getStringExtra(Intent.EXTRA_HTML_TEXT);
        browsable = SupportedSite.browsableUrlFromText(html);
        if (browsable != null) {
            return browsable;
        }
        ClipData clipData = intent.getClipData();
        if (clipData == null) {
            return null;
        }
        int itemCount = Math.min(clipData.getItemCount(), 10);
        for (int index = 0; index < itemCount; index++) {
            ClipData.Item item = clipData.getItemAt(index);
            Uri uri = item.getUri();
            browsable = SupportedSite.browsableUrlFromText(
                    uri == null ? null : uri.toString()
            );
            if (browsable == null && item.getText() != null) {
                browsable = SupportedSite.browsableUrlFromText(item.getText().toString());
            }
            if (browsable != null) {
                return browsable;
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
        previousChapterButton = makeIconButton(
                R.drawable.ic_previous_chapter,
                "Previous chapter. You can also swipe left",
                ignored -> navigateChapter(false)
        );
        navigation.addView(previousChapterButton);
        nextChapterButton = makeIconButton(
                R.drawable.ic_next_chapter,
                "Next chapter. You can also swipe right",
                ignored -> navigateChapter(true)
        );
        navigation.addView(nextChapterButton);
        updateChapterButtons();
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
                R.drawable.ic_links,
                "Save visible page links",
                ignored -> saveLinksManually()
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
        navigationControls = scrollingRow(navigation);
        root.addView(navigationControls, new LinearLayout.LayoutParams(
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

        watchPathControls = buildWatchPathControls();
        watchPathControls.setVisibility(View.GONE);
        root.addView(watchPathControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        speedControls = new LinearLayout(this);
        speedControls.setOrientation(LinearLayout.VERTICAL);
        speedControls.setPadding(dp(8), dp(6), dp(8), dp(8));
        speedControls.setBackgroundColor(PANEL);

        LinearLayout speedContent = new LinearLayout(this);
        speedContent.setOrientation(LinearLayout.VERTICAL);
        speedControlsContent = speedContent;

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
        customSpeedInput.setInputType(
                InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
        );
        customSpeedInput.setImeOptions(EditorInfo.IME_ACTION_DONE);
        customSpeedInput.setBackground(
                outlinedBackground(BUTTON, Color.rgb(105, 105, 105), 1)
        );
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
        speedContent.addView(scrollingRow(presets));

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
        actions.addView(makeIconButton(
                R.drawable.ic_collapse,
                "Minimize speed controls",
                ignored -> applySpeedControlsCollapsed(true, true)
        ));
        speedContent.addView(actions);
        speedControls.addView(speedContent);

        LinearLayout collapsedRow = horizontalRow();
        collapsedRow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        collapsedRow.addView(makeIconButton(
                R.drawable.ic_expand,
                "Restore speed controls",
                ignored -> applySpeedControlsCollapsed(false, true)
        ));
        collapsedSpeedControlsRow = collapsedRow;
        speedControls.addView(collapsedRow);

        root.addView(speedControls, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        applySpeedControlsCollapsed(appSettings.areSpeedControlsCollapsed(), false);
        refreshSpeedSelection();
        return root;
    }

    private LinearLayout buildWatchPathControls() {
        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(8), dp(6), dp(8), dp(6));
        controls.setBackground(outlinedBackground(PANEL, ACTIVE, 1));

        watchPathStatus = new TextView(this);
        watchPathStatus.setTextColor(Color.WHITE);
        watchPathStatus.setTextSize(13);
        watchPathStatus.setSingleLine(true);
        watchPathStatus.setPadding(dp(6), 0, dp(6), dp(4));
        controls.addView(watchPathStatus, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout actions = horizontalRow();
        watchPathPreviousButton = addWatchPathButton(
                actions,
                "Previous",
                ignored -> applyWatchPathAction(activeWatchPath == null
                        ? WatchPathPlayback.Action.NONE
                        : activeWatchPath.previous())
        );
        watchPathNextButton = addWatchPathButton(
                actions,
                "Next",
                ignored -> applyWatchPathAction(activeWatchPath == null
                        ? WatchPathPlayback.Action.NONE
                        : activeWatchPath.next())
        );
        watchPathUndoButton = addWatchPathButton(
                actions,
                "Undo",
                ignored -> applyWatchPathAction(activeWatchPath == null
                        ? WatchPathPlayback.Action.NONE
                        : activeWatchPath.undo())
        );
        addWatchPathButton(actions, "Stop", ignored -> stopWatchPath(false));
        controls.addView(actions);
        return controls;
    }

    private Button addWatchPathButton(
            LinearLayout row,
            String label,
            View.OnClickListener listener
    ) {
        Button button = makeButton(label, listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(44), 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        row.addView(button, params);
        return button;
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

    private void applySpeedControlsCollapsed(boolean collapsed, boolean persist) {
        if (speedControlsContent == null || collapsedSpeedControlsRow == null) {
            return;
        }
        speedControlsContent.setVisibility(collapsed ? View.GONE : View.VISIBLE);
        collapsedSpeedControlsRow.setVisibility(collapsed ? View.VISIBLE : View.GONE);
        speedControls.setPadding(
                dp(collapsed ? 4 : 8),
                dp(collapsed ? 2 : 6),
                dp(collapsed ? 4 : 8),
                dp(collapsed ? 2 : 8)
        );
        speedControls.setBackgroundColor(collapsed ? Color.TRANSPARENT : PANEL);
        if (persist) {
            appSettings.setSpeedControlsCollapsed(collapsed);
        }
        speedControls.requestLayout();
        speedControls.post(this::positionFloatingControls);
    }


    /**
     * Installs the fixed controller in every frame. It exposes no Android bridge; frames only
     * exchange bounded media status and commands with the top-page controller.
     */
    private void installFrameController() {
        frameControllerEnabled =
                WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT);
        if (!frameControllerEnabled) {
            return;
        }
        WebViewCompat.addDocumentStartJavaScript(
                webView,
                frameControllerScript,
                Collections.singleton("*")
        );
    }

    private void injectController() {
        webView.evaluateJavascript(controllerScript, ignored -> {
            applyControllerState();
            scheduleChapterRefresh();
            refreshSponsorSegments(webView.getUrl());
            injectChineseTranslator();
            attemptPendingMegaResume();
            collectXThreadLinks();
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

    private void collectXThreadLinks() {
        String sourceUrl = webView.getUrl();
        if (!appSettings.isAutoScrapeXLinksEnabled()
                || sourceUrl == null
                || SupportedSite.forUrl(sourceUrl) != SupportedSite.X) {
            return;
        }
        long requestId = xLinkRequestCounter.incrementAndGet();
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.collectXLinks() : null";
        webView.evaluateJavascript(script, result -> {
            if (requestId != xLinkRequestCounter.get()
                    || sourceUrl == null
                    || !sourceUrl.equals(webView.getUrl())) {
                return;
            }
            handleXLinkResult(result, sourceUrl, false);
        });
    }

    private void saveLinksManually() {
        String sourceUrl = webView.getUrl();
        if (sourceUrl == null
                || SupportedSite.validatedHttpsUrl(sourceUrl) == null
                || SupportedSite.forUrl(sourceUrl) == SupportedSite.MEGA) {
            Toast.makeText(this, "This page cannot save links", Toast.LENGTH_SHORT).show();
            return;
        }
        long requestId = xLinkRequestCounter.incrementAndGet();
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.collectPageLinks() : null";
        webView.evaluateJavascript(script, result -> {
            if (requestId != xLinkRequestCounter.get()
                    || sourceUrl == null
                    || !sourceUrl.equals(webView.getUrl())) {
                return;
            }
            handleXLinkResult(result, sourceUrl, true);
        });
    }

    private void handleXLinkResult(String result, String sourceUrl, boolean manual) {
        List<ScrapedLinkCandidate> candidates = parseXLinkResult(result);
        if (candidates.isEmpty()) {
            if (manual) {
                runOnUiThread(() -> Toast.makeText(
                        MainActivity.this,
                        "No links found on this page yet",
                        Toast.LENGTH_SHORT
                ).show());
            }
            return;
        }
        ioExecutor.execute(() -> {
            long now = System.currentTimeMillis();
            int added = 0;
            List<Long> newIds = new ArrayList<>();
            List<String> newUrls = new ArrayList<>();
            Map<Long, String> shortLinks = new LinkedHashMap<>();
            for (ScrapedLinkCandidate candidate : candidates) {
                long id = scrapedLinkStore.record(
                        candidate.url,
                        candidate.hint,
                        candidate.text,
                        candidate.poster,
                        sourceUrl,
                        candidate.postedAt,
                        now
                );
                if (id > 0) {
                    added++;
                    newIds.add(id);
                    newUrls.add(candidate.url);
                    if (ScrapedLinkStore.isShortLink(candidate.url)) {
                        shortLinks.put(id, candidate.url);
                    }
                }
            }
            int expansionLimit = Math.min(20, shortLinks.size());
            int expanded = 0;
            for (Map.Entry<Long, String> entry : shortLinks.entrySet()) {
                if (expanded >= expansionLimit) {
                    break;
                }
                String destination = expandShortLink(entry.getValue());
                if (destination != null && !ScrapedLinkStore.isShortLink(destination)) {
                    scrapedLinkStore.updateExpandedUrl(entry.getKey(), destination);
                }
                expanded++;
            }
            int previewLimit = Math.min(20, newIds.size());
            for (int index = 0; index < previewLimit; index++) {
                ScrapedLinkStore.Entry entry = scrapedLinkStore.get(newIds.get(index));
                if (entry == null || entry.preview != null) {
                    continue;
                }
                try {
                    byte[] preview = OpenGraphPreview.fetch(entry.url);
                    if (preview != null) {
                        scrapedLinkStore.updatePreview(entry.id, preview);
                    }
                } catch (IOException ignored) {
                    // Link saving remains successful when an optional preview is unavailable.
                }
            }
            final int savedCount = added;
            final List<String> copiedUrls = new ArrayList<>(newUrls);
            runOnUiThread(() -> {
                if (!manual) {
                    if (savedCount > 0) {
                        statusText.setText("Links saved");
                    }
                    return;
                }
                if (savedCount == 0) {
                    Toast.makeText(
                            MainActivity.this,
                            "No new links on this page",
                            Toast.LENGTH_SHORT
                    ).show();
                    return;
                }
                ClipboardManager clipboard =
                        (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText(
                        "Links", String.join("\n", copiedUrls)));
                Toast.makeText(
                        MainActivity.this,
                        "Saved " + savedCount
                                + (savedCount == 1 ? " new link" : " new links")
                                + ", copied to clipboard",
                        Toast.LENGTH_LONG
                ).show();
            });
        });
    }

    private List<ScrapedLinkCandidate> parseXLinkResult(String result) {
        List<ScrapedLinkCandidate> candidates = new ArrayList<>();
        try {
            Object value = new JSONTokener(result == null ? "null" : result).nextValue();
            if (value instanceof String encoded) {
                value = new JSONTokener(encoded).nextValue();
            }
            if (!(value instanceof JSONObject payload)) {
                return candidates;
            }
            JSONArray links = payload.optJSONArray("links");
            if (links == null) {
                return candidates;
            }
            for (int index = 0; index < links.length() && index < 500; index++) {
                JSONObject link = links.optJSONObject(index);
                if (link == null) {
                    continue;
                }
                String url = boundedText(link.optString("url"), 2000);
                if (url.isEmpty()) {
                    continue;
                }
                candidates.add(new ScrapedLinkCandidate(
                        url,
                        boundedText(link.optString("hint"), 2000),
                        boundedText(link.optString("text"), 500),
                        boundedText(link.optString("poster"), 120),
                        parseXPostedAt(link.optString("at"))
                ));
            }
        } catch (org.json.JSONException | RuntimeException ignored) {
            // Malformed controller output simply yields no candidates.
        }
        return candidates;
    }

    private static String boundedText(String value, int maximumLength) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.length() > maximumLength
                ? trimmed.substring(0, maximumLength) : trimmed;
    }

    /** One link harvested from an X thread by the injected controller. */
    private static final class ScrapedLinkCandidate {
        final String url;
        final String hint;
        final String text;
        final String poster;
        final Long postedAt;

        ScrapedLinkCandidate(
                String url,
                String hint,
                String text,
                String poster,
                Long postedAt
        ) {
            this.url = url;
            this.hint = hint;
            this.text = text;
            this.poster = poster;
            this.postedAt = postedAt;
        }
    }

    private static Long parseXPostedAt(String isoTimestamp) {
        if (isoTimestamp == null || isoTimestamp.isEmpty()) {
            return null;
        }
        try {
            return java.time.Instant.parse(isoTimestamp).toEpochMilli();
        } catch (RuntimeException ignored) {
            // Fall through to offset parsing for non-UTC timestamps.
        }
        try {
            return java.time.OffsetDateTime.parse(isoTimestamp).toInstant().toEpochMilli();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    /** Follows a short-link redirect chain manually with HTTPS-only hops and hard bounds. */
    private static String expandShortLink(String shortUrl) {
        URI current;
        try {
            current = URI.create(shortUrl);
        } catch (RuntimeException error) {
            return null;
        }
        for (int hop = 0; hop < 4 && current != null; hop++) {
            if (!"https".equalsIgnoreCase(current.getScheme())) {
                return null;
            }
            try {
                java.net.HttpURLConnection connection =
                        (java.net.HttpURLConnection) current.toURL().openConnection();
                connection.setInstanceFollowRedirects(false);
                connection.setConnectTimeout(8000);
                connection.setReadTimeout(8000);
                connection.setRequestMethod("GET");
                int status = connection.getResponseCode();
                String location = status >= 300 && status < 400
                        ? connection.getHeaderField("Location") : null;
                connection.disconnect();
                if (location == null) {
                    return status >= 200 && status < 300 ? current.toString() : null;
                }
                URI next = current.resolve(location.trim());
                current = next.equals(current) ? null : next;
            } catch (IOException | RuntimeException error) {
                return null;
            }
        }
        return null;
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
                + "const status = c.status(); "
                + "return status.hasMedia ? 'media:' + status.speed.toFixed(2) : 'ready'; })();";
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

    private void scheduleChapterRefresh() {
        chapterHandler.removeCallbacks(chapterRefreshTick);
        chapterRequestCounter.incrementAndGet();
        activeChapters = List.of();
        updateChapterButtons();
        chapterHandler.postDelayed(chapterRefreshTick, 800);
        chapterHandler.postDelayed(chapterRefreshTick, 2500);
    }

    private void refreshChapters() {
        requestChapterContext(null);
    }

    private void navigateChapter(boolean next) {
        chapterHandler.removeCallbacks(chapterRefreshTick);
        requestChapterContext(next);
    }

    private void requestChapterContext(Boolean navigateNext) {
        String sourceUrl = webView.getUrl();
        long requestId = chapterRequestCounter.incrementAndGet();
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.chapterContext() : null";
        webView.evaluateJavascript(script, result -> {
            if (requestId != chapterRequestCounter.get()
                    || sourceUrl == null
                    || !sourceUrl.equals(webView.getUrl())) {
                return;
            }
            List<DescriptionChapters.Chapter> chapters = List.of();
            double currentTime = Double.NaN;
            try {
                JSONObject context = new JSONObject(result == null ? "{}" : result);
                String videoId = context.optString("videoId", "");
                String description = context.optString("description", "");
                double duration = context.optDouble("duration", Double.NaN);
                currentTime = context.optDouble("currentTime", Double.NaN);
                if (videoId.matches("[A-Za-z0-9_-]{11}")) {
                    chapters = DescriptionChapters.parse(description, duration);
                }
            } catch (Exception ignored) {
                // Missing or malformed page data means chapter controls stay unavailable.
            }
            activeChapters = chapters;
            updateChapterButtons();
            if (navigateNext == null) {
                return;
            }
            DescriptionChapters.Chapter target =
                    DescriptionChapters.target(chapters, currentTime, navigateNext);
            if (target == null) {
                String message = chapters.isEmpty()
                        ? "No description chapters found"
                        : (navigateNext ? "Already at the last chapter" : "Already at the first chapter");
                Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
                return;
            }
            seekToChapter(sourceUrl, target);
        });
    }

    private void seekToChapter(String sourceUrl, DescriptionChapters.Chapter chapter) {
        if (!sourceUrl.equals(webView.getUrl())) {
            return;
        }
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.seekTo("
                + String.format(Locale.US, "%.3f", chapter.startSeconds)
                + ") : false";
        webView.evaluateJavascript(script, result -> {
            if ("true".equals(result) && sourceUrl.equals(webView.getUrl())) {
                Toast.makeText(
                        this,
                        "Chapter: " + chapter.title,
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void updateChapterButtons() {
        boolean available = !activeChapters.isEmpty();
        if (previousChapterButton != null) {
            previousChapterButton.setEnabled(available);
            previousChapterButton.setAlpha(available ? 1f : 0.45f);
        }
        if (nextChapterButton != null) {
            nextChapterButton.setEnabled(available);
            nextChapterButton.setAlpha(available ? 1f : 0.45f);
        }
    }

    private void initializeMediaGestures() {
        webView.setOnTouchListener(this::handleMediaGesture);
    }

    private boolean handleMediaGesture(View ignored, MotionEvent event) {
        if (screenLocked) {
            return false;
        }
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            mediaGestureConsumed = false;
            pictureInPicturePinchStartSpan = 0f;
            pictureInPicturePinchEligible = false;
            pictureInPicturePinchTriggered = false;
            chapterSwipeStartX = event.getX();
            chapterSwipeStartY = event.getY();
            chapterSwipeEligible = DescriptionChapters.swipeStartsInActiveRegion(
                    !activeChapters.isEmpty(),
                    chapterSwipeStartY,
                    webView.getHeight()
            );
        } else if (action == MotionEvent.ACTION_POINTER_DOWN
                && event.getPointerCount() == 2) {
            float focusX = (event.getX(0) + event.getX(1)) / 2f;
            float focusY = (event.getY(0) + event.getY(1)) / 2f;
            pictureInPicturePinchStartSpan = pictureInPicturePinchSpan(event);
            pictureInPicturePinchEligible =
                    SpeedyWatchSettings.PIP_CONTROL_PINCH.equals(
                            appSettings.getPictureInPictureControl()
                    )
                    && pictureInPicturePlaybackActive
                    && pictureInPicturePinchStartSpan >= dp(48)
                    && activeVideoBounds.contains(focusX, focusY);
            chapterSwipeEligible = false;
        }

        if (pictureInPicturePinchEligible
                && !pictureInPicturePinchTriggered
                && action == MotionEvent.ACTION_MOVE
                && event.getPointerCount() >= 2
                && pictureInPicturePinchSpan(event)
                <= pictureInPicturePinchStartSpan * 0.72f) {
            pictureInPicturePinchTriggered = true;
            cancelWebViewGesture(event);
            mediaGestureConsumed = true;
            webView.post(MainActivity.this::enterPictureInPictureFromButton);
        } else if (!mediaGestureConsumed
                && chapterSwipeEligible
                && action == MotionEvent.ACTION_MOVE
                && event.getPointerCount() == 1) {
            float deltaX = event.getX() - chapterSwipeStartX;
            float deltaY = event.getY() - chapterSwipeStartY;
            int swipeDirection = DescriptionChapters.swipeDirection(deltaX, deltaY, dp(72));
            if (swipeDirection != 0) {
                cancelWebViewGesture(event);
                mediaGestureConsumed = true;
                chapterSwipeEligible = false;
                navigateChapter(swipeDirection > 0);
            }
        }

        boolean consumed = mediaGestureConsumed;
        if (action == MotionEvent.ACTION_POINTER_UP && !pictureInPicturePinchTriggered) {
            pictureInPicturePinchEligible = false;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            chapterSwipeEligible = false;
            pictureInPicturePinchEligible = false;
            mediaGestureConsumed = false;
        }
        return consumed;
    }

    private static float pictureInPicturePinchSpan(MotionEvent event) {
        float deltaX = event.getX(0) - event.getX(1);
        float deltaY = event.getY(0) - event.getY(1);
        return (float) Math.hypot(deltaX, deltaY);
    }

    private void cancelWebViewGesture(MotionEvent event) {
        MotionEvent cancel = MotionEvent.obtain(event);
        cancel.setAction(MotionEvent.ACTION_CANCEL);
        webView.onTouchEvent(cancel);
        cancel.recycle();
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
        return SupportedSite.isInAppNavigationUrl(uri.toString());
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
                    if (selectedSite == SupportedSite.MEGA
                            || selectedSite == SupportedSite.WEB) {
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
        if (site == null && SupportedSite.isInAppNavigationUrl(url)) {
            site = SupportedSite.WEB;
        }
        if (site == null || site == selectedSite) {
            return;
        }
        selectedSite = site;
        updateSiteButton(siteButton);
        if (statusText != null) {
            statusText.setText(statusLabel());
        }
    }

    private void loadBrowsableUrl(String value) {
        String browsableUrl = SupportedSite.browsableUrlFromText(value);
        if (browsableUrl == null) {
            Toast.makeText(this, "Enter a valid public HTTPS URL", Toast.LENGTH_SHORT).show();
            return;
        }
        updateSelectedSiteForUrl(browsableUrl);
        webView.loadUrl(browsableUrl);
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
        if (selectedSite == SupportedSite.MEGA) {
            showMegaBookmarks();
            return;
        }
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

        String clipboardUrl = clipboardBrowsableUrl();
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
                .setTitle((supportsKeywordSearch ? "Search or open a page" : "Open ") + selectedSite.label)
                .setMessage(supportsKeywordSearch
                        ? "Enter any public HTTPS page or search words."
                        : "Paste a complete MEGA folder or file link.")
                .setView(container)
                .setNegativeButton("Cancel", null)
                .setPositiveButton(supportsKeywordSearch ? "Go" : "Open", null)
                .create();
        dialog.setOnShowListener(ignored -> dialog
                .getButton(AlertDialog.BUTTON_POSITIVE)
                .setOnClickListener(button -> {
                    String value = input.getText().toString().trim();
                    String enteredUrl = selectedSite == SupportedSite.MEGA
                            ? SupportedSite.supportedUrlFromText(value)
                            : SupportedSite.browsableUrlFromText(value);
                    if (enteredUrl != null) {
                        loadBrowsableUrl(enteredUrl);
                        dialog.dismiss();
                        return;
                    }
                    if (!supportsKeywordSearch) {
                        input.setError("Paste a complete MEGA folder or file link");
                        return;
                    }
                    if (value.isEmpty() || value.contains("://")) {
                        input.setError("Enter a public HTTPS URL or search words");
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

    private void showMegaBookmarks() {
        String clipboardUrl = clipboardBrowsableUrl();
        if (SupportedSite.megaPlaybackIdentity(clipboardUrl) == null) {
            clipboardUrl = null;
        }
        new MegaBookmarksDialog(
                this,
                megaBookmarkStore,
                new MegaBookmarksDialog.Host() {
                    @Override
                    public String currentMegaUrl() {
                        return SupportedSite.megaPlaybackIdentity(webView.getUrl());
                    }

                    @Override
                    public void currentMegaFolderName(
                            MegaBookmarksDialog.FolderNameCallback callback
                    ) {
                        queryMegaFolderName(callback);
                    }

                    @Override
                    public void currentTime(MegaBookmarksDialog.CurrentTimeCallback callback) {
                        queryCurrentTime(callback::onTime);
                    }

                    @Override
                    public void openMegaUrl(String url, double positionSeconds) {
                        openMegaBookmark(url, positionSeconds);
                    }
                },
                clipboardUrl
        ).show();
    }

    private void openMegaBookmark(String url, double positionSeconds) {
        String valid = SupportedSite.megaPlaybackIdentity(url);
        if (valid == null) {
            Toast.makeText(this, "This MEGA link cannot be opened", Toast.LENGTH_SHORT).show();
            return;
        }
        megaResumeHandler.removeCallbacks(megaResumeTick);
        pendingMegaResume = positionSeconds >= 0.25
                ? new PendingMegaResume(valid, positionSeconds) : null;
        loadBrowsableUrl(valid);
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
        CapturedMediaRequest mediaRequest = clipboardUrl == null
                && capturedMediaRequest != null
                && capturedMediaRequest.matches(videoUrl)
                ? capturedMediaRequest
                : null;
        if (mediaRequest == null && currentUrl != null
                && SupportedSite.forUrl(currentUrl) == SupportedSite.FACEBOOK) {
            requestFacebookPageMedia(currentUrl, videoUrl, clipboardUrl != null);
            return;
        }
        openDownloadDialog(
                videoUrl,
                clipboardUrl != null,
                videoUrl == null ? null : CookieManager.getInstance().getCookie(videoUrl),
                mediaRequest,
                clipboardUrl == null ? webView.getTitle() : "Video"
        );
    }

    /** Facebook embeds player media inside page data instead of fetching a
     *  manifest URL, so no request capture fires; ask the injected controller
     *  to extract those URLs and offer them as a captured download target. */
    private void requestFacebookPageMedia(String pageUrl, String videoUrl, boolean fromClipboard) {
        String script = "(function(){try{return window.__speedyWatchController"
                + "&&window.__speedyWatchController.facebookMedia"
                + "?window.__speedyWatchController.facebookMedia()"
                + ":JSON.stringify({url:\"\"})"
                + "}catch(error){return JSON.stringify({url:\"\"})}})()";
        webView.evaluateJavascript(script, result -> {
            CapturedMediaRequest found = null;
            try {
                Object outer = new JSONTokener(result == null ? "null" : result).nextValue();
                if (outer instanceof String payload) {
                    JSONObject parsed = new JSONObject(payload);
                    String mediaUrl = parsed.optString("url", "");
                    if (mediaUrl.startsWith("https://")) {
                        found = CapturedMediaRequest.fromPageData(
                                pageUrl,
                                mediaUrl,
                                CookieManager.getInstance().getCookie(pageUrl),
                                webView.getSettings().getUserAgentString()
                        );
                    }
                }
            } catch (Exception ignored) {
                found = null;
            }
            openDownloadDialog(
                    videoUrl,
                    fromClipboard,
                    videoUrl == null ? null : CookieManager.getInstance().getCookie(videoUrl),
                    found,
                    fromClipboard ? "Video" : webView.getTitle()
            );
        });
    }

    private void openDownloadDialog(
            String videoUrl,
            boolean fromClipboard,
            String cookieHeader,
            CapturedMediaRequest mediaRequest,
            String initialTitle
    ) {
        String mediaCookieHeader = mediaRequest == null ? null : mediaRequest.cookieHeader;
        if (mediaRequest != null && mediaCookieHeader == null) {
            mediaCookieHeader = CookieManager.getInstance().getCookie(mediaRequest.mediaUrl);
        }
        new VideoDownloadDialog(
                this,
                ioExecutor,
                appSettings,
                videoUrl,
                fromClipboard,
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
            String previous = activeMainFrameUrl;
            activeMainFrameUrl = next;
            capturedMediaRequest = null;
            if (SupportedSite.megaPlaybackIdentity(previous) != null) {
                runOnUiThread(() -> captureMegaBookmarkPosition(previous));
            }
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

    private String clipboardBrowsableUrl() {
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
                : SupportedSite.browsableUrlFromText(value);
    }

    private void showSavedSummaries() {
        new SavedSummariesDialog(
                this,
                savedSummaryStore,
                scrapedLinkStore,
                ioExecutor,
                appSettings.areSavedThumbnailsEnabled(),
                new SavedSummariesDialog.Host() {
                    @Override
                    public void openVideo(String url) {
                        if (SavedSummaryStore.isSupportedSourceUrl(url)) {
                            webView.loadUrl(url);
                        }
                    }

                    @Override
                    public void openLink(String url) {
                        String valid = SupportedSite.validatedHttpsUrl(url);
                        if (valid == null) {
                            Toast.makeText(MainActivity.this,
                                    "This link is unavailable", Toast.LENGTH_LONG).show();
                            return;
                        }
                        loadBrowsableUrl(valid);
                    }
                }
        ).show();
    }

    private void showSettings() {
        new SettingsDialog(
                this,
                appSettings,
                openRouterClient,
                ioExecutor,
                () -> {
                    applyScreenLockSettings();
                    lastSponsorLookupKey = "";
                    refreshSponsorSegments(webView.getUrl());
                },
                () -> setSpeed(appSettings.getDefaultPlaybackSpeed()),
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
                String json = AppBackup.create(appSettings, savedSummaryStore, scrapedLinkStore);
                try (java.io.OutputStream output = getContentResolver().openOutputStream(uri, "wt")) {
                    if (output == null) {
                        throw new IOException("Backup destination is unavailable");
                    }
                    output.write(json.getBytes(StandardCharsets.UTF_8));
                }
                runOnUiThread(() ->
                        Toast.makeText(this, "Backup exported without protected keys or links", Toast.LENGTH_LONG).show());
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
                AppBackup.restore(json, appSettings, savedSummaryStore, scrapedLinkStore);
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
        showYouTubeSubs(YouTubeSubsDialog.InitialSummary.NONE);
    }

    private void showYouTubeSubs(YouTubeSubsDialog.InitialSummary initialSummary) {
        new YouTubeSubsDialog(
                this,
                transcriptHost(),
                appSettings,
                openRouterClient,
                ioExecutor,
                savedSummaryStore,
                initialSummary
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
            public void startWatchPath(WatchPathPlan plan) {
                MainActivity.this.startWatchPath(plan);
            }


            @Override
            public void currentTime(YouTubeSubsDialog.CurrentTimeCallback callback) {
                queryCurrentTime(callback);
            }

            @Override
            public boolean isTextSource() {
                return activePageUsesTextSource();
            }

            @Override
            public String sourceLabel() {
                SupportedSite site = SupportedSite.forUrl(webView.getUrl());
                if (site == SupportedSite.X) {
                    return "X page text";
                }
                if (activePageUsesTextSource()) {
                    return "Page text";
                }
                return (site == null ? "Video" : site.label) + " captions";
            }
        };
    }

    private boolean activePageIsX() {
        return SupportedSite.forUrl(webView.getUrl()) == SupportedSite.X;
    }

    private boolean activePageUsesTextSource() {
        String pageUrl = webView.getUrl();
        if (SupportedSite.forUrl(pageUrl) == SupportedSite.X) {
            return true;
        }
        Uri uri = pageUrl == null ? null : Uri.parse(pageUrl);
        String videoId = uri == null ? null : uri.getQueryParameter("v");
        boolean youtubeWatch = uri != null
                && uri.getHost() != null
                && uri.getHost().toLowerCase(Locale.US).endsWith("youtube.com")
                && "/watch".equals(uri.getPath())
                && videoId != null
                && videoId.matches("[A-Za-z0-9_-]{11}");
        return !youtubeWatch && !SupportedSite.isSupportedDownloadUrl(pageUrl);
    }

    private void requestXPageTranscript(YouTubeSubsDialog.TranscriptCallback callback) {
        String pageUrl = webView.getUrl();
        long requestId = transcriptRequestCounter.incrementAndGet();
        activeTranscriptRequestId = requestId;
        activeTranscriptCallback = callback;
        activeTranscriptDelivered = false;
        activeTranscriptTitle = "X page";
        activeTranscriptChannel = "";
        activeTranscriptPageUrl = SupportedSite.validatedHttpsUrl(webView.getUrl());
        activeVideoId = "";
        activeCaptionRequestUrl = "";
        Uri pageUri = pageUrl == null ? null : Uri.parse(pageUrl);
        String path = pageUri == null || pageUri.getPath() == null
                ? "" : pageUri.getPath();
        // Summaries target posts, self-threads, and long-form articles only;
        // home timelines and other X pages have no single text source.
        if (!path.matches(".*/status/\\d+.*") && !path.matches("/i/articles?/.*")) {
            callback.onError("Open an X post or article first");
            return;
        }
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.collectXPageText() : null";
        webView.evaluateJavascript(script, result -> handleXPageTextResult(requestId, result));
    }

    private void handleXPageTextResult(long requestId, String evaluationResult) {
        if (requestId != activeTranscriptRequestId || activeTranscriptDelivered) {
            return;
        }
        try {
            Object outer = new JSONTokener(
                    evaluationResult == null ? "null" : evaluationResult
            ).nextValue();
            if (!(outer instanceof String json)) {
                throw new IOException("Controller was unavailable");
            }
            JSONObject metadata = new JSONObject(json);
            JSONArray blocks = metadata.optJSONArray("blocks");
            List<TranscriptEntry> entries = new ArrayList<>();
            if (blocks != null) {
                for (int index = 0; index < blocks.length(); index++) {
                    String block = blocks.optString(index, "").trim();
                    if (!block.isEmpty()) {
                        entries.add(TranscriptEntry.textEntry(block));
                    }
                }
            }
            if (entries.isEmpty()) {
                throw new IOException("No readable text found");
            }
            boolean article = "article".equals(metadata.optString("kind", ""));
            String author = SavedSummaryStore.normalizeChannel(metadata.optString("author", ""));
            String title = metadata.optString("title", "").trim();
            activeTranscriptTitle = article
                    ? (title.isEmpty() ? "X article" : title)
                    : "X post";
            activeTranscriptChannel = author;
            deliverTranscript(requestId, entries);
        } catch (Exception error) {
            deliverTranscriptError(requestId, "Could not read text from this X page");
        }
    }

    private void requestPageTranscript(YouTubeSubsDialog.TranscriptCallback callback) {
        String pageUrl = SupportedSite.validatedHttpsUrl(webView.getUrl());
        if (!SupportedSite.isShareablePageUrl(pageUrl)) {
            callback.onError("Open a public HTTPS page first");
            return;
        }
        long requestId = transcriptRequestCounter.incrementAndGet();
        activeTranscriptRequestId = requestId;
        activeTranscriptCallback = callback;
        activeTranscriptDelivered = false;
        activeTranscriptTitle = webView.getTitle() == null
                ? "Web page" : webView.getTitle().trim();
        activeTranscriptChannel = URI.create(pageUrl).getHost();
        activeTranscriptPageUrl = pageUrl;
        activeVideoId = "";
        activeCaptionRequestUrl = "";
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.collectPageContent() : null";
        webView.evaluateJavascript(script,
                result -> handlePageContentResult(requestId, result));
    }

    private void handlePageContentResult(long requestId, String evaluationResult) {
        if (requestId != activeTranscriptRequestId || activeTranscriptDelivered) {
            return;
        }
        try {
            Object outer = new JSONTokener(
                    evaluationResult == null ? "null" : evaluationResult
            ).nextValue();
            if (!(outer instanceof String json)) {
                throw new IOException("Controller was unavailable");
            }
            JSONObject metadata = new JSONObject(json);
            JSONArray blocks = metadata.optJSONArray("blocks");
            List<TranscriptEntry> entries = new ArrayList<>();
            if (blocks != null) {
                for (int index = 0; index < blocks.length() && entries.size() < 400; index++) {
                    String block = blocks.optString(index, "").trim();
                    if (!block.isEmpty()) {
                        entries.add(TranscriptEntry.textEntry(block));
                    }
                }
            }
            if (entries.isEmpty()) {
                throw new IOException("No readable page text or captions found");
            }
            String title = metadata.optString("title", "").trim();
            String author = SavedSummaryStore.normalizeChannel(
                    metadata.optString("author", "")
            );
            if (!title.isEmpty()) {
                activeTranscriptTitle = title;
            }
            if (!author.isEmpty()) {
                activeTranscriptChannel = author;
            }
            deliverTranscript(requestId, entries);
        } catch (Exception error) {
            deliverTranscriptError(
                    requestId,
                    "No readable page text or accessible captions were found"
            );
        }
    }


    private void requestTranscript(
            String languageCode,
            YouTubeSubsDialog.TranscriptCallback callback
    ) {
        String pageUrl = webView.getUrl();
        if (activePageIsX()) {
            requestXPageTranscript(callback);
            return;
        }
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
                requestPageTranscript(callback);
            }
            return;
        }

        long requestId = transcriptRequestCounter.incrementAndGet();
        activeTranscriptRequestId = requestId;
        activeTranscriptCallback = callback;
        activeTranscriptDelivered = false;
        activeTranscriptTitle = "YouTube Video";
        activeTranscriptChannel = "";
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
        activeTranscriptChannel = "";
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
                activeTranscriptChannel = result.channelName;
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
            activeTranscriptChannel = SavedSummaryStore.normalizeChannel(
                    metadata.optString("channel", "")
            );
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
        if (activePageUsesTextSource()) {
            // Generic pages and X pages use bounded readable text or accessible
            // in-frame captions; there is no network caption-language picker.
            callback.onLoaded(new ArrayList<>());
            return;
        }
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
                callback.onLoaded(
                        entries,
                        activeTranscriptTitle,
                        activeTranscriptPageUrl,
                        activeTranscriptChannel
                );
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

    private void queryMegaFolderName(MegaBookmarksDialog.FolderNameCallback callback) {
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.megaFolderName() : ''";
        webView.evaluateJavascript(script, result -> {
            String name = "";
            try {
                Object value = new JSONTokener(result == null ? "\"\"" : result).nextValue();
                if (value instanceof String text && text.length() <= 120) {
                    name = text;
                }
            } catch (Exception ignored) {
                // The naming prompt retains its editable fallback.
            }
            callback.onName(name);
        });
    }

    private void queryCurrentTime(YouTubeSubsDialog.CurrentTimeCallback callback) {
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
                // Playback controls remain available when current time is unavailable.
            }
            callback.onTime(seconds);
        });
    }

    private void captureMegaBookmarkPosition(String url) {
        String expected = SupportedSite.megaPlaybackIdentity(url);
        if (expected == null || !expected.equals(
                SupportedSite.megaPlaybackIdentity(webView.getUrl())
        )) {
            return;
        }
        queryCurrentTime(seconds -> {
            if (!expected.equals(SupportedSite.megaPlaybackIdentity(webView.getUrl()))) {
                return;
            }
            try {
                megaBookmarkStore.updateResumePosition(expected, seconds);
            } catch (GeneralSecurityException ignored) {
                // Manual bookmark actions surface storage errors to the user.
            }
        });
    }

    private void captureActiveMegaBookmarkPosition() {
        megaBookmarkPositionHandler.removeCallbacks(megaBookmarkPositionTick);
        if (!activityResumed) {
            return;
        }
        captureMegaBookmarkPosition(webView.getUrl());
        megaBookmarkPositionHandler.postDelayed(megaBookmarkPositionTick, 10_000);
    }

    private void attemptPendingMegaResume() {
        megaResumeHandler.removeCallbacks(megaResumeTick);
        PendingMegaResume pending = pendingMegaResume;
        if (pending == null) {
            return;
        }
        String current = SupportedSite.megaPlaybackIdentity(webView.getUrl());
        if (!pending.url.equals(current)) {
            if (pending.attempts++ < 40
                    && SupportedSite.megaBookmarkIdentity(pending.url).equals(
                    SupportedSite.megaBookmarkIdentity(current)
            )) {
                megaResumeHandler.postDelayed(megaResumeTick, 500);
            } else {
                pendingMegaResume = null;
            }
            return;
        }
        pending.attempts++;
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.seekTo("
                + String.format(Locale.US, "%.3f", pending.positionSeconds)
                + ") : false";
        webView.evaluateJavascript(script, result -> {
            if (pendingMegaResume != pending) {
                return;
            }
            if ("true".equals(result)) {
                pendingMegaResume = null;
                Toast.makeText(
                        this,
                        MegaBookmarkStore.resumeLabel(pending.positionSeconds),
                        Toast.LENGTH_SHORT
                ).show();
            } else if (pending.attempts < 40) {
                megaResumeHandler.postDelayed(megaResumeTick, 500);
            } else {
                pendingMegaResume = null;
                Toast.makeText(
                        this,
                        "MEGA opened, but the saved video was not ready to resume",
                        Toast.LENGTH_LONG
                ).show();
            }
        });
    }

    private void startWatchPath(WatchPathPlan plan) {
        if (plan == null || !watchPathSourceMatches(plan.sourceUrl)) {
            Toast.makeText(this, "The video changed. Create a new WatchPath.", Toast.LENGTH_LONG).show();
            return;
        }
        stopWatchPath(false);
        activeWatchPath = new WatchPathPlayback(plan);
        watchPathControls.setVisibility(View.VISIBLE);
        updateWatchPathControls();
        applyWatchPathAction(activeWatchPath.start());
        scheduleWatchPathTick();
    }

    private void pollWatchPath() {
        watchPathHandler.removeCallbacks(watchPathTick);
        WatchPathPlayback playback = activeWatchPath;
        if (!watchPathForeground || playback == null || !playback.isActive()) {
            return;
        }
        if (!watchPathSourceMatches(playback.sourceUrl())) {
            stopWatchPath(false);
            return;
        }
        queryCurrentTime(seconds -> {
            if (!watchPathForeground
                    || activeWatchPath != playback
                    || !watchPathSourceMatches(playback.sourceUrl())) {
                if (activeWatchPath == playback) {
                    stopWatchPath(false);
                }
                return;
            }
            applyWatchPathAction(playback.onPlaybackTime(seconds));
            if (activeWatchPath == playback && playback.isActive()) {
                watchPathHandler.postDelayed(watchPathTick, 650);
            }
        });
    }

    private void scheduleWatchPathTick() {
        watchPathHandler.removeCallbacks(watchPathTick);
        if (watchPathForeground && activeWatchPath != null && activeWatchPath.isActive()) {
            watchPathHandler.postDelayed(watchPathTick, 650);
        }
    }

    private void applyWatchPathAction(WatchPathPlayback.Action action) {
        WatchPathPlayback playback = activeWatchPath;
        if (playback == null) {
            return;
        }
        if (!watchPathSourceMatches(playback.sourceUrl())) {
            stopWatchPath(false);
            return;
        }
        if (action.completed) {
            stopWatchPath(false);
            Toast.makeText(this, "WatchPath complete", Toast.LENGTH_SHORT).show();
            return;
        }
        if (action.shouldSeek()) {
            seekVideo(action.seekSeconds);
        }
        updateWatchPathControls();
    }

    private void updateWatchPathControls() {
        WatchPathPlayback playback = activeWatchPath;
        if (playback == null) {
            return;
        }
        String status = playback.isReviewingSkippedSection()
                ? "WatchPath • reviewing skipped section • resumes "
                        + playback.currentTitle()
                : "WatchPath "
                        + (playback.segmentIndex() + 1)
                        + "/"
                        + playback.segmentCount()
                        + " • "
                        + playback.currentTitle();
        watchPathStatus.setText(status);
        watchPathPreviousButton.setEnabled(true);
        watchPathNextButton.setEnabled(playback.canGoNext());
        watchPathUndoButton.setEnabled(playback.canUndo());
    }

    private void stopWatchPath(boolean completed) {
        watchPathHandler.removeCallbacks(watchPathTick);
        if (activeWatchPath != null) {
            activeWatchPath.stop();
        }
        activeWatchPath = null;
        if (watchPathControls != null) {
            watchPathControls.setVisibility(View.GONE);
        }
        if (completed) {
            Toast.makeText(this, "WatchPath complete", Toast.LENGTH_SHORT).show();
        }
    }

    private boolean watchPathSourceMatches(String expectedUrl) {
        String expected = SupportedSite.validatedHttpsUrl(expectedUrl);
        String current = SupportedSite.validatedHttpsUrl(
                activeMainFrameUrl.isEmpty() ? webView.getUrl() : activeMainFrameUrl
        );
        if (expected == null || current == null) {
            return false;
        }
        String expectedYouTube = YouTubeUrls.canonicalVideoUrl(expected);
        String currentYouTube = YouTubeUrls.canonicalVideoUrl(current);
        if (expectedYouTube != null || currentYouTube != null) {
            return expectedYouTube != null && expectedYouTube.equals(currentYouTube);
        }
        return withoutFragment(expected).equals(withoutFragment(current));
    }

    private static String withoutFragment(String value) {
        return Uri.parse(value).buildUpon().fragment(null).build().toString();
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
        screenLockShield.setClipChildren(false);
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
        pictureInPictureButton = makeIconButton(
                R.drawable.ic_picture_in_picture,
                "Picture-in-Picture unavailable. Drag to move",
                ignored -> enterPictureInPictureFromButton()
        );
        pictureInPictureButton.setAlpha(0.45f);
        omniButton = makeIconButton(
                R.drawable.ic_omni_control,
                "Omnibutton. Hold to move; triple-tap to lock",
                ignored -> {
                }
        );
        int buttonSize = dp(52);
        screenLockShield.addView(screenLockButton, new FrameLayout.LayoutParams(
                buttonSize,
                buttonSize,
                Gravity.TOP | Gravity.START
        ));
        screenLockShield.addView(pictureInPictureButton, new FrameLayout.LayoutParams(
                buttonSize,
                buttonSize,
                Gravity.TOP | Gravity.START
        ));
        screenLockShield.addView(omniButton, new FrameLayout.LayoutParams(
                buttonSize,
                buttonSize,
                Gravity.TOP | Gravity.START
        ));
        screenLockButton.setOnTouchListener(new FloatingDragListener(false));
        pictureInPictureButton.setOnTouchListener(new FloatingDragListener(true));
        omniButton.setOnTouchListener(new OmniButtonTouchListener());
        addContentView(screenLockShield, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));
        screenLockShield.setOnApplyWindowInsetsListener((view, insets) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Insets safeInsets = insets.getInsets(
                        WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()
                );
                screenLockInsetLeft = safeInsets.left;
                screenLockInsetTop = safeInsets.top;
                screenLockInsetRight = safeInsets.right;
                screenLockInsetBottom = safeInsets.bottom;
            } else {
                screenLockInsetLeft = insets.getSystemWindowInsetLeft();
                screenLockInsetTop = insets.getSystemWindowInsetTop();
                screenLockInsetRight = insets.getSystemWindowInsetRight();
                screenLockInsetBottom = insets.getSystemWindowInsetBottom();
            }
            positionFloatingControls();
            return insets;
        });
        applyScreenLockSettings();
        screenLockShield.requestApplyInsets();
    }

    private void applyScreenLockSettings() {
        if (screenLockShield == null) {
            return;
        }
        screenLockShield.setVisibility(View.VISIBLE);
        boolean omniEnabled = appSettings.isOmniButtonEnabled();
        if (!appSettings.isLockIconEnabled() && !omniEnabled) {
            setScreenLocked(false);
        }
        updateScreenLockButtonVisibility();
        pictureInPictureButton.setVisibility(shouldShowPictureInPictureButton()
                ? View.VISIBLE : View.GONE);
        omniButton.setVisibility(!screenLocked && omniEnabled ? View.VISIBLE : View.GONE);
        updateOmniButtonContentDescription();
        positionFloatingControls();
        screenLockShield.bringToFront();
    }

    private void updateScreenLockButtonVisibility() {
        boolean visible = screenLocked
                || (appSettings.isLockIconEnabled() && !appSettings.isOmniButtonEnabled());
        screenLockButton.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private boolean shouldShowPictureInPictureButton() {
        return !screenLocked
                && SpeedyWatchSettings.PIP_CONTROL_BUTTON.equals(
                        appSettings.getPictureInPictureControl()
                );
    }

    private void positionFloatingControls() {
        if (screenLockButton == null
                || pictureInPictureButton == null
                || omniButton == null) {
            return;
        }
        screenLockShield.post(() -> {
            if (screenLockShield.getWidth() == 0 || screenLockShield.getHeight() == 0) {
                return;
            }
            int safeGap = dp(4);
            int minimumX = screenLockInsetLeft + safeGap;
            int minimumY = screenLockInsetTop + safeGap;
            int maximumX = FloatingControlPosition.maximumCoordinate(
                    screenLockShield.getWidth(),
                    screenLockInsetRight,
                    omniButton.getWidth(),
                    safeGap,
                    minimumX
            );
            int maximumY = FloatingControlPosition.maximumCoordinate(
                    screenLockShield.getHeight(),
                    screenLockInsetBottom,
                    omniButton.getHeight(),
                    safeGap,
                    minimumY
            );
            int controlsHeight = speedControls == null ? 0 : speedControls.getHeight();
            int defaultLockX = maximumX;
            int defaultLockY = Math.max(minimumY, maximumY - controlsHeight - dp(8));
            positionFloatingControl(
                    screenLockButton,
                    appSettings.getLockPositionX(),
                    appSettings.getLockPositionY(),
                    minimumX,
                    minimumY,
                    maximumX,
                    maximumY,
                    defaultLockX,
                    defaultLockY
            );
            int defaultPipY = Math.max(
                    minimumY,
                    defaultLockY - (screenLockButton.getVisibility() == View.VISIBLE ? dp(60) : 0)
            );
            positionFloatingControl(
                    pictureInPictureButton,
                    appSettings.getPictureInPicturePositionX(),
                    appSettings.getPictureInPicturePositionY(),
                    minimumX,
                    minimumY,
                    maximumX,
                    maximumY,
                    maximumX,
                    defaultPipY
            );
            int defaultOmniY = minimumY
                    + Math.round((maximumY - minimumY) * 0.38f);
            positionFloatingControl(
                    omniButton,
                    appSettings.getOmniButtonPositionX(),
                    appSettings.getOmniButtonPositionY(),
                    minimumX,
                    minimumY,
                    maximumX,
                    maximumY,
                    maximumX,
                    defaultOmniY
            );
            updateScreenLockHoldDirection();
        });
    }

    private void positionFloatingControl(
            View control,
            float xFraction,
            float yFraction,
            int minimumX,
            int minimumY,
            int maximumX,
            int maximumY,
            int defaultX,
            int defaultY
    ) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) control.getLayoutParams();
        params.gravity = Gravity.TOP | Gravity.START;
        params.leftMargin = FloatingControlPosition.resolve(
                xFraction, minimumX, maximumX, defaultX
        );
        params.topMargin = FloatingControlPosition.resolve(
                yFraction, minimumY, maximumY, defaultY
        );
        params.rightMargin = 0;
        params.bottomMargin = 0;
        control.setLayoutParams(params);
    }

    private void persistFloatingControlPosition(View control, boolean pictureInPicture) {
        int safeGap = dp(4);
        int minimumX = screenLockInsetLeft + safeGap;
        int minimumY = screenLockInsetTop + safeGap;
        int maximumX = FloatingControlPosition.maximumCoordinate(
                screenLockShield.getWidth(),
                screenLockInsetRight,
                control.getWidth(),
                safeGap,
                minimumX
        );
        int maximumY = FloatingControlPosition.maximumCoordinate(
                screenLockShield.getHeight(),
                screenLockInsetBottom,
                control.getHeight(),
                safeGap,
                minimumY
        );
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) control.getLayoutParams();
        float x = FloatingControlPosition.fraction(params.leftMargin, minimumX, maximumX);
        float y = FloatingControlPosition.fraction(params.topMargin, minimumY, maximumY);
        if (control == omniButton) {
            appSettings.setOmniButtonPosition(x, y);
        } else if (pictureInPicture) {
            appSettings.setPictureInPicturePosition(x, y);
        } else {
            appSettings.setLockPosition(x, y);
        }
        updateScreenLockHoldDirection();
    }

    private void updateScreenLockHoldDirection() {
        if (screenLockButton == null || screenLockShield == null) {
            return;
        }
        FrameLayout.LayoutParams params =
                (FrameLayout.LayoutParams) screenLockButton.getLayoutParams();
        boolean moveRight = params.leftMargin + screenLockButton.getWidth() / 2
                < screenLockShield.getWidth() / 2;
        boolean moveDown = params.topMargin + screenLockButton.getHeight() / 2
                < screenLockShield.getHeight() / 2;
        screenLockButton.setHoldTranslation(
                dp(moveRight ? 18 : -18),
                dp(moveDown ? 18 : -18)
        );
    }

    private void moveFloatingControl(
            View view,
            int startLeft,
            int startTop,
            float deltaX,
            float deltaY
    ) {
        int safeGap = dp(4);
        int minimumX = screenLockInsetLeft + safeGap;
        int minimumY = screenLockInsetTop + safeGap;
        int maximumX = FloatingControlPosition.maximumCoordinate(
                screenLockShield.getWidth(),
                screenLockInsetRight,
                view.getWidth(),
                safeGap,
                minimumX
        );
        int maximumY = FloatingControlPosition.maximumCoordinate(
                screenLockShield.getHeight(),
                screenLockInsetBottom,
                view.getHeight(),
                safeGap,
                minimumY
        );
        FrameLayout.LayoutParams moveParams =
                (FrameLayout.LayoutParams) view.getLayoutParams();
        moveParams.leftMargin = FloatingControlPosition.clamp(
                Math.round(startLeft + deltaX), minimumX, maximumX
        );
        moveParams.topMargin = FloatingControlPosition.clamp(
                Math.round(startTop + deltaY), minimumY, maximumY
        );
        view.setLayoutParams(moveParams);
        updateScreenLockHoldDirection();
    }
    private void animateOmniButtonEmphasis(View view, float scale) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        int safeGap = dp(4);
        int minimumX = screenLockInsetLeft + safeGap;
        int minimumY = screenLockInsetTop + safeGap;
        int maximumX = FloatingControlPosition.maximumCoordinate(
                screenLockShield.getWidth(),
                screenLockInsetRight,
                view.getWidth(),
                safeGap,
                minimumX
        );
        int maximumY = FloatingControlPosition.maximumCoordinate(
                screenLockShield.getHeight(),
                screenLockInsetBottom,
                view.getHeight(),
                safeGap,
                minimumY
        );
        float translationX = FloatingControlPosition.expansionTranslation(
                params.leftMargin,
                view.getWidth(),
                scale,
                minimumX,
                maximumX
        );
        float translationY = FloatingControlPosition.expansionTranslation(
                params.topMargin,
                view.getHeight(),
                scale,
                minimumY,
                maximumY
        );
        view.animate().cancel();
        view.animate()
                .scaleX(scale)
                .scaleY(scale)
                .translationX(translationX)
                .translationY(translationY)
                .setDuration(OMNI_EMPHASIS_MILLIS)
                .start();
    }

    private void updateOmniButtonDragTranslation(View view) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) view.getLayoutParams();
        int safeGap = dp(4);
        int minimumX = screenLockInsetLeft + safeGap;
        int minimumY = screenLockInsetTop + safeGap;
        int maximumX = FloatingControlPosition.maximumCoordinate(
                screenLockShield.getWidth(),
                screenLockInsetRight,
                view.getWidth(),
                safeGap,
                minimumX
        );
        int maximumY = FloatingControlPosition.maximumCoordinate(
                screenLockShield.getHeight(),
                screenLockInsetBottom,
                view.getHeight(),
                safeGap,
                minimumY
        );
        view.setTranslationX(FloatingControlPosition.expansionTranslation(
                params.leftMargin,
                view.getWidth(),
                OMNI_DRAG_READY_SCALE,
                minimumX,
                maximumX
        ));
        view.setTranslationY(FloatingControlPosition.expansionTranslation(
                params.topMargin,
                view.getHeight(),
                OMNI_DRAG_READY_SCALE,
                minimumY,
                maximumY
        ));
    }


    private final class FloatingDragListener implements View.OnTouchListener {
        private final boolean pictureInPicture;
        private final int touchSlop = ViewConfiguration.get(MainActivity.this)
                .getScaledTouchSlop();
        private float downRawX;
        private float downRawY;
        private int startLeft;
        private int startTop;
        private boolean dragging;

        FloatingDragListener(boolean pictureInPicture) {
            this.pictureInPicture = pictureInPicture;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (screenLocked) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    FrameLayout.LayoutParams downParams =
                            (FrameLayout.LayoutParams) view.getLayoutParams();
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startLeft = downParams.leftMargin;
                    startTop = downParams.topMargin;
                    dragging = false;
                    return false;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - downRawX;
                    float deltaY = event.getRawY() - downRawY;
                    if (!dragging && Math.hypot(deltaX, deltaY) >= touchSlop) {
                        dragging = true;
                        view.setPressed(false);
                    }
                    if (!dragging) {
                        return false;
                    }
                    moveFloatingControl(view, startLeft, startTop, deltaX, deltaY);
                    return true;
                case MotionEvent.ACTION_UP:
                    if (!dragging) {
                        return false;
                    }
                    view.setPressed(false);
                    persistFloatingControlPosition(view, pictureInPicture);
                    dragging = false;
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    if (!dragging) {
                        return false;
                    }
                    view.setPressed(false);
                    dragging = false;
                    positionFloatingControls();
                    return true;
                default:
                    return dragging;
            }
        }
    }

    private final class OmniButtonTouchListener implements View.OnTouchListener {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final int touchSlop = ViewConfiguration.get(MainActivity.this)
                .getScaledTouchSlop();
        private final int swipeDistance = dp(16);
        private float downRawX;
        private float downRawY;
        private int startLeft;
        private int startTop;
        private boolean pointerDown;
        private boolean movedBeforeLongPress;
        private boolean dragging;
        private View activeView;
        private int tapCount;
        private long previousTapAt = -1L;
        private final Runnable beginDrag = () -> {
            if (!pointerDown || movedBeforeLongPress || activeView == null) {
                return;
            }
            dragging = true;
            activeView.setPressed(false);
            activeView.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
            animateOmniButtonEmphasis(activeView, OMNI_DRAG_READY_SCALE);
        };

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            if (screenLocked) {
                return false;
            }
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    FrameLayout.LayoutParams params =
                            (FrameLayout.LayoutParams) view.getLayoutParams();
                    downRawX = event.getRawX();
                    downRawY = event.getRawY();
                    startLeft = params.leftMargin;
                    startTop = params.topMargin;
                    pointerDown = true;
                    movedBeforeLongPress = false;
                    dragging = false;
                    activeView = view;
                    animateOmniButtonEmphasis(view, OMNI_PRESS_SCALE);
                    handler.postDelayed(beginDrag, OMNI_DRAG_HOLD_MILLIS);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - downRawX;
                    float deltaY = event.getRawY() - downRawY;
                    if (!dragging
                            && !movedBeforeLongPress
                            && Math.hypot(deltaX, deltaY) >= touchSlop) {
                        movedBeforeLongPress = true;
                        handler.removeCallbacks(beginDrag);
                        animateOmniButtonEmphasis(view, 1f);
                    }
                    if (dragging) {
                        moveFloatingControl(view, startLeft, startTop, deltaX, deltaY);
                        updateOmniButtonDragTranslation(view);
                    }
                    return true;
                case MotionEvent.ACTION_UP:
                    pointerDown = false;
                    handler.removeCallbacks(beginDrag);
                    animateOmniButtonEmphasis(view, 1f);
                    if (dragging) {
                        dragging = false;
                        activeView = null;
                        persistFloatingControlPosition(view, false);
                        return true;
                    }
                    activeView = null;
                    OmniButtonGesture.Direction direction = OmniButtonGesture.direction(
                            event.getRawX() - downRawX,
                            event.getRawY() - downRawY,
                            swipeDistance
                    );
                    if (direction != OmniButtonGesture.Direction.NONE) {
                        tapCount = 0;
                        previousTapAt = -1L;
                        performOmniButtonAction(direction);
                        return true;
                    }
                    long tapAt = SystemClock.uptimeMillis();
                    tapCount = OmniButtonGesture.nextTapCount(
                            tapCount,
                            previousTapAt,
                            tapAt,
                            ViewConfiguration.getDoubleTapTimeout()
                    );
                    previousTapAt = tapAt;
                    if (tapCount == 3) {
                        tapCount = 0;
                        previousTapAt = -1L;
                        setScreenLocked(true);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    pointerDown = false;
                    dragging = false;
                    activeView = null;
                    handler.removeCallbacks(beginDrag);
                    animateOmniButtonEmphasis(view, 1f);
                    positionFloatingControls();
                    return true;
                default:
                    return true;
            }
        }
    }

    private SupportedSite omniButtonContext() {
        String url = webView == null ? null : webView.getUrl();
        SupportedSite site = SupportedSite.forUrl(url);
        if (site == SupportedSite.YOUTUBE || site == SupportedSite.X) {
            return site;
        }
        return SupportedSite.WEB;
    }

    private void performOmniButtonAction(OmniButtonGesture.Direction direction) {
        SupportedSite context = omniButtonContext();
        OmniButtonAction action = context == SupportedSite.X
                ? appSettings.getOmniXButtonAction(direction)
                : context == SupportedSite.YOUTUBE
                        ? appSettings.getOmniButtonAction(direction)
                        : appSettings.getOmniWebButtonAction(direction);
        double amount = context == SupportedSite.X
                ? appSettings.getOmniXButtonAmount(direction)
                : context == SupportedSite.YOUTUBE
                        ? appSettings.getOmniButtonAmount(direction)
                        : appSettings.getOmniWebButtonAmount(direction);
        switch (action) {
            case NONE:
                Toast.makeText(
                        this,
                        "No action assigned to " + direction.label,
                        Toast.LENGTH_SHORT
                ).show();
                break;
            case PREVIOUS_CHAPTER:
                navigateChapter(false);
                break;
            case NEXT_CHAPTER:
                navigateChapter(true);
                break;
            case BROWSER_BACK:
                if (webView.canGoBack()) {
                    webView.goBack();
                } else {
                    Toast.makeText(this, "No previous browser page", Toast.LENGTH_SHORT).show();
                }
                break;
            case BROWSER_FORWARD:
                if (webView.canGoForward()) {
                    webView.goForward();
                } else {
                    Toast.makeText(this, "No forward browser page", Toast.LENGTH_SHORT).show();
                }
                break;
            case YOUTUBE_HISTORY:
                loadBrowsableUrl(YOUTUBE_HISTORY_URL);
                break;
            case WATCH_LATER:
                loadBrowsableUrl(YOUTUBE_WATCH_LATER_URL);
                break;
            case SEARCH:
                showSiteSearch();
                break;
            case CHOOSE_SITE:
                showSitePicker();
                break;
            case RELOAD:
                webView.reload();
                break;
            case SITE_HOME:
                openSelectedSiteHome();
                break;
            case SPEED_UP:
                setSpeed(selectedSpeed + amount);
                break;
            case SPEED_DOWN:
                setSpeed(selectedSpeed - amount);
                break;
            case SPEED_0_5:
            case SPEED_0_8:
            case SPEED_1:
            case SPEED_1_5:
            case SPEED_2:
            case SPEED_2_5:
            case SPEED_3:
            case SPEED_4:
                setSpeed(action.exactSpeed);
                break;
            case SEEK_FORWARD:
                seekRelative(amount);
                break;
            case SEEK_BACKWARD:
                seekRelative(-amount);
                break;
            case PLAY_PAUSE:
                toggleMediaPlayback();
                break;
            case PICTURE_IN_PICTURE:
                enterPictureInPictureFromButton();
                break;
            case TOGGLE_SPEED_BAR:
                applySpeedControlsCollapsed(
                        speedControlsContent.getVisibility() == View.VISIBLE,
                        true
                );
                break;
            case SHARE:
                shareCurrentPage();
                break;
            case VIDEO_SUBS:
                showYouTubeSubs();
                break;
            case SUMMARY_ONE:
                showYouTubeSubs(YouTubeSubsDialog.InitialSummary.ONE);
                break;
            case SUMMARY_TWO:
                showYouTubeSubs(YouTubeSubsDialog.InitialSummary.TWO);
                break;
            case QUIZ:
                showQuiz();
                break;
            case DOWNLOAD:
                showDownload();
                break;
            case SAVED:
                showSavedSummaries();
                break;
            case SETTINGS:
                showSettings();
                break;
            case LOCK_SCREEN:
                setScreenLocked(true);
                break;
            case SAVE_LINKS:
                saveLinksManually();
                break;
            default:
                break;
        }
    }

    private void updateOmniButtonContentDescription() {
        if (omniButton == null) {
            return;
        }
        SupportedSite context = omniButtonContext();
        StringBuilder description = new StringBuilder(
                "Omnibutton, " + context.label + " actions. ");
        for (OmniButtonGesture.Direction direction
                : OmniButtonGesture.Direction.configurableValues()) {
            OmniButtonAction action = context == SupportedSite.X
                    ? appSettings.getOmniXButtonAction(direction)
                    : context == SupportedSite.YOUTUBE
                            ? appSettings.getOmniButtonAction(direction)
                            : appSettings.getOmniWebButtonAction(direction);
            description.append(direction.label).append(": ");
            if (!action.usesAmount()) {
                description.append(action.label);
            } else {
                double amount = context == SupportedSite.X
                        ? appSettings.getOmniXButtonAmount(direction)
                        : context == SupportedSite.YOUTUBE
                                ? appSettings.getOmniButtonAmount(direction)
                                : appSettings.getOmniWebButtonAmount(direction);
                description.append(action.label(amount));
            }
            description.append(". ");
        }
        description.append("Hold to move; triple-tap to lock");
        omniButton.setContentDescription(description);
    }

    private void openSelectedSiteHome() {
        if (selectedSite == SupportedSite.MEGA) {
            showMegaBookmarks();
            return;
        }
        if (selectedSite.homeUrl == null) {
            Toast.makeText(this, "This site has no home page", Toast.LENGTH_SHORT).show();
            return;
        }
        webView.loadUrl(selectedSite.homeUrl);
    }

    private String mediaUnavailableMessage(String action) {
        if (!frameControllerEnabled) {
            return action + " is unavailable for embedded players in this WebView";
        }
        return "No controllable video or audio found on this page";
    }

    private void seekRelative(double seconds) {
        queryCurrentTime(currentTime -> {
            if (!Double.isFinite(currentTime)) {
                Toast.makeText(
                        this,
                        mediaUnavailableMessage("seeking"),
                        Toast.LENGTH_SHORT
                ).show();
                return;
            }
            seekVideo(currentTime + seconds);
            String direction = seconds >= 0 ? "Fast-forwarded " : "Rewound ";
            Toast.makeText(
                    this,
                    direction + formatSpeedValue(Math.abs(seconds)) + " seconds",
                    Toast.LENGTH_SHORT
            ).show();
        });
    }

    private void toggleMediaPlayback() {
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.togglePlayback() : 'unavailable'";
        webView.evaluateJavascript(script, result -> {
            String state = "unavailable";
            try {
                Object value = new JSONTokener(
                        result == null ? "\"unavailable\"" : result
                ).nextValue();
                if (value instanceof String text) {
                    state = text;
                }
            } catch (Exception ignored) {
                // The unavailable message below remains authoritative.
            }
            if ("unavailable".equals(state)) {
                Toast.makeText(this, mediaUnavailableMessage("Play/Pause"), Toast.LENGTH_SHORT)
                        .show();
            } else {
                Toast.makeText(
                        this,
                        "playing".equals(state) ? "Playback started" : "Playback paused",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }

    private void shareCurrentPage() {
        String pageUrl = SupportedSite.validatedHttpsUrl(webView.getUrl());
        if (!SupportedSite.isShareablePageUrl(pageUrl)) {
            String message = SupportedSite.forUrl(pageUrl) == SupportedSite.MEGA
                    ? "MEGA shared-link keys stay private inside SpeedyWatch"
                    : "Open a public HTTPS page before sharing";
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            return;
        }
        String title = webView.getTitle();
        if (title == null || title.trim().isEmpty()) {
            title = URI.create(pageUrl).getHost();
        }
        Intent share = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, title)
                .putExtra(Intent.EXTRA_TEXT, title + "\n\n" + pageUrl);
        try {
            startActivity(Intent.createChooser(share, "Share with"));
        } catch (RuntimeException error) {
            Toast.makeText(this, "No app can share this page", Toast.LENGTH_SHORT).show();
        }
    }


    private void setScreenLocked(boolean locked) {
        screenLocked = locked;
        screenLockShield.setClickable(locked);
        screenLockButton.setLocked(locked);
        updateScreenLockButtonVisibility();
        pictureInPictureButton.setVisibility(shouldShowPictureInPictureButton()
                ? View.VISIBLE : View.GONE);
        omniButton.setVisibility(!locked && appSettings.isOmniButtonEnabled()
                ? View.VISIBLE : View.GONE);
        positionFloatingControls();
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
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
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

    private void registerPictureInPictureReceiver() {
        IntentFilter filter = new IntentFilter(ACTION_TOGGLE_PICTURE_IN_PICTURE_PLAYBACK);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(pictureInPictureReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(pictureInPictureReceiver, filter);
        }
        pictureInPictureReceiverRegistered = true;
    }

    private void pollPictureInPictureState() {
        pictureInPictureHandler.removeCallbacks(pictureInPictureTick);
        if (!activityResumed || isInPictureInPictureMode()) {
            return;
        }
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.pictureInPictureState() : null";
        webView.evaluateJavascript(script, result -> {
            boolean active = false;
            Rect sourceRect = new Rect();
            try {
                JSONObject state = new JSONObject(result == null ? "{}" : result);
                active = state.optBoolean("playing", false);
                sourceRect = pictureInPictureSourceRect();
                updateActiveVideoBounds(state);
            } catch (Exception ignored) {
                activeVideoBounds.setEmpty();
                // A page without the current controller is not eligible for PiP.
            }
            pictureInPicturePlaybackActive = active;
            pictureInPictureSourceRect = sourceRect;
            pictureInPictureButton.setAlpha(active ? 1f : 0.45f);
            pictureInPictureButton.setContentDescription(active
                    ? "Enter Picture-in-Picture. Drag to move"
                    : "Picture-in-Picture unavailable. Drag to move");
            updatePictureInPictureParams(active, active);
            if (activityResumed) {
                pictureInPictureHandler.postDelayed(pictureInPictureTick, 750);
            }
        });
    }

    private void updateActiveVideoBounds(JSONObject state) {
        double viewportWidth = state.optDouble("viewportWidth", Double.NaN);
        double viewportHeight = state.optDouble("viewportHeight", Double.NaN);
        if (!state.optBoolean("video", false)
                || !Double.isFinite(viewportWidth)
                || !Double.isFinite(viewportHeight)
                || viewportWidth <= 0
                || viewportHeight <= 0
                || webView.getWidth() <= 0
                || webView.getHeight() <= 0) {
            activeVideoBounds.setEmpty();
            return;
        }
        float scaleX = (float) (webView.getWidth() / viewportWidth);
        float scaleY = (float) (webView.getHeight() / viewportHeight);
        float left = (float) state.optDouble("left", 0) * scaleX;
        float top = (float) state.optDouble("top", 0) * scaleY;
        float right = (float) state.optDouble("right", 0) * scaleX;
        float bottom = (float) state.optDouble("bottom", 0) * scaleY;
        left = Math.max(0, Math.min(webView.getWidth(), left));
        top = Math.max(0, Math.min(webView.getHeight(), top));
        right = Math.max(0, Math.min(webView.getWidth(), right));
        bottom = Math.max(0, Math.min(webView.getHeight(), bottom));
        if (right <= left || bottom <= top) {
            activeVideoBounds.setEmpty();
            return;
        }
        activeVideoBounds.set(left, top, right, bottom);
    }
    private Rect pictureInPictureSourceRect() {
        Rect webBounds = new Rect();
        if (!webView.getGlobalVisibleRect(webBounds) || webBounds.isEmpty()) {
            return new Rect();
        }
        int size = Math.min(webBounds.width(), webBounds.height());
        int left = webBounds.centerX() - size / 2;
        int top = webBounds.centerY() - size / 2;
        return new Rect(left, top, left + size, top + size);
    }


    private RemoteAction pictureInPicturePlaybackAction(boolean playing) {
        Intent intent = new Intent(ACTION_TOGGLE_PICTURE_IN_PICTURE_PLAYBACK)
                .setPackage(getPackageName());
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String label = playing ? "Pause" : "Play";
        return new RemoteAction(
                Icon.createWithResource(
                        this,
                        playing
                                ? android.R.drawable.ic_media_pause
                                : android.R.drawable.ic_media_play
                ),
                label,
                label,
                pendingIntent
        );
    }

    private void updatePictureInPictureParams(boolean active, boolean playing) {
        if (active == pictureInPictureParamsActive
                && playing == pictureInPictureParamsPlaying
                && pictureInPictureSourceRect.equals(pictureInPictureParamsSourceRect)) {
            return;
        }
        pictureInPictureParamsActive = active;
        pictureInPictureParamsPlaying = playing;
        pictureInPictureParamsSourceRect = new Rect(pictureInPictureSourceRect);
        PictureInPictureParams.Builder builder = new PictureInPictureParams.Builder()
                .setAspectRatio(new Rational(1, 1))
                .setActions(active
                        ? Collections.singletonList(pictureInPicturePlaybackAction(playing))
                        : Collections.emptyList());
        if (!pictureInPictureParamsSourceRect.isEmpty()) {
            builder.setSourceRectHint(pictureInPictureParamsSourceRect);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(false);
            builder.setSeamlessResizeEnabled(false);
        }
        pictureInPictureParams = builder.build();
        setPictureInPictureParams(pictureInPictureParams);
    }

    private void setPictureInPictureUi(boolean enabled) {
        setPictureInPictureUi(enabled, null);
    }

    private void setPictureInPictureUi(boolean enabled, Runnable ready) {
        if (enabled) {
            if (!pictureInPictureUiPrepared) {
                watchPathVisibleBeforePictureInPicture =
                        watchPathControls.getVisibility() == View.VISIBLE;
            }
            pictureInPictureUiPrepared = true;
            screenLockShield.setVisibility(View.GONE);
            navigationControls.setVisibility(View.GONE);
            watchPathControls.setVisibility(View.GONE);
            speedControls.setVisibility(View.GONE);
            webView.setVisibility(View.VISIBLE);
        } else if (pictureInPictureUiPrepared) {
            pictureInPictureUiPrepared = false;
            webView.setVisibility(View.VISIBLE);
            navigationControls.setVisibility(View.VISIBLE);
            watchPathControls.setVisibility(
                    watchPathVisibleBeforePictureInPicture ? View.VISIBLE : View.GONE
            );
            speedControls.setVisibility(View.VISIBLE);
            appRoot.requestApplyInsets();
            applyScreenLockSettings();
        }
        appRoot.requestLayout();
        if (ready != null) {
            appRoot.post(ready);
        }
    }

    private void togglePictureInPicturePlayback() {
        pictureInPicturePlaybackRequested = !pictureInPicturePlaybackRequested;
        setPictureInPicturePlayback(pictureInPicturePlaybackRequested);
        updatePictureInPictureParams(true, pictureInPicturePlaybackRequested);
    }
    private void enterPictureInPictureFromButton() {
        if (!pictureInPicturePlaybackActive) {
            Toast.makeText(this, "Start playback first", Toast.LENGTH_SHORT).show();
            return;
        }
        pictureInPictureHandler.removeCallbacks(pictureInPictureTransitionTimeout);
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.preparePictureInPicture() "
                + ": \"unavailable\"";
        webView.evaluateJavascript(script, result -> {
            String mode = "unavailable";
            try {
                Object value = new JSONTokener(result == null ? "null" : result).nextValue();
                if (value instanceof String stringValue) {
                    mode = stringValue;
                }
            } catch (Exception ignored) {
                // Treat malformed controller output as unavailable.
            }
            if ("audio".equals(mode)) {
                pictureInPictureEntryPending = true;
                pictureInPicturePlaybackRequested = true;
                setPictureInPictureActive(true);
                setPictureInPictureUi(true, this::enterPendingPictureInPicture);
            } else {
                Toast.makeText(
                        this,
                        "This player cannot enter Picture-in-Picture",
                        Toast.LENGTH_SHORT
                ).show();
            }
        });
    }



    private void enterPendingPictureInPicture() {
        if (!pictureInPictureEntryPending || isInPictureInPictureMode()) {
            return;
        }
        boolean entered;
        try {
            entered = enterPictureInPictureMode(pictureInPictureParams);
        } catch (IllegalStateException ignored) {
            entered = false;
        }
        if (!entered) {
            restoreAfterFailedPictureInPictureEntry(!activityResumed);
        }
    }

    private void restoreAfterFailedPictureInPictureEntry(boolean pauseWebView) {
        pictureInPictureEntryPending = false;
        pictureInPicturePlaybackRequested = false;
        setPictureInPictureActive(false);
        pictureInPictureHandler.removeCallbacks(pictureInPictureResumePlayback);
        setPictureInPictureUi(false);
        if (pauseWebView) {
            webView.onPause();
        }
    }

    @Override
    public void onUserLeaveHint() {
        super.onUserLeaveHint();
    }

    private void setPictureInPicturePlayback(boolean playing) {
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.setPictureInPicturePlayback("
                + playing + ") : false";
        webView.evaluateJavascript(script, ignored -> {});
    }

    private void setPictureInPictureActive(boolean active) {
        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.setPictureInPictureActive("
                + active + ") : false";
        webView.evaluateJavascript(script, ignored -> {});
    }

    private void resumePictureInPicturePlayback() {
        if (!isInPictureInPictureMode() || !pictureInPicturePlaybackRequested) {
            return;
        }
        setPictureInPicturePlayback(true);
    }

    private void finishPictureInPictureTransition() {
        if (!activityResumed
                && pictureInPictureEntryPending
                && !isInPictureInPictureMode()) {
            restoreAfterFailedPictureInPictureEntry(true);
        }
    }

    @Override
    public void onPictureInPictureModeChanged(
            boolean isInPictureInPictureMode,
            Configuration newConfig
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig);
        pictureInPictureEntryPending = false;
        pictureInPictureHandler.removeCallbacks(pictureInPictureTransitionTimeout);
        pictureInPictureHandler.removeCallbacks(pictureInPictureResumePlayback);
        pictureInPicturePlaybackRequested = isInPictureInPictureMode;
        setPictureInPictureActive(isInPictureInPictureMode);
        setPictureInPictureUi(isInPictureInPictureMode);
        if (isInPictureInPictureMode) {
            webView.onResume();
            updatePictureInPictureParams(true, true);
            pictureInPictureHandler.postDelayed(
                    pictureInPictureResumePlayback,
                    300
            );
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
    public void onConfigurationChanged(Configuration configuration) {
        super.onConfigurationChanged(configuration);
        positionFloatingControls();
    }

    private static final class PendingMegaResume {
        final String url;
        final double positionSeconds;
        int attempts;

        PendingMegaResume(String url, double positionSeconds) {
            this.url = url;
            this.positionSeconds = Math.max(0, positionSeconds);
        }
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
        activityResumed = false;
        pictureInPictureHandler.removeCallbacks(pictureInPictureTick);
        chapterHandler.removeCallbacks(chapterRefreshTick);
        watchPathForeground = false;
        captureMegaBookmarkPosition(webView.getUrl());
        megaBookmarkPositionHandler.removeCallbacks(megaBookmarkPositionTick);
        watchPathHandler.removeCallbacks(watchPathTick);
        GitHubUpdateChecker.unregisterResumedActivity(this);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        // Keep the renderer active across the dedicated PiP transition and while
        // playback is owned by the floating window.
        if (!isInPictureInPictureMode()
                && !pictureInPictureEntryPending
                && !pictureInPictureUiPrepared) {
            webView.onPause();
        } else if (pictureInPictureEntryPending) {
            pictureInPictureHandler.postDelayed(
                    pictureInPictureTransitionTimeout,
                    1000
            );
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        activityResumed = true;
        pictureInPictureEntryPending = false;
        pictureInPicturePlaybackRequested = false;
        setPictureInPictureActive(false);
        pictureInPictureHandler.removeCallbacks(pictureInPictureTransitionTimeout);
        pictureInPictureHandler.removeCallbacks(pictureInPictureResumePlayback);
        if (!isInPictureInPictureMode()) {
            setPictureInPictureUi(false);
        }
        watchPathForeground = true;
        webView.onResume();
        GitHubUpdateChecker.registerResumedActivity(this);
        GitHubUpdateChecker.resumePendingInstaller(this);
        scheduleWatchPathTick();
        pictureInPictureHandler.post(pictureInPictureTick);
        megaBookmarkPositionHandler.post(megaBookmarkPositionTick);
        scheduleChapterRefresh();
    }

    @Override
    protected void onDestroy() {
        hideFullscreenView();
        stopWatchPath(false);
        pictureInPictureHandler.removeCallbacks(pictureInPictureTransitionTimeout);
        pictureInPictureHandler.removeCallbacks(pictureInPictureResumePlayback);
        megaResumeHandler.removeCallbacks(megaResumeTick);
        megaBookmarkPositionHandler.removeCallbacks(megaBookmarkPositionTick);
        ioExecutor.shutdownNow();
        pictureInPictureHandler.removeCallbacks(pictureInPictureTick);
        chapterHandler.removeCallbacks(chapterRefreshTick);
        if (pictureInPictureReceiverRegistered) {
            unregisterReceiver(pictureInPictureReceiver);
            pictureInPictureReceiverRegistered = false;
        }
        savedSummaryStore.close();
        webView.loadUrl("about:blank");
        webView.removeAllViews();
        webView.destroy();
        super.onDestroy();
    }
}
