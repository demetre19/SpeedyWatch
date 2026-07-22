package com.speedywatch.app;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class OpenRouterClient {
    private static final String MODELS_URL = "https://openrouter.ai/api/v1/models";
    private static final String CHAT_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    static final class Model {
        final String id;
        final String name;
        final int contextLength;

        Model(String id, String name, int contextLength) {
            this.id = id;
            this.name = name;
            this.contextLength = contextLength;
        }

        String searchText() {
            return (name + " " + id).toLowerCase(java.util.Locale.US);
        }
    }
    static final class Message {
        final String role;
        final String content;

        Message(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }


    List<Model> fetchModels(String apiKey) throws IOException, JSONException {
        HttpURLConnection connection = openConnection(MODELS_URL, "GET", apiKey, 30000);
        try {
            JSONObject response = readJsonResponse(connection);
            JSONArray data = response.optJSONArray("data");
            if (data == null) {
                throw new IOException("OpenRouter returned no model catalog");
            }

            List<Model> models = new ArrayList<>();
            for (int index = 0; index < data.length(); index++) {
                JSONObject item = data.optJSONObject(index);
                if (item == null || !supportsTextOutput(item)) {
                    continue;
                }
                String id = item.optString("id", "").trim();
                if (id.isEmpty()) {
                    continue;
                }
                String name = item.optString("name", id).trim();
                models.add(new Model(id, name.isEmpty() ? id : name, item.optInt("context_length", 0)));
            }
            models.sort(Comparator.comparing(model -> model.name.toLowerCase(java.util.Locale.US)));
            return models;
        } finally {
            connection.disconnect();
        }
    }

    String summarize(
            String apiKey,
            String modelId,
            String systemPrompt,
            String userMessage
    ) throws IOException, JSONException {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        messages.add(new Message("user", userMessage));
        return generate(apiKey, modelId, messages);
    }

    String generate(
            String apiKey,
            String modelId,
            List<Message> messages
    ) throws IOException, JSONException {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            throw new IOException("Add an OpenRouter API key in Settings");
        }
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new IOException("Choose an OpenRouter model in Settings");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IOException("OpenRouter request has no messages");
        }

        JSONObject body = new JSONObject();
        body.put("model", modelId.trim());
        body.put("max_tokens", 4096);
        body.put("temperature", 0.7);
        JSONArray payloadMessages = new JSONArray();
        for (Message message : messages) {
            payloadMessages.put(new JSONObject()
                    .put("role", message.role)
                    .put("content", message.content));
        }
        body.put("messages", payloadMessages);

        HttpURLConnection connection = openConnection(CHAT_URL, "POST", apiKey, 120000);
        try {
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            connection.getOutputStream().write(payload);

            JSONObject response = readJsonResponse(connection);
            JSONArray choices = response.optJSONArray("choices");
            JSONObject message = choices == null || choices.length() == 0
                    ? null : choices.optJSONObject(0).optJSONObject("message");
            if (message == null) {
                throw new IOException("OpenRouter returned no result");
            }
            String content = extractContent(message.opt("content"));
            if (content.trim().isEmpty()) {
                throw new IOException("OpenRouter returned an empty result");
            }
            return content.trim();
        } finally {
            connection.disconnect();
        }
    }

    private static HttpURLConnection openConnection(
            String endpoint,
            String method,
            String apiKey,
            int timeoutMillis
    ) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(20000);
        connection.setReadTimeout(timeoutMillis);
        connection.setUseCaches(false);
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("X-OpenRouter-Title", "SpeedyWatch");
        if (apiKey != null && !apiKey.trim().isEmpty()) {
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
        }
        if ("POST".equals(method)) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        }
        return connection;
    }

    private static JSONObject readJsonResponse(HttpURLConnection connection) throws IOException, JSONException {
        int status = connection.getResponseCode();
        InputStream stream = status >= 200 && status < 300
                ? connection.getInputStream() : connection.getErrorStream();
        String text = stream == null ? "" : readBounded(stream);
        JSONObject response;
        try {
            response = text.trim().isEmpty() ? new JSONObject() : new JSONObject(text);
        } catch (JSONException error) {
            throw new IOException("OpenRouter returned an invalid response (HTTP " + status + ")", error);
        }
        if (status < 200 || status >= 300 || response.has("error")) {
            JSONObject error = response.optJSONObject("error");
            String message = error == null ? "" : error.optString("message", "").trim();
            throw new IOException(message.isEmpty()
                    ? "OpenRouter request failed (HTTP " + status + ")" : message);
        }
        return response;
    }

    private static String readBounded(InputStream stream) throws IOException {
        try (InputStream input = stream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int count;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_RESPONSE_BYTES) {
                    throw new IOException("Response exceeded the allowed size");
                }
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private static boolean supportsTextOutput(JSONObject model) {
        JSONObject architecture = model.optJSONObject("architecture");
        if (architecture == null) {
            return true;
        }
        JSONArray outputs = architecture.optJSONArray("output_modalities");
        if (outputs != null) {
            for (int index = 0; index < outputs.length(); index++) {
                if ("text".equalsIgnoreCase(outputs.optString(index))) {
                    return true;
                }
            }
            return false;
        }
        String modality = architecture.optString("modality", "");
        return modality.isEmpty() || modality.endsWith("->text");
    }

    private static String extractContent(Object content) {
        if (content instanceof String text) {
            return text;
        }
        if (content instanceof JSONArray parts) {
            StringBuilder combined = new StringBuilder();
            for (int index = 0; index < parts.length(); index++) {
                JSONObject part = parts.optJSONObject(index);
                if (part != null && "text".equals(part.optString("type"))) {
                    combined.append(part.optString("text"));
                }
            }
            return combined.toString();
        }
        return "";
    }
}
