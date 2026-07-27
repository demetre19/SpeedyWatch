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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
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
    static final String EXTRA_KIND = "kind";
    static final String EXTRA_HEIGHT = "height";
    static final String EXTRA_MP3_QUALITY = "mp3_quality";
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
        final String kind;
        final int height;
        final String mp3Quality;
        final String processId = UUID.randomUUID().toString();
        final int resultNotificationId;
        final AtomicBoolean cancelled = new AtomicBoolean();
        final AtomicBoolean finalizationScheduled = new AtomicBoolean();
        final AtomicBoolean finalizationTimedOut = new AtomicBoolean();
        final AtomicBoolean executionFinished = new AtomicBoolean();
        volatile ScheduledFuture<?> finalizationTimeout;

        DownloadJob(
                String url,
                String title,
                String kind,
                int height,
                String mp3Quality,
                int resultNotificationId
        ) {
            this.url = url;
            this.title = title;
            this.kind = kind;
            this.height = height;
            this.mp3Quality = mp3Quality;
            this.resultNotificationId = resultNotificationId;
        }

        void finishExecution() {
            executionFinished.set(true);
            ScheduledFuture<?> timeout = finalizationTimeout;
            if (timeout != null) {
                timeout.cancel(false);
            }
        }
    }

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
        String kind = intent.getStringExtra(EXTRA_KIND);
        int height = intent.getIntExtra(EXTRA_HEIGHT, 0);
        String mp3Quality = intent.getStringExtra(EXTRA_MP3_QUALITY);
        if (mp3Quality == null) {
            mp3Quality = SpeedyWatchSettings.MP3_QUALITY_STANDARD;
        }
        if (!YouTubeDownloadEngine.isSupportedYouTubeUrl(url)
                || (!KIND_MP3.equals(kind) && !KIND_MP4.equals(kind))
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
                YouTubeDownloadEngine.safeDisplayName(title),
                kind,
                height,
                mp3Quality,
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
            YouTubeDownloadEngine.initialize(this);
            if (job.cancelled.get()) {
                throw new IOException("Download cancelled");
            }

            YoutubeDLRequest request = buildRequest(
                    job.url,
                    job.kind,
                    job.height,
                    job.mp3Quality,
                    jobDir
            );
            updateNotification(
                    job.title,
                    "Downloading " + formatLabel(job.kind, job.height, job.mp3Quality),
                    0,
                    true
            );
            YoutubeDL.getInstance().execute(
                    request,
                    job.processId,
                    false,
                    (progress, etaSeconds, outputLine) -> {
                        updateProgress(job, progress, etaSeconds);
                        return Unit.INSTANCE;
                    }
            );
            job.finishExecution();
            if (job.finalizationTimedOut.get()) {
                throw new IOException("Download finalization timed out");
            }
            if (job.cancelled.get()) {
                throw new IOException("Download cancelled");
            }

            String resolvedTitle = YouTubeDownloadEngine.downloadedTitle(jobDir, job.title);
            String extension = KIND_MP3.equals(job.kind) ? "mp3" : "mp4";
            File completed = findCompletedFile(jobDir, extension);
            if (completed == null) {
                throw new IOException(
                        "Download did not produce an "
                                + extension.toUpperCase(Locale.US) + " file"
                );
            }
            updateNotification(resolvedTitle, "Saving to Android Downloads", 100, true);
            Uri published = publish(completed, resolvedTitle + "." + extension, extension);
            showFinishedNotification(job, resolvedTitle, published, extension);
        } catch (Exception error) {
            String message;
            if (job.finalizationTimedOut.get()) {
                message = "Download finalization timed out";
            } else if (job.cancelled.get()) {
                message = "Download cancelled";
            } else {
                message = readableError(error);
            }
            showFailureNotification(job, job.title, message);
        } finally {
            job.finishExecution();
            deleteRecursively(jobDir);
        }
    }

    private YoutubeDLRequest buildRequest(
            String url,
            String kind,
            int height,
            String mp3Quality,
            File jobDir
    ) {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--no-playlist");
        request.addOption("--newline");
        request.addOption("--no-warnings");
        request.addOption("-o", new File(jobDir, "source.%(ext)s").getAbsolutePath());
        request.addOption("--write-info-json");
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
                    + "+bestaudio[ext=m4a][acodec^=mp4a]"
                    + "/best[height<=" + height + "][ext=mp4][vcodec^=avc1][acodec^=mp4a]"
                    + "/bestvideo[height<=" + height + "][ext=mp4]+bestaudio[ext=m4a]"
                    + "/best[height<=" + height + "][ext=mp4]";
            request.addOption("-f", selector);
            request.addOption("--merge-output-format", "mp4");
        }
        return request;
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
            YoutubeDL.getInstance().destroyProcessById(job.processId);
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
            YoutubeDL.getInstance().destroyProcessById(job.processId);
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
            String extension
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
                        extension.toUpperCase(Locale.US) + " saved to Downloads/SpeedyWatch"
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

    private Uri publish(File source, String displayName, String extension) throws IOException {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(
                MediaStore.MediaColumns.MIME_TYPE,
                KIND_MP3.equals(extension) ? "audio/mpeg" : "video/mp4"
        );
        values.put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SpeedyWatch");
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

    private String readableError(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) {
            return "Download failed";
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
