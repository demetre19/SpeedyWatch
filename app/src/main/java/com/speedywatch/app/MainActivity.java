package com.speedywatch.app;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.Context;
import android.content.pm.ActivityInfo;
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
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

public final class MainActivity extends Activity {
    private static final String HOME_URL = "https://www.youtube.com/";
    private static final int BACKGROUND = Color.rgb(15, 15, 15);
    private static final int PANEL = Color.rgb(30, 30, 30);
    private static final int BUTTON = Color.rgb(48, 48, 48);
    private static final int ACTIVE = Color.rgb(255, 0, 51);

    private WebView webView;
    private TextView statusText;
    private Button adSkippingButton;
    private String controllerScript;
    private double selectedSpeed = 1.0;
    private boolean adSkipping = true;
    private final Map<Double, Button> speedButtons = new LinkedHashMap<>();
    private final OpenRouterClient openRouterClient = new OpenRouterClient();
    private final ExecutorService ioExecutor = Executors.newFixedThreadPool(2);
    private final AtomicLong transcriptRequestCounter = new AtomicLong();
    private SpeedyWatchSettings appSettings;
    private SavedSummaryStore savedSummaryStore;
    private LinearLayout appRoot;
    private EditText customSpeedInput;
    private View fullscreenView;
    private WebChromeClient.CustomViewCallback fullscreenCallback;
    private volatile YouTubeSubsDialog.TranscriptCallback activeTranscriptCallback;
    private volatile long activeTranscriptRequestId;
    private volatile boolean activeTranscriptDelivered;
    private volatile String activeTranscriptTitle = "YouTube Video";
    private volatile String activeTranscriptPageUrl = "";
    private volatile String activeVideoId = "";
    private volatile String lastCaptionRequestUrl = "";

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        appSettings = new SpeedyWatchSettings(this);
        savedSummaryStore = new SavedSummaryStore(this);
        controllerScript = readAsset("speedywatch.js");
        appRoot = buildUi();
        setContentView(appRoot);
        applySystemBarInsets(appRoot);

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
            public void onPageFinished(WebView view, String url) {
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
                    Toast.makeText(MainActivity.this, "YouTube could not be loaded", Toast.LENGTH_SHORT).show();
                }
            }
        });

        if (savedInstanceState == null || webView.restoreState(savedInstanceState) == null) {
            webView.loadUrl(HOME_URL);
        }
    }

    private LinearLayout buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BACKGROUND);

        LinearLayout navigation = horizontalRow();
        navigation.addView(makeIconButton(
                R.drawable.ic_home,
                "YouTube home",
                ignored -> webView.loadUrl(HOME_URL)
        ));
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
                "YouTube subtitles",
                ignored -> showYouTubeSubs()
        ));
        navigation.addView(makeIconButton(
                R.drawable.ic_quiz,
                "Create video quiz",
                ignored -> showQuiz()
        ));
        navigation.addView(makeIconButton(
                R.drawable.ic_bookmark,
                "Saved summaries",
                ignored -> showSavedSummaries()
        ));
        navigation.addView(makeIconButton(
                R.drawable.ic_settings,
                "Settings",
                ignored -> showSettings()
        ));
        root.addView(navigation, new LinearLayout.LayoutParams(
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

        LinearLayout controls = new LinearLayout(this);
        controls.setOrientation(LinearLayout.VERTICAL);
        controls.setPadding(dp(8), dp(6), dp(8), dp(8));
        controls.setBackgroundColor(PANEL);

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
        controls.addView(scrollingRow(presets));

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
        adSkippingButton = makeButton("Skip ads: ON", ignored -> toggleAdSkipping());
        setButtonBackground(adSkippingButton, ACTIVE, ACTIVE, 0);
        actions.addView(adSkippingButton);
        controls.addView(actions);


        root.addView(controls, new LinearLayout.LayoutParams(
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
        button.setLayoutParams(params);
        button.setOnClickListener(listener);
        return button;
    }


    private void setButtonBackground(Button button, int fill, int stroke, int strokeWidthDp) {
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

    private void toggleAdSkipping() {
        adSkipping = !adSkipping;
        adSkippingButton.setText(adSkipping ? "Skip ads: ON" : "Skip ads: OFF");
        setButtonBackground(adSkippingButton, adSkipping ? ACTIVE : BUTTON, adSkipping ? ACTIVE : BUTTON, 0);
        applyControllerState();
    }

    private void injectController() {
        webView.evaluateJavascript(controllerScript, ignored -> applyControllerState());
    }

    private void applyControllerState() {
        statusText.setText(formatRate(selectedSpeed) + " | applying");
        String script = "(() => { const c = window.__speedyWatchController; "
                + "if (!c) return 'missing'; "
                + "c.setSpeed(" + String.format(Locale.US, "%.2f", selectedSpeed) + "); "
                + "c.setAdSkipping(" + adSkipping + "); "
                + "const media = document.querySelector('video, audio'); "
                + "return media ? 'media:' + media.playbackRate.toFixed(2) : 'ready'; })();";
        String liveResult = "\"media:" + String.format(Locale.US, "%.2f", selectedSpeed) + "\"";
        webView.evaluateJavascript(script, result -> {
            boolean ready = result != null && !result.equals("\"missing\"") && !result.equals("null");
            String label = result != null && result.equals(liveResult)
                    ? formatRate(selectedSpeed) + " live | ads " + (adSkipping ? "blocked" : "allowed")
                    : statusLabel();
            statusText.setText(ready ? label : formatRate(selectedSpeed) + " | loading");
        });
    }

    private String statusLabel() {
        return formatRate(selectedSpeed) + " | ads " + (adSkipping ? "blocked" : "allowed");
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
            return true;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return false;
        }
        String host = uri.getHost();
        if (host == null) {
            return false;
        }
        host = host.toLowerCase(Locale.US);
        return host.equals("youtube.com")
                || host.endsWith(".youtube.com")
                || host.equals("youtu.be")
                || host.equals("accounts.google.com")
                || host.equals("consent.google.com");
    }

    private void showSavedSummaries() {
        new SavedSummariesDialog(this, savedSummaryStore, url -> {
            if (SavedSummaryStore.isSupportedSourceUrl(url)) {
                webView.loadUrl(url);
            }
        }).show();
    }

    private void showSettings() {
        new SettingsDialog(this, appSettings, openRouterClient, ioExecutor).show();
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
                ioExecutor
        ).show();
    }

    private YouTubeSubsDialog.TranscriptHost transcriptHost() {
        return new YouTubeSubsDialog.TranscriptHost() {
            @Override
            public void loadTranscript(YouTubeSubsDialog.TranscriptCallback callback) {
                requestTranscript(callback);
            }

            @Override
            public void seekTo(double seconds) {
                seekVideo(seconds);
            }
        };
    }

    private void requestTranscript(YouTubeSubsDialog.TranscriptCallback callback) {
        String pageUrl = webView.getUrl();
        Uri pageUri = pageUrl == null ? null : Uri.parse(pageUrl);
        String videoId = pageUri == null ? null : pageUri.getQueryParameter("v");
        if (pageUri == null
                || pageUri.getHost() == null
                || !pageUri.getHost().toLowerCase(Locale.US).endsWith("youtube.com")
                || !"/watch".equals(pageUri.getPath())
                || videoId == null
                || !videoId.matches("[A-Za-z0-9_-]{11}")) {
            callback.onError("Open a YouTube video first");
            return;
        }

        long requestId = transcriptRequestCounter.incrementAndGet();
        activeTranscriptRequestId = requestId;
        activeTranscriptCallback = callback;
        activeTranscriptDelivered = false;
        activeTranscriptTitle = "YouTube Video";
        activeTranscriptPageUrl = pageUrl;
        activeVideoId = videoId;
        lastCaptionRequestUrl = "";

        String script = "window.__speedyWatchController "
                + "? window.__speedyWatchController.getCaptionTrack() : null";
        webView.evaluateJavascript(script, result -> handleCaptionTrackResult(requestId, result));
    }

    private void handleCaptionTrackResult(long requestId, String evaluationResult) {
        if (requestId != activeTranscriptRequestId || activeTranscriptDelivered) {
            return;
        }
        try {
            Object outer = new JSONTokener(evaluationResult == null ? "null" : evaluationResult).nextValue();
            if (!(outer instanceof String json)) {
                loadInnerTubeCaptionTrack(requestId);
                return;
            }
            JSONObject metadata = new JSONObject(json);
            String title = metadata.optString("title", "").trim();
            if (!title.isEmpty()) {
                activeTranscriptTitle = title;
            }
            String baseUrl = metadata.optString("baseUrl", "").trim();
            if (baseUrl.isEmpty() || !isTrustedCaptionUri(Uri.parse(baseUrl))) {
                loadInnerTubeCaptionTrack(requestId);
                return;
            }
            fetchTranscriptUrl(requestId, baseUrl, true);
        } catch (Exception error) {
            loadInnerTubeCaptionTrack(requestId);
        }
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

    private String fetchInnerTubeCaptionUrl(String videoId) throws Exception {
        URL endpoint = new URL("https://www.youtube.com/youtubei/v1/player?prettyPrint=false");
        HttpURLConnection connection = (HttpURLConnection) endpoint.openConnection();
        try {
            JSONObject client = new JSONObject()
                    .put("clientName", "ANDROID")
                    .put("clientVersion", "20.10.38");
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
                    "com.google.android.youtube/20.10.38 (Linux; U; Android 14)"
            );
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
            JSONObject selected = selectCaptionTrack(tracks);
            String baseUrl = selected == null ? "" : selected.optString("baseUrl", "").trim();
            if (baseUrl.isEmpty() || !isTrustedCaptionUri(Uri.parse(baseUrl))) {
                throw new IOException("No caption track was available");
            }
            return baseUrl;
        } finally {
            connection.disconnect();
        }
    }

    private static JSONObject selectCaptionTrack(JSONArray tracks) {
        if (tracks == null || tracks.length() == 0) {
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

    private void triggerCaptionNetworkRequest(long requestId) {
        if (requestId != activeTranscriptRequestId || activeTranscriptDelivered) {
            return;
        }
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
        if (!isTrustedCaptionUri(uri)
                || activeTranscriptCallback == null
                || activeTranscriptDelivered) {
            return;
        }
        String url = uri.toString();
        if (url.equals(lastCaptionRequestUrl)) {
            return;
        }
        lastCaptionRequestUrl = url;
        long requestId = activeTranscriptRequestId;
        runOnUiThread(() -> fetchTranscriptUrl(requestId, url, false));
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
        if (fullscreenView != null) {
            hideFullscreenView();
        } else if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
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
