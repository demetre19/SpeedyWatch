package com.speedywatch.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.IBinder;
import android.provider.MediaStore;
import android.widget.Toast;

import com.yausername.youtubedl_android.YoutubeDL;
import com.yausername.youtubedl_android.YoutubeDLRequest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import kotlin.Unit;

public final class SpeedyWatchDownloadService extends Service {
    static final String ACTION_DOWNLOAD = "com.speedywatch.app.action.DOWNLOAD";
    static final String ACTION_CANCEL = "com.speedywatch.app.action.CANCEL_DOWNLOAD";
    static final String EXTRA_URL = "url";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_TITLE_VERIFIED = "title_verified";
    static final String EXTRA_KIND = "kind";
    static final String EXTRA_HEIGHT = "height";
    static final String EXTRA_MP3_QUALITY = "mp3_quality";
    static final String EXTRA_COOKIE_HEADER = "cookie_header";
    static final String EXTRA_USER_AGENT = "user_agent";
    static final String EXTRA_REFERER = "referer";
    static final String EXTRA_CAPTURED_MEDIA_URL = "captured_media_url";
    static final String EXTRA_CAPTURED_COOKIE_HEADER = "captured_cookie_header";
    static final String EXTRA_CAPTURED_USER_AGENT = "captured_user_agent";
    static final String EXTRA_CAPTURED_REFERER = "captured_referer";
    static final String EXTRA_CAPTURED_ORIGIN = "captured_origin";
    static final String EXTRA_CAPTURED_AUTHORIZATION = "captured_authorization";
    static final String KIND_MP3 = "mp3";
    static final String KIND_MP4 = "mp4";

    private static final String CHANNEL_ID = "speedywatch_downloads";
    private static final int NOTIFICATION_ID = 4107;
    private static final String STATE_PREFERENCES = "download_service_state";
    private static final String NEXT_RESULT_NOTIFICATION_ID = "next_result_notification_id";
    private static final int MAX_QUEUED_DOWNLOADS = 50;
    private static final long FINALIZATION_TIMEOUT_MINUTES = 10L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor();
    private final SequentialDownloadQueue<DownloadJob> queue =
            new SequentialDownloadQueue<>(MAX_QUEUED_DOWNLOADS);

    private DownloadJob activeJob;
    private boolean workerScheduled;
    private int latestStartId;
    private volatile long lastProgressUpdate;
    private volatile int lastProgress = -1;
    private volatile String notificationTitle = "SpeedyWatch";
    private volatile String notificationDetail = "Preparing download";
    private volatile int notificationProgress;
    private volatile boolean notificationIndeterminate = true;

    private static final class DownloadJob {
        final String url;
        final String title;
        final boolean titleVerified;
        final String kind;
        final int height;
        final String mp3Quality;
        final String cookieHeader;
        final String userAgent;
        final String referer;
        final String capturedMediaUrl;
        final String capturedCookieHeader;
        final String capturedUserAgent;
        final String capturedReferer;
        final String capturedOrigin;
        final String capturedAuthorization;
        final String processId = UUID.randomUUID().toString();
        final int resultNotificationId;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean finalizationScheduled = new AtomicBoolean();
        final AtomicBoolean finalizationTimedOut = new AtomicBoolean();
        final AtomicBoolean executionFinished = new AtomicBoolean();
        volatile ScheduledFuture<?> finalizationTimeout;
        volatile Process capturedProcess;

        DownloadJob(
                String url,
                String title,
                boolean titleVerified,
                String kind,
                int height,
                String mp3Quality,
                String cookieHeader,
                String userAgent,
                String referer,
                String capturedMediaUrl,
                String capturedCookieHeader,
                String capturedUserAgent,
                String capturedReferer,
                String capturedOrigin,
                String capturedAuthorization,
                int resultNotificationId
        ) {
            this.url = url;
            this.title = title;
            this.titleVerified = titleVerified;
            this.kind = kind;
            this.height = height;
            this.mp3Quality = mp3Quality;
            this.cookieHeader = cookieHeader;
            this.userAgent = userAgent;
            this.referer = referer;
            this.capturedMediaUrl = capturedMediaUrl;
            this.capturedCookieHeader = capturedCookieHeader;
            this.capturedUserAgent = capturedUserAgent;
            this.capturedReferer = capturedReferer;
            this.capturedOrigin = capturedOrigin;
            this.capturedAuthorization = capturedAuthorization;
            this.resultNotificationId = resultNotificationId;
        }

        void beginExecutionAttempt() {
            executionFinished.set(false);
            finalizationScheduled.set(false);
            finalizationTimedOut.set(false);
            ScheduledFuture<?> timeout = finalizationTimeout;
            if (timeout != null) {
                timeout.cancel(false);
                finalizationTimeout = null;
            }
        }

        void finishExecution() {
            executionFinished.set(true);
            ScheduledFuture<?> timeout = finalizationTimeout;
            if (timeout != null) {
                timeout.cancel(false);
            }
        }
    }
    private static final class DownloadResult {
        final File directory;
        final boolean captured;

        DownloadResult(File directory, boolean captured) {
            this.directory = directory;
            this.captured = captured;
        }
    }
    enum AttemptSource {
        PAGE,
        PAGE_WITH_CAPTURED_CONTEXT,
        CAPTURED_MEDIA
    }
    private static final List<AttemptSource> PAGE_ATTEMPTS =
            List.of(AttemptSource.PAGE);
    private static final List<AttemptSource> CAPTURED_THEN_PAGE_ATTEMPTS =
            List.of(AttemptSource.CAPTURED_MEDIA, AttemptSource.PAGE);
    private static final List<AttemptSource> PAGE_THEN_CAPTURED_ATTEMPTS =
            List.of(AttemptSource.PAGE, AttemptSource.CAPTURED_MEDIA);
    private static final List<AttemptSource> VIMEO_ATTEMPTS = List.of(
            AttemptSource.PAGE_WITH_CAPTURED_CONTEXT,
            AttemptSource.CAPTURED_MEDIA,
            AttemptSource.PAGE
    );




    private int reserveResultNotificationId() {
        SharedPreferences preferences = getSharedPreferences(STATE_PREFERENCES, MODE_PRIVATE);
        int current = preferences.getInt(
                NEXT_RESULT_NOTIFICATION_ID,
                NOTIFICATION_ID + 1
        );
        if (current <= NOTIFICATION_ID) {
            current = NOTIFICATION_ID + 1;
        }
        int next = current == Integer.MAX_VALUE ? NOTIFICATION_ID + 1 : current + 1;
        boolean saved = preferences.edit()
                .putInt(NEXT_RESULT_NOTIFICATION_ID, next)
                .commit();
        return saved ? current : -1;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager manager = getSystemService(NotificationManager.class);
        manager.createNotificationChannel(new NotificationChannel(
                CHANNEL_ID,
                "Video downloads",
                NotificationManager.IMPORTANCE_LOW
        ));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        synchronized (queue) {
            latestStartId = Math.max(latestStartId, startId);
        }
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancelActiveDownload();
            stopIfIdle();
            return START_NOT_STICKY;
        }
        if (intent == null || !ACTION_DOWNLOAD.equals(intent.getAction())) {
            stopIfIdle();
            return START_NOT_STICKY;
        }

        String url = intent.getStringExtra(EXTRA_URL);
        String title = intent.getStringExtra(EXTRA_TITLE);
        boolean titleVerified = intent.getBooleanExtra(
                EXTRA_TITLE_VERIFIED,
                false
        );
        String kind = intent.getStringExtra(EXTRA_KIND);
        int height = intent.getIntExtra(EXTRA_HEIGHT, 0);
        String mp3Quality = intent.getStringExtra(EXTRA_MP3_QUALITY);
        String cookieHeader = boundedExtra(intent.getStringExtra(EXTRA_COOKIE_HEADER), 32 * 1024);
        String userAgent = boundedExtra(intent.getStringExtra(EXTRA_USER_AGENT), 512);
        String referer = CapturedMediaRequest.validContextUrl(
                url,
                intent.getStringExtra(EXTRA_REFERER)
        );
        String capturedMediaUrl = SupportedSite.validatedHttpsUrl(
                intent.getStringExtra(EXTRA_CAPTURED_MEDIA_URL)
        );
        String capturedCookieHeader = boundedExtra(
                intent.getStringExtra(EXTRA_CAPTURED_COOKIE_HEADER),
                32 * 1024
        );
        String capturedUserAgent = boundedExtra(
                intent.getStringExtra(EXTRA_CAPTURED_USER_AGENT),
                512
        );
        String capturedReferer = CapturedMediaRequest.validContextUrl(
                url,
                intent.getStringExtra(EXTRA_CAPTURED_REFERER)
        );
        String capturedOrigin = CapturedMediaRequest.validContextUrl(
                url,
                intent.getStringExtra(EXTRA_CAPTURED_ORIGIN)
        );
        String capturedAuthorization = boundedExtra(
                intent.getStringExtra(EXTRA_CAPTURED_AUTHORIZATION),
                8 * 1024
        );
        if (!CapturedMediaRequest.isAllowedResource(url, capturedMediaUrl)) {
            capturedMediaUrl = null;
            capturedCookieHeader = null;
            capturedUserAgent = null;
            capturedReferer = null;
            capturedOrigin = null;
            capturedAuthorization = null;
        }
        if (mp3Quality == null) {
            mp3Quality = SpeedyWatchSettings.MP3_QUALITY_STANDARD;
        }
        SupportedSite downloadSite = SupportedSite.forUrl(url);
        if (!MediaDownloadEngine.isSupportedDownloadUrl(url)
                || (!KIND_MP3.equals(kind) && !KIND_MP4.equals(kind))
                || (downloadSite == SupportedSite.SOUNDCLOUD && !KIND_MP3.equals(kind))
                || (KIND_MP3.equals(kind) && !SpeedyWatchSettings.isMp3Quality(mp3Quality))
                || (KIND_MP4.equals(kind) && (height < 144 || height > 4320))) {
            stopIfIdle();
            Toast.makeText(this, "Invalid download request", Toast.LENGTH_LONG).show();
            return START_NOT_STICKY;
        }

        int resultNotificationId = reserveResultNotificationId();
        if (resultNotificationId < 0) {
            stopIfIdle();
            Toast.makeText(
                    this,
                    "Could not reserve a download notification",
                    Toast.LENGTH_LONG
            ).show();
            return START_NOT_STICKY;
        }

        DownloadJob job = new DownloadJob(
                url,
                MediaDownloadEngine.safeDisplayName(title),
                titleVerified,
                kind,
                height,
                mp3Quality,
                cookieHeader,
                userAgent,
                referer,
                capturedMediaUrl,
                capturedCookieHeader,
                capturedUserAgent,
                capturedReferer,
                capturedOrigin,
                capturedAuthorization,
                resultNotificationId
        );
        boolean startWorker = false;
        int queuePosition = 0;
        synchronized (queue) {
            if (!workerScheduled) {
                activeJob = job;
                workerScheduled = true;
                startWorker = true;
            } else {
                queuePosition = queue.offer(job);
            }
        }

        if (queuePosition < 0) {
            Toast.makeText(
                    this,
                    "Download queue is full (" + MAX_QUEUED_DOWNLOADS + " waiting)",
                    Toast.LENGTH_LONG
            ).show();
            return START_NOT_STICKY;
        }
        if (startWorker) {
            beginForeground(job);
            Toast.makeText(
                    this,
                    formatLabel(job.kind, job.height, job.mp3Quality) + " download started",
                    Toast.LENGTH_LONG
            ).show();
            executor.execute(this::drainQueue);
        } else {
            refreshQueueNotification();
            Toast.makeText(
                    this,
                    formatLabel(job.kind, job.height, job.mp3Quality)
                            + " queued — position " + queuePosition,
                    Toast.LENGTH_LONG
            ).show();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        cancelActiveDownload();
        synchronized (queue) {
            queue.clear();
            activeJob = null;
            workerScheduled = false;
        }
        executor.shutdownNow();
        watchdog.shutdownNow();
        super.onDestroy();
    }

    private void drainQueue() {
        while (true) {
            DownloadJob job;
            synchronized (queue) {
                job = activeJob;
                if (job == null) {
                    finishServiceLocked();
                    return;
                }
            }

            lastProgress = -1;
            lastProgressUpdate = 0L;
            updateNotification(job.title, "Preparing download", 0, true);
            download(job);

            synchronized (queue) {
                activeJob = queue.poll();
                if (activeJob == null) {
                    finishServiceLocked();
                    return;
                }
            }
        }
    }

    private void finishServiceLocked() {
        workerScheduled = false;
        stopForeground(STOP_FOREGROUND_REMOVE);
        stopSelfResult(latestStartId);
    }

    private void stopIfIdle() {
        synchronized (queue) {
            if (!workerScheduled) {
                stopSelfResult(latestStartId);
            }
        }
    }

    private void download(DownloadJob job) {
        File jobDir = new File(getCacheDir(), "media-download-" + job.processId);
        try {
            if (!jobDir.mkdirs() && !jobDir.isDirectory()) {
                throw new IOException("Could not create temporary download folder");
            }
            MediaDownloadEngine.initialize(this);
            if (job.cancelled.get()) {
                throw new IOException("Download cancelled");
            }

            DownloadResult result = executeDownload(job, jobDir);
            MediaDownloadEngine.DownloadedMedia metadata = resolvedDownloadedMedia(
                    result.directory,
                    job.title,
                    result.captured,
                    job.titleVerified
            );
            String resolvedTitle = metadata.title;
            String extension = KIND_MP3.equals(job.kind) ? "mp3" : "mp4";
            File completed = findCompletedFile(result.directory, extension);
            if (completed == null) {
                throw new IOException(
                        "Download did not produce an "
                                + extension.toUpperCase(Locale.US) + " file"
                );
            }
            String relativePath = relativeDownloadPath(
                    job.url,
                    job.kind,
                    metadata.publisher
            );
            updateNotification(
                    resolvedTitle,
                    "Saving to " + displayDownloadPath(relativePath),
                    100,
                    true
            );
            Uri published = publish(
                    completed,
                    resolvedTitle + "." + extension,
                    extension,
                    relativePath
            );
            showFinishedNotification(job, resolvedTitle, published, extension, relativePath);
        } catch (Exception error) {
            String message;
            if (job.finalizationTimedOut.get()) {
                message = "Download finalization timed out";
            } else if (job.cancelled.get()) {
                message = "Download cancelled";
            } else {
                message = readableError(job, error);
            }
            showFailureNotification(job, job.title, message);
        } finally {
            job.finishExecution();
            deleteRecursively(jobDir);
        }
    }
    static MediaDownloadEngine.DownloadedMedia resolvedDownloadedMedia(
            File directory,
            String title,
            boolean captured,
            boolean titleVerified
    ) throws IOException {
        if (!captured) {
            return MediaDownloadEngine.downloadedMedia(directory, title);
        }
        if (!titleVerified) {
            throw new IOException("Could not determine the Vimeo video title");
        }
        return new MediaDownloadEngine.DownloadedMedia(
                MediaDownloadEngine.safeDisplayName(title),
                null
        );
    }


    private DownloadResult executeDownload(DownloadJob job, File jobDir) throws Exception {
        Exception lastError = null;
        for (AttemptSource attempt : attemptSequence(
                job.url,
                job.capturedMediaUrl != null
        )) {
            try {
                return executeAttempt(job, jobDir, attempt);
            } catch (Exception error) {
                lastError = error;
                if (job.cancelled.get() || job.finalizationTimedOut.get()) {
                    throw error;
                }
            }
        }
        if (lastError != null) {
            throw lastError;
        }
        throw new IOException("Download failed");
    }

    private DownloadResult executeAttempt(
            DownloadJob job,
            File jobDir,
            AttemptSource attempt
    ) throws Exception {
        boolean capturedContext = attempt != AttemptSource.PAGE;
        String attemptName = attempt == AttemptSource.PAGE
                ? "page"
                : attempt == AttemptSource.PAGE_WITH_CAPTURED_CONTEXT
                ? "page-context"
                : "captured";
        File attemptDir = new File(jobDir, attemptName);
        deleteRecursively(attemptDir);
        if (!attemptDir.mkdirs() && !attemptDir.isDirectory()) {
            throw new IOException("Could not prepare the download");
        }
        String targetUrl = targetUrlForAttempt(job.url, job.capturedMediaUrl, attempt);
        if (attempt == AttemptSource.CAPTURED_MEDIA
                && KIND_MP3.equals(job.kind)
                && SupportedSite.forUrl(job.url) == SupportedSite.VIMEO) {
            return executeCapturedVimeo(job, attemptDir, targetUrl);
        }
        File cookieFile;
        if (attempt == AttemptSource.PAGE_WITH_CAPTURED_CONTEXT) {
            cookieFile = MediaDownloadEngine.writeSessionCookies(
                    attemptDir,
                    targetUrl,
                    job.cookieHeader,
                    job.capturedMediaUrl,
                    job.capturedCookieHeader
            );
        } else if (attempt == AttemptSource.CAPTURED_MEDIA) {
            cookieFile = MediaDownloadEngine.writeSessionCookies(
                    attemptDir,
                    targetUrl,
                    job.capturedCookieHeader
            );
        } else {
            cookieFile = MediaDownloadEngine.writeSessionCookies(
                    attemptDir,
                    targetUrl,
                    job.cookieHeader
            );
        }
        YoutubeDLRequest request = buildRequest(
                targetUrl,
                job.kind,
                job.height,
                job.mp3Quality,
                capturedContext ? null : job.userAgent,
                capturedContext ? null : job.referer,
                cookieFile,
                attemptDir
        );
        if (capturedContext) {
            addCapturedRequestOptions(request, job, attempt);
        }
        job.beginExecutionAttempt();
        lastProgress = -1;
        lastProgressUpdate = 0L;
        updateNotification(
                job.title,
                "Downloading " + formatLabel(job.kind, job.height, job.mp3Quality),
                0,
                true
        );
        try {
            YoutubeDL.getInstance().execute(
                    request,
                    job.processId,
                    false,
                    (progress, etaSeconds, outputLine) -> {
                        updateProgress(job, progress, etaSeconds);
                        return Unit.INSTANCE;
                    }
            );
        } finally {
            job.finishExecution();
        }
        if (job.finalizationTimedOut.get()) {
            throw new IOException("Download finalization timed out");
        }
        if (job.cancelled.get()) {
            throw new IOException("Download cancelled");
        }
        return new DownloadResult(attemptDir, capturedContext);
    }
    private DownloadResult executeCapturedVimeo(
            DownloadJob job,
            File attemptDir,
            String targetUrl
    ) throws Exception {
        if (!job.titleVerified) {
            throw new IOException("Could not determine the Vimeo video title");
        }
        File output = new File(
                attemptDir,
                KIND_MP3.equals(job.kind) ? "source.mp3" : "source.mp4"
        );
        File ffmpeg = new File(getApplicationInfo().nativeLibraryDir, "libffmpeg.so");
        if (!ffmpeg.isFile()) {
            throw new IOException("Video converter is unavailable");
        }
        List<String> command = buildCapturedFfmpegCommand(
                ffmpeg.getAbsolutePath(),
                targetUrl,
                job.kind,
                job.mp3Quality,
                job.capturedUserAgent == null ? job.userAgent : job.capturedUserAgent,
                job.capturedReferer == null ? job.referer : job.capturedReferer,
                job.capturedOrigin,
                job.capturedCookieHeader,
                job.capturedAuthorization,
                output
        );
        ProcessBuilder builder = new ProcessBuilder(command).redirectErrorStream(true);
        configureFfmpegEnvironment(builder);
        job.beginExecutionAttempt();
        lastProgress = -1;
        lastProgressUpdate = 0L;
        updateNotification(
                job.title,
                "Downloading " + formatLabel(job.kind, job.height, job.mp3Quality),
                0,
                true
        );
        StringBuilder processOutput = new StringBuilder();
        try {
            Process process = builder.start();
            job.capturedProcess = process;
            if (job.cancelled.get()) {
                stopProcess(process);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            )) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (processOutput.length() > 32 * 1024) {
                        processOutput.delete(0, processOutput.length() - 16 * 1024);
                    }
                    processOutput.append(line).append('\n');
                }
            }
            int exitCode = process.waitFor();
            if (job.cancelled.get()) {
                throw new IOException("Download cancelled");
            }
            if (exitCode != 0) {
                String outputText = processOutput.toString();
                throw new IOException(
                        outputText.contains("403")
                                ? "Captured Vimeo stream was rejected (HTTP 403)"
                                : "Captured Vimeo stream could not be downloaded"
                );
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IOException("Download interrupted", error);
        } finally {
            Process process = job.capturedProcess;
            if (process != null && process.isAlive()) {
                stopProcess(process);
            }
            job.capturedProcess = null;
            job.finishExecution();
        }
        if (!output.isFile() || output.length() <= 0) {
            throw new IOException("Captured Vimeo stream produced no media");
        }
        return new DownloadResult(attemptDir, true);
    }

    private void configureFfmpegEnvironment(ProcessBuilder builder) {
        File packages = new File(
                new File(getNoBackupFilesDir(), "youtubedl-android"),
                "packages"
        );
        File python = new File(packages, "python");
        File ffmpeg = new File(packages, "ffmpeg");
        File aria2c = new File(packages, "aria2c");
        String libraryPath = new File(python, "usr/lib").getAbsolutePath()
                + ":" + new File(ffmpeg, "usr/lib").getAbsolutePath()
                + ":" + new File(aria2c, "usr/lib").getAbsolutePath();
        String pythonHome = new File(python, "usr").getAbsolutePath();
        String nativeLibraryDir = getApplicationInfo().nativeLibraryDir;
        builder.environment().put("LD_LIBRARY_PATH", libraryPath);
        builder.environment().put(
                "SSL_CERT_FILE",
                new File(python, "usr/etc/tls/cert.pem").getAbsolutePath()
        );
        builder.environment().put(
                "PATH",
                String.valueOf(System.getenv("PATH")) + ":" + nativeLibraryDir
        );
        builder.environment().put("PYTHONHOME", pythonHome);
        builder.environment().put("HOME", pythonHome);
        builder.environment().put("TMPDIR", getCacheDir().getAbsolutePath());
    }

    static List<String> buildCapturedFfmpegCommand(
            String executable,
            String url,
            String kind,
            String mp3Quality,
            String userAgent,
            String referer,
            String origin,
            String cookieHeader,
            String authorization,
            File output
    ) {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.add("-nostdin");
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("warning");
        command.add("-rw_timeout");
        command.add("30000000");
        command.add("-y");
        if (userAgent != null) {
            command.add("-user_agent");
            command.add(userAgent);
        }
        StringBuilder headers = new StringBuilder();
        appendFfmpegHeader(headers, "Referer", referer);
        appendFfmpegHeader(headers, "Origin", origin);
        appendFfmpegHeader(headers, "Cookie", cookieHeader);
        appendFfmpegHeader(headers, "Authorization", authorization);
        if (headers.length() > 0) {
            command.add("-headers");
            command.add(headers.toString());
        }
        command.add("-i");
        command.add(url);
        if (KIND_MP3.equals(kind)) {
            command.add("-map");
            command.add("0:a:0?");
            command.add("-vn");
            command.add("-c:a");
            command.add("libmp3lame");
            command.add("-b:a");
            command.add(SpeedyWatchSettings.mp3BitrateForQuality(mp3Quality));
        } else {
            command.add("-map");
            command.add("0:v:0?");
            command.add("-map");
            command.add("0:a:0?");
            command.add("-c");
            command.add("copy");
            command.add("-movflags");
            command.add("+faststart");
        }
        command.add(output.getAbsolutePath());
        return command;
    }

    private static void appendFfmpegHeader(
            StringBuilder headers,
            String name,
            String value
    ) {
        if (value != null && !value.isEmpty()) {
            headers.append(name).append(": ").append(value).append("\r\n");
        }
    }

    private static void stopProcess(Process process) {
        process.destroy();
        if (process.isAlive()) {
            process.destroyForcibly();
        }
    }

    private void addCapturedRequestOptions(
            YoutubeDLRequest request,
            DownloadJob job,
            AttemptSource attempt
    ) {
        String capturedUserAgent = job.capturedUserAgent == null
                ? job.userAgent
                : job.capturedUserAgent;
        if (capturedUserAgent != null) {
            request.addOption("--user-agent", capturedUserAgent);
        }
        String capturedReferer = job.capturedReferer == null
                ? job.referer
                : job.capturedReferer;
        if (capturedReferer != null) {
            request.addOption("--referer", capturedReferer);
        }
        if (job.capturedOrigin != null) {
            request.addOption("--add-header", "Origin:" + job.capturedOrigin);
        }
        if (attempt == AttemptSource.CAPTURED_MEDIA
                && job.capturedAuthorization != null) {
            request.addOption(
                    "--add-header",
                    "Authorization:" + job.capturedAuthorization
            );
        }
    }

    static List<AttemptSource> attemptSequence(
            String sourceUrl,
            boolean hasCapturedMedia
    ) {
        if (!hasCapturedMedia) {
            return PAGE_ATTEMPTS;
        }
        if (SupportedSite.forUrl(sourceUrl) == SupportedSite.VIMEO) {
            return VIMEO_ATTEMPTS;
        }
        return prefersCapturedRequest(sourceUrl)
                ? CAPTURED_THEN_PAGE_ATTEMPTS
                : PAGE_THEN_CAPTURED_ATTEMPTS;
    }

    private static boolean prefersCapturedRequest(String sourceUrl) {
        SupportedSite site = SupportedSite.forUrl(sourceUrl);
        return site == SupportedSite.VIMEO
                || site == SupportedSite.X
                || site == SupportedSite.FACEBOOK
                || site == SupportedSite.LOOM;
    }
    static String targetUrlForAttempt(
            String sourceUrl,
            String capturedMediaUrl,
            AttemptSource attempt
    ) {
        if (attempt == AttemptSource.CAPTURED_MEDIA && capturedMediaUrl != null) {
            return capturedMediaUrl;
        }
        return sourceUrl;
    }


    static YoutubeDLRequest buildRequest(
            String url,
            String kind,
            int height,
            String mp3Quality,
            String userAgent,
            String referer,
            File cookieFile,
            File jobDir
    ) {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--no-playlist");
        request.addOption("--newline");
        request.addOption("--no-warnings");
        request.addOption("-o", new File(jobDir, "source.%(ext)s").getAbsolutePath());
        request.addOption("--write-info-json");
        MediaDownloadEngine.addSessionOptions(
                request,
                url,
                userAgent,
                referer,
                cookieFile
        );
        if (KIND_MP3.equals(kind)) {
            request.addOption("-f", "bestaudio/best");
            request.addOption("-x");
            request.addOption("--audio-format", "mp3");
            request.addOption(
                    "--audio-quality",
                    SpeedyWatchSettings.mp3BitrateForQuality(mp3Quality)
            );
        } else {
            String selector = "bestvideo[height<=" + height + "][ext=mp4][vcodec^=avc1]"
                    + "+bestaudio[acodec^=mp4a]"
                    + "/best[height<=" + height + "][ext=mp4][vcodec^=avc1][acodec^=mp4a]"
                    + "/bestvideo[height<=" + height + "][ext=mp4]+bestaudio[acodec^=mp4a]"
                    + "/bestvideo[height<=" + height + "][ext=mp4]+bestaudio"
                    + "/best[height<=" + height + "][ext=mp4]";
            request.addOption("-f", selector);
            request.addOption("--merge-output-format", "mp4");
        }
        return request;
    }

    static String relativeDownloadPath(
            String url,
            String kind,
            String publisher
    ) {
        String root = "Download/SpeedyWatch";
        SupportedSite site = SupportedSite.forUrl(url);
        if (site == null) {
            return root;
        }
        String optionalPublisher = publisher == null ? "" : publisher.trim();
        switch (site) {
            case YOUTUBE:
                String youtube = root + "/YouTube/"
                        + (KIND_MP3.equals(kind) ? "audio" : "video");
                return optionalPublisher.isEmpty()
                        ? youtube
                        : youtube + "/" + optionalPublisher;
            case BILIBILI:
                String bilibili = root + "/BiliBili";
                return optionalPublisher.isEmpty()
                        ? bilibili
                        : bilibili + "/" + optionalPublisher;
            case INSTAGRAM:
                return root + "/Instagram";
            case VIMEO:
                return root + "/Vimeo";
            case X:
                return root + "/X";
            case FACEBOOK:
                return root + "/Facebook";
            case SOUNDCLOUD:
                String soundCloudArtist = optionalPublisher.isEmpty()
                        ? "Unknown artist"
                        : optionalPublisher;
                return root + "/SoundCloud/" + soundCloudArtist;
            case LOOM:
                return root + "/Loom";
            default:
                return root;
        }
    }

    private static String displayDownloadPath(String relativePath) {
        return relativePath.startsWith("Download/")
                ? "Downloads/" + relativePath.substring("Download/".length())
                : relativePath;
    }

    private void updateProgress(DownloadJob job, float progress, long etaSeconds) {
        int rounded = Math.max(0, Math.min(100, Math.round(progress)));
        long now = System.currentTimeMillis();
        if (rounded >= 100) {
            scheduleFinalizationTimeout(job);
            if (lastProgress != 100) {
                lastProgress = 100;
                lastProgressUpdate = now;
                updateNotification(
                        job.title,
                        "Finishing " + formatLabel(job.kind, job.height, job.mp3Quality),
                        100,
                        true
                );
            }
            return;
        }
        if (rounded == lastProgress || (now - lastProgressUpdate < 500)) {
            return;
        }
        lastProgress = rounded;
        lastProgressUpdate = now;
        String detail = "Downloading " + formatLabel(job.kind, job.height, job.mp3Quality)
                + " — " + rounded + "%";
        if (etaSeconds > 0) {
            detail += " — " + etaSeconds + "s left";
        }
        updateNotification(job.title, detail, rounded, false);
    }

    private void scheduleFinalizationTimeout(DownloadJob job) {
        if (!job.finalizationScheduled.compareAndSet(false, true)) {
            return;
        }
        job.finalizationTimeout = watchdog.schedule(() -> {
            if (job.executionFinished.get() || job.cancelled.get()) {
                return;
            }
            job.finalizationTimedOut.set(true);
            stopActiveExecution(job);
        }, FINALIZATION_TIMEOUT_MINUTES, TimeUnit.MINUTES);
    }

    private String formatLabel(String kind, int height, String mp3Quality) {
        return KIND_MP3.equals(kind)
                ? "MP3 · " + SpeedyWatchSettings.mp3QualityLabel(mp3Quality)
                : height + "p MP4";
    }

    private void cancelActiveDownload() {
        DownloadJob job;
        synchronized (queue) {
            job = activeJob;
        }
        if (job != null) {
            job.cancelled.set(true);
            stopActiveExecution(job);
        }
    }

    private void stopActiveExecution(DownloadJob job) {
        YoutubeDL.getInstance().destroyProcessById(job.processId);
        Process process = job.capturedProcess;
        if (process != null) {
            stopProcess(process);
        }
    }

    private void beginForeground(DownloadJob job) {
        notificationTitle = job.title;
        notificationDetail = "Preparing download";
        notificationProgress = 0;
        notificationIndeterminate = true;
        startForeground(
                NOTIFICATION_ID,
                progressNotification(job.title, notificationDetail, 0, true)
        );
    }

    private void refreshQueueNotification() {
        boolean active;
        synchronized (queue) {
            active = workerScheduled;
        }
        if (!active) {
            return;
        }
        getSystemService(NotificationManager.class).notify(
                NOTIFICATION_ID,
                progressNotification(
                        notificationTitle,
                        queuedDetail(notificationDetail),
                        notificationProgress,
                        notificationIndeterminate
                )
        );
    }

    private String queuedDetail(String detail) {
        int waiting = queue.size();
        return waiting > 0 ? detail + " • " + waiting + " queued" : detail;
    }

    private Notification progressNotification(
            String title,
            String detail,
            int progress,
            boolean indeterminate
    ) {
        Intent cancelIntent = new Intent(this, SpeedyWatchDownloadService.class)
                .setAction(ACTION_CANCEL);
        PendingIntent cancelPendingIntent = PendingIntent.getService(
                this,
                1,
                cancelIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(detail)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setProgress(100, progress, indeterminate)
                .addAction(new Notification.Action.Builder(
                        null,
                        "Cancel current",
                        cancelPendingIntent
                ).build())
                .build();
    }

    private void updateNotification(String title, String detail, int progress, boolean indeterminate) {
        notificationTitle = title;
        notificationDetail = detail;
        notificationProgress = progress;
        notificationIndeterminate = indeterminate;
        getSystemService(NotificationManager.class).notify(
                NOTIFICATION_ID,
                progressNotification(title, queuedDetail(detail), progress, indeterminate)
        );
    }


    private void showFinishedNotification(
            DownloadJob job,
            String title,
            Uri uri,
            String extension,
            String relativePath
    ) {
        int notificationId = job.resultNotificationId;
        Intent openIntent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, KIND_MP3.equals(extension) ? "audio/mpeg" : "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                notificationId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(
                        extension.toUpperCase(Locale.US)
                                + " saved to " + displayDownloadPath(relativePath)
                )
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(notificationId, notification);
    }

    private void showFailureNotification(DownloadJob job, String title, String message) {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(job.resultNotificationId, notification);
    }

    private Uri publish(
            File source,
            String displayName,
            String extension,
            String relativePath
    ) throws IOException {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(
                MediaStore.MediaColumns.MIME_TYPE,
                KIND_MP3.equals(extension) ? "audio/mpeg" : "video/mp4"
        );
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath);
        values.put(MediaStore.MediaColumns.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) {
            throw new IOException("Android could not create the download file");
        }
        try (FileInputStream input = new FileInputStream(source);
             OutputStream output = resolver.openOutputStream(uri, "w")) {
            if (output == null) {
                throw new IOException("Android could not open the download file");
            }
            byte[] buffer = new byte[64 * 1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
        } catch (IOException error) {
            resolver.delete(uri, null, null);
            throw error;
        }
        ContentValues complete = new ContentValues();
        complete.put(MediaStore.MediaColumns.IS_PENDING, 0);
        resolver.update(uri, complete, null, null);
        return uri;
    }

    private File findCompletedFile(File directory, String extension) {
        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }
        File best = null;
        for (File file : files) {
            if (file.isFile()
                    && file.getName().toLowerCase(Locale.US).endsWith("." + extension)
                    && (best == null || file.length() > best.length())) {
                best = file;
            }
        }
        return best;
    }

    private static String boundedExtra(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() > maxLength
                || value.indexOf('\r') >= 0
                || value.indexOf('\n') >= 0
                || value.indexOf('\0') >= 0) {
            return null;
        }
        return value;
    }

    private String readableError(DownloadJob job, Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Download failed";
        }
        String lower = message.toLowerCase(Locale.US);
        if (SupportedSite.forUrl(job.url) == SupportedSite.LOOM
                && (lower.contains("403")
                || lower.contains("private")
                || lower.contains("no video formats")
                || lower.contains("not authorized"))) {
            return "Loom video is private or unavailable to SpeedyWatch";
        }
        String firstLine = message.trim().split("\\R", 2)[0];
        return firstLine.length() > 140 ? firstLine.substring(0, 140) : firstLine;
    }

    private void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        file.delete();
    }
}
