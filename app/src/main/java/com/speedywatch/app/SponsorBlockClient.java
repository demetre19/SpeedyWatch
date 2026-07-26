package com.speedywatch.app;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Set;

final class SponsorBlockClient {
    private static final int MAXIMUM_RESPONSE_BYTES = 1024 * 1024;

    static final class Segment {
        final double start;
        final double end;
        final int category;

        Segment(double start, double end, int category) {
            this.start = start;
            this.end = end;
            this.category = category;
        }
    }

    List<Segment> fetch(String videoId, Set<String> categories) throws Exception {
        if (videoId == null || !videoId.matches("[A-Za-z0-9_-]{11}") || categories.isEmpty()) {
            return Collections.emptyList();
        }
        StringBuilder endpoint = new StringBuilder("https://sponsor.ajay.app/api/skipSegments/")
                .append(hashPrefix(videoId))
                .append("?actionType=skip&trimUUIDs=true");
        for (String category : categories) {
            if (categoryCode(category) >= 0) {
                endpoint.append("&category=").append(category);
            }
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint.toString()).openConnection();
        connection.setRequestMethod("GET");
        connection.setConnectTimeout(15_000);
        connection.setReadTimeout(15_000);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("User-Agent", "SpeedyWatch/Android");
        try {
            int status = connection.getResponseCode();
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                return Collections.emptyList();
            }
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("SponsorBlock lookup failed (HTTP " + status + ")");
            }
            JSONArray matches = new JSONArray(readBounded(connection.getInputStream()));
            List<Segment> result = new ArrayList<>();
            for (int index = 0; index < matches.length(); index++) {
                JSONObject match = matches.optJSONObject(index);
                if (match == null || !videoId.equals(match.optString("videoID"))) {
                    continue;
                }
                JSONArray segments = match.optJSONArray("segments");
                if (segments == null) {
                    continue;
                }
                for (int segmentIndex = 0; segmentIndex < segments.length() && result.size() < 500; segmentIndex++) {
                    JSONObject item = segments.optJSONObject(segmentIndex);
                    JSONArray range = item == null ? null : item.optJSONArray("segment");
                    String category = item == null ? "" : item.optString("category");
                    int code = categoryCode(category);
                    if (range == null || range.length() < 2 || code < 0 || !categories.contains(category)
                            || !"skip".equals(item.optString("actionType", "skip"))) {
                        continue;
                    }
                    double start = range.optDouble(0, Double.NaN);
                    double end = range.optDouble(1, Double.NaN);
                    if (Double.isFinite(start) && Double.isFinite(end)
                            && start >= 0 && end > start && end <= 604_800) {
                        result.add(new Segment(start, end, code));
                    }
                }
            }
            return result;
        } finally {
            connection.disconnect();
        }
    }

    private static int categoryCode(String category) {
        if ("sponsor".equals(category)) return 0;
        if ("selfpromo".equals(category)) return 1;
        if ("interaction".equals(category)) return 2;
        return -1;
    }

    private static String hashPrefix(String videoId) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(videoId.getBytes(StandardCharsets.UTF_8));
        return String.format(Locale.US, "%02x%02x", digest[0] & 0xff, digest[1] & 0xff);
    }

    private static String readBounded(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAXIMUM_RESPONSE_BYTES) {
                    throw new IOException("SponsorBlock response exceeded the allowed size");
                }
                output.write(buffer, 0, count);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }
}
