package com.speedywatch.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
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
import java.util.concurrent.atomic.AtomicBoolean;

import kotlin.Unit;

public final class SpeedyWatchDownloadService extends Service {
    static final String ACTION_DOWNLOAD = "com.speedywatch.app.action.DOWNLOAD";
    static final String ACTION_CANCEL = "com.speedywatch.app.action.CANCEL_DOWNLOAD";
    static final String EXTRA_URL = "url";
    static final String EXTRA_TITLE = "title";
    static final String EXTRA_KIND = "kind";
    static final String EXTRA_HEIGHT = "height";
    static final String KIND_MP3 = "mp3";
    static final String KIND_MP4 = "mp4";

    private static final String CHANNEL_ID = "speedywatch_downloads";
    private static final int NOTIFICATION_ID = 4107;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final AtomicBoolean running = new AtomicBoolean();
    private volatile boolean cancelled;
    private volatile String processId;
    private volatile long lastProgressUpdate;
    private volatile int lastProgress = -1;

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
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancelActiveDownload();
            return START_NOT_STICKY;
        }
        if (intent == null || !ACTION_DOWNLOAD.equals(intent.getAction())) {
            stopSelf(startId);
            return START_NOT_STICKY;
        }
        if (!running.compareAndSet(false, true)) {
            Toast.makeText(this, "A SpeedyWatch download is already running", Toast.LENGTH_LONG).show();
            return START_NOT_STICKY;
        }

        String url = intent.getStringExtra(EXTRA_URL);
        String title = intent.getStringExtra(EXTRA_TITLE);
        String kind = intent.getStringExtra(EXTRA_KIND);
        int height = intent.getIntExtra(EXTRA_HEIGHT, 0);
        if (!YouTubeDownloadEngine.isSupportedYouTubeUrl(url)
                || (!KIND_MP3.equals(kind) && !KIND_MP4.equals(kind))
                || (KIND_MP4.equals(kind) && (height < 144 || height > 4320))) {
            running.set(false);
            stopSelf(startId);
            Toast.makeText(this, "Invalid download request", Toast.LENGTH_LONG).show();
            return START_NOT_STICKY;
        }

        cancelled = false;
        processId = UUID.randomUUID().toString();
        String safeTitle = YouTubeDownloadEngine.safeDisplayName(title);
        startForeground(NOTIFICATION_ID, progressNotification(safeTitle, "Preparing download", 0, true));
        executor.execute(() -> download(startId, url, safeTitle, kind, height));
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onDestroy() {
        cancelActiveDownload();
        executor.shutdownNow();
        super.onDestroy();
    }

    private void download(int startId, String url, String title, String kind, int height) {
        File jobDir = new File(getCacheDir(), "media-download-" + processId);
        try {
            if (!jobDir.mkdirs() && !jobDir.isDirectory()) {
                throw new IOException("Could not create temporary download folder");
            }
            YouTubeDownloadEngine.initialize(this);
            if (cancelled) {
                throw new IOException("Download cancelled");
            }

            YoutubeDLRequest request = buildRequest(url, kind, height, jobDir);
            updateNotification(title, "Downloading " + formatLabel(kind, height), 0, true);
            YoutubeDL.getInstance().execute(request, processId, false, (progress, etaSeconds, outputLine) -> {
                updateProgress(title, kind, height, progress, etaSeconds);
                return Unit.INSTANCE;
            });
            if (cancelled) {
                throw new IOException("Download cancelled");
            }

            String extension = KIND_MP3.equals(kind) ? "mp3" : "mp4";
            File completed = findCompletedFile(jobDir, extension);
            if (completed == null) {
                throw new IOException("Download did not produce an " + extension.toUpperCase(Locale.US) + " file");
            }
            updateNotification(title, "Saving to Android Downloads", 100, true);
            Uri published = publish(completed, title + "." + extension, extension);
            showFinishedNotification(title, published, extension);
        } catch (Exception error) {
            showFailureNotification(title, cancelled ? "Download cancelled" : readableError(error));
        } finally {
            deleteRecursively(jobDir);
            processId = null;
            cancelled = false;
            running.set(false);
            stopForeground(STOP_FOREGROUND_DETACH);
            stopSelf(startId);
        }
    }

    private YoutubeDLRequest buildRequest(String url, String kind, int height, File jobDir) {
        YoutubeDLRequest request = new YoutubeDLRequest(url);
        request.addOption("--no-playlist");
        request.addOption("--newline");
        request.addOption("--no-warnings");
        request.addOption("-o", new File(jobDir, "source.%(ext)s").getAbsolutePath());
        if (KIND_MP3.equals(kind)) {
            request.addOption("-f", "bestaudio/best");
            request.addOption("-x");
            request.addOption("--audio-format", "mp3");
            request.addOption("--audio-quality", "0");
        } else {
            String selector = "bestvideo[height<=" + height + "][ext=mp4][vcodec^=avc1]+bestaudio[ext=m4a]"
                    + "/bestvideo[height<=" + height + "][ext=mp4]+bestaudio[ext=m4a]"
                    + "/bestvideo[height<=" + height + "]+bestaudio/best[height<=" + height + "]/best";
            request.addOption("-f", selector);
            request.addOption("--merge-output-format", "mp4");
            request.addOption("--recode-video", "mp4");
        }
        return request;
    }

    private void updateProgress(String title, String kind, int height, float progress, long etaSeconds) {
        int rounded = Math.max(0, Math.min(100, Math.round(progress)));
        long now = System.currentTimeMillis();
        if (rounded == lastProgress || (now - lastProgressUpdate < 500 && rounded < 100)) {
            return;
        }
        lastProgress = rounded;
        lastProgressUpdate = now;
        String detail = "Downloading " + formatLabel(kind, height) + " — " + rounded + "%";
        if (etaSeconds > 0) {
            detail += " — " + etaSeconds + "s left";
        }
        updateNotification(title, detail, rounded, false);
    }

    private String formatLabel(String kind, int height) {
        return KIND_MP3.equals(kind) ? "MP3" : height + "p MP4";
    }

    private void cancelActiveDownload() {
        cancelled = true;
        String activeId = processId;
        if (activeId != null) {
            YoutubeDL.getInstance().destroyProcessById(activeId);
        }
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
                        "Cancel",
                        cancelPendingIntent
                ).build())
                .build();
    }

    private void updateNotification(String title, String detail, int progress, boolean indeterminate) {
        getSystemService(NotificationManager.class).notify(
                NOTIFICATION_ID,
                progressNotification(title, detail, progress, indeterminate)
        );
    }

    private void showFinishedNotification(String title, Uri uri, String extension) {
        Intent openIntent = new Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, KIND_MP3.equals(extension) ? "audio/mpeg" : "video/mp4")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent openPendingIntent = PendingIntent.getActivity(
                this,
                2,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(extension.toUpperCase(Locale.US) + " saved to Downloads/SpeedyWatch")
                .setContentIntent(openPendingIntent)
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
    }

    private void showFailureNotification(String title, String message) {
        Notification notification = new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_download)
                .setContentTitle(title)
                .setContentText(message)
                .setAutoCancel(true)
                .build();
        getSystemService(NotificationManager.class).notify(NOTIFICATION_ID, notification);
    }

    private Uri publish(File source, String displayName, String extension) throws IOException {
        ContentResolver resolver = getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.MediaColumns.DISPLAY_NAME, displayName);
        values.put(MediaStore.MediaColumns.MIME_TYPE, KIND_MP3.equals(extension) ? "audio/mpeg" : "video/mp4");
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
            if (file.isFile() && file.getName().toLowerCase(Locale.US).endsWith("." + extension)
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
