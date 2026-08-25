package com.speedywatch.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.regex.Pattern;

/** Creates small, app-private previews for saved YouTube content. */
final class SavedThumbnail {
    static final int MAX_BYTES = 64 * 1024;

    private static final int MAX_DOWNLOAD_BYTES = 2 * 1024 * 1024;
    private static final int WIDTH = 320;
    private static final int HEIGHT = 180;
    private static final Pattern VIDEO_ID = Pattern.compile("[A-Za-z0-9_-]{11}");

    private SavedThumbnail() {
    }

    static String urlFor(String sourceUrl) {
        String canonical = YouTubeUrls.canonicalVideoUrl(sourceUrl);
        if (canonical == null) {
            return null;
        }
        try {
            String videoId = URI.create(canonical).getQuery();
            if (videoId == null || !videoId.startsWith("v=")) {
                return null;
            }
            videoId = videoId.substring(2);
            int separator = videoId.indexOf('&');
            if (separator >= 0) {
                videoId = videoId.substring(0, separator);
            }
            if (!VIDEO_ID.matcher(videoId).matches()) {
                return null;
            }
            return "https://i.ytimg.com/vi/" + videoId + "/mqdefault.jpg";
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    static byte[] fetch(String sourceUrl) throws IOException {
        String thumbnailUrl = urlFor(sourceUrl);
        if (thumbnailUrl == null) {
            return null;
        }
        return fetchImageUrl(thumbnailUrl);
    }

    /**
     * Fetches and bounds an HTTPS image without sending WebView cookies. Redirects are
     * followed only across validated public HTTPS URLs.
     */
    static byte[] fetchImageUrl(String imageUrl) throws IOException {
        String current = SupportedSite.validatedHttpsUrl(imageUrl);
        if (current == null || SupportedSite.forUrl(current) == SupportedSite.MEGA) {
            return null;
        }
        for (int redirect = 0; redirect <= 3; redirect++) {
            HttpURLConnection connection = (HttpURLConnection) new URL(current).openConnection();
            try {
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10_000);
                connection.setReadTimeout(15_000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestProperty("Accept", "image/*");
                int responseCode = connection.getResponseCode();
                if (responseCode >= 300 && responseCode < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null) {
                        return null;
                    }
                    URI resolved = URI.create(current).resolve(location);
                    current = SupportedSite.validatedHttpsUrl(resolved.toString());
                    if (current == null || SupportedSite.forUrl(current) == SupportedSite.MEGA) {
                        return null;
                    }
                    continue;
                }
                if (responseCode != HttpURLConnection.HTTP_OK) {
                    return null;
                }
                String contentType = connection.getContentType();
                if (contentType == null
                        || !contentType.toLowerCase(java.util.Locale.US).startsWith("image/")) {
                    return null;
                }
                int contentLength = connection.getContentLength();
                if (contentLength > MAX_DOWNLOAD_BYTES) {
                    return null;
                }
                byte[] downloaded;
                try (InputStream input = connection.getInputStream()) {
                    downloaded = readBounded(input);
                }
                return resize(downloaded);
            } finally {
                connection.disconnect();
            }
        }
        return null;
    }

    private static byte[] readBounded(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(32 * 1024);
        byte[] buffer = new byte[8 * 1024];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_DOWNLOAD_BYTES) {
                throw new IOException("Video thumbnail is too large");
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static byte[] resize(byte[] encoded) throws IOException {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(encoded, 0, encoded.length, bounds);
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            return null;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 1;
        while (bounds.outWidth / options.inSampleSize > WIDTH * 2
                || bounds.outHeight / options.inSampleSize > HEIGHT * 2) {
            options.inSampleSize *= 2;
        }
        Bitmap source = BitmapFactory.decodeByteArray(encoded, 0, encoded.length, options);
        if (source == null) {
            return null;
        }

        Bitmap target = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.RGB_565);
        try {
            Canvas canvas = new Canvas(target);
            canvas.drawColor(android.graphics.Color.BLACK);
            float scale = Math.max(
                    WIDTH / (float) source.getWidth(),
                    HEIGHT / (float) source.getHeight()
            );
            float scaledWidth = source.getWidth() * scale;
            float scaledHeight = source.getHeight() * scale;
            Rect sourceBounds = new Rect(0, 0, source.getWidth(), source.getHeight());
            RectF destination = new RectF(
                    (WIDTH - scaledWidth) / 2f,
                    (HEIGHT - scaledHeight) / 2f,
                    (WIDTH + scaledWidth) / 2f,
                    (HEIGHT + scaledHeight) / 2f
            );
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
            canvas.drawBitmap(source, sourceBounds, destination, paint);

            ByteArrayOutputStream output = new ByteArrayOutputStream(32 * 1024);
            for (int quality : new int[]{78, 64, 50}) {
                output.reset();
                if (!target.compress(Bitmap.CompressFormat.JPEG, quality, output)) {
                    throw new IOException("Video thumbnail could not be encoded");
                }
                if (output.size() <= MAX_BYTES) {
                    return output.toByteArray();
                }
            }
            return null;
        } finally {
            source.recycle();
            target.recycle();
        }
    }
}
