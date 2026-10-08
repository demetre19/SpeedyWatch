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
    private static final int MAX_RESPONSE_BYTES = 16 * 1024 * 1024;

    static final class Model {
        final String id;
        final String name;
        final int contextLength;
        final double promptPrice;
        final double completionPrice;
        final String description;

        Model(String id, String name, int contextLength, double promptPrice, double completionPrice) {
            this(id, name, contextLength, promptPrice, completionPrice, null);
        }

        Model(
                String id,
                String name,
                int contextLength,
                double promptPrice,
                double completionPrice,
                String description
        ) {
            this.id = id;
            this.name = name;
            this.contextLength = contextLength;
            this.promptPrice = promptPrice;
            this.completionPrice = completionPrice;
            this.description = description == null || description.trim().isEmpty()
                    ? null : description.trim();
        }

        boolean isFree() {
            return Double.isFinite(promptPrice)
                    && Double.isFinite(completionPrice)
                    && promptPrice == 0
                    && completionPrice == 0;
        }

        boolean hasLongContext() {
            return contextLength >= 100_000;
        }

        String guidance() {
            if (description != null) {
                return description;
            }
            return contextLabel() + " • " + pricingLabel();
        }

        String searchText() {
            return (name + " " + id + " " + guidance() + (isFree() ? " free" : " paid"))
                    .toLowerCase(java.util.Locale.US);
        }

        private String contextLabel() {
            if (contextLength <= 0) {
                return "Context unknown";
            }
            if (contextLength >= 1_000_000) {
                return String.format(java.util.Locale.US, "%.1fM context", contextLength / 1_000_000d);
            }
            return Math.round(contextLength / 1_000d) + "K context";
        }

        private String pricingLabel() {
            if (!Double.isFinite(promptPrice) || !Double.isFinite(completionPrice)) {
                return "Pricing unavailable";
            }
            if (isFree()) {
                return "Free";
            }
            return "$" + pricePerMillion(promptPrice)
                    + "/M input • $" + pricePerMillion(completionPrice) + "/M output";
        }

        private static String pricePerMillion(double perToken) {
            double value = perToken * 1_000_000d;
            return String.format(java.util.Locale.US, value >= 10 ? "%.2f" : "%.4f", value)
                    .replaceAll("0+$", "")
                    .replaceAll("\\.$", "");
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


    List<Model> fetchModels(AiEndpoint endpoint) throws IOException, JSONException {
        HttpURLConnection connection = openConnection(
                endpoint.modelsUrl, "GET", endpoint.bearer, 30000);
        try {
            JSONObject response = readJsonResponse(connection);
            List<Model> models = new ArrayList<>();
            if (endpoint.cloudflare) {
                JSONArray catalog = response.optJSONArray("models");
                if (catalog == null) {
                    throw new IOException("Cloudflare AI returned no model catalog");
                }
                for (int index = 0; index < catalog.length(); index++) {
                    JSONObject item = catalog.optJSONObject(index);
                    if (item == null) {
                        continue;
                    }
                    String id = item.optString("id", "").trim();
                    if (id.isEmpty()) {
                        continue;
                    }
                    models.add(new Model(id, id, 0, Double.NaN, Double.NaN,
                            item.optString("desc", "").trim()));
                }
                return models;
            }
            JSONArray data = response.optJSONArray("data");
            if (data == null) {
                throw new IOException("OpenRouter returned no model catalog");
            }

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
                JSONObject pricing = item.optJSONObject("pricing");
                models.add(new Model(
                        id,
                        name.isEmpty() ? id : name,
                        item.optInt("context_length", 0),
                        parsePrice(pricing, "prompt"),
                        parsePrice(pricing, "completion")
                ));
            }
            models.sort(Comparator.comparing(model -> model.name.toLowerCase(java.util.Locale.US)));
            return models;
        } finally {
            connection.disconnect();
        }
    }

    String summarize(
            AiEndpoint endpoint,
            String modelId,
            String systemPrompt,
            String userMessage
    ) throws IOException, JSONException {
        List<Message> messages = new ArrayList<>();
        messages.add(new Message("system", systemPrompt));
        messages.add(new Message("user", userMessage));
        return generate(endpoint, modelId, messages);
    }

    String generate(
            AiEndpoint endpoint,
            String modelId,
            List<Message> messages
    ) throws IOException, JSONException {
        String providerLabel = endpoint.cloudflare ? "Cloudflare AI" : "OpenRouter";
        if (endpoint.bearer.trim().isEmpty()) {
            throw new IOException(endpoint.cloudflare
                    ? "Add the Cloudflare access token in Settings"
                    : "Add an OpenRouter API key in Settings");
        }
        if (modelId == null || modelId.trim().isEmpty()) {
            throw new IOException("Choose an AI model in Settings");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IOException(providerLabel + " request has no messages");
        }

        JSONObject body = new JSONObject();
        body.put("model", modelId.trim());
        body.put("max_tokens", 4096);
        if (!endpoint.cloudflare) {
            body.put("temperature", 0.7);
        }
        JSONArray payloadMessages = new JSONArray();
        for (Message message : messages) {
            payloadMessages.put(new JSONObject()
                    .put("role", message.role)
                    .put("content", message.content));
        }
        body.put("messages", payloadMessages);

        HttpURLConnection connection = openConnection(
                endpoint.chatUrl, "POST", endpoint.bearer, 120000);
        try {
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            connection.getOutputStream().write(payload);

            JSONObject response = readJsonResponse(connection);
            JSONArray choices = response.optJSONArray("choices");
            JSONObject message = choices == null || choices.length() == 0
                    ? null : choices.optJSONObject(0).optJSONObject("message");
            if (message == null) {
                throw new IOException(providerLabel + " returned no result");
            }
            String content = extractContent(message.opt("content"));
            if (content.trim().isEmpty()) {
                throw new IOException(providerLabel + " returned an empty result");
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
            throw new IOException("The AI service returned an invalid response (HTTP " + status + ")", error);
        }
        if (status < 200 || status >= 300 || response.has("error")) {
            Object error = response.opt("error");
            String message = null;
            if (error instanceof JSONObject errorObject) {
                message = errorObject.optString("message", "").trim();
            } else if (error instanceof String errorText) {
                message = errorText.trim();
            }
            throw new IOException(message == null || message.isEmpty()
                    ? "AI request failed (HTTP " + status + ")" : message);
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

    private static double parsePrice(JSONObject pricing, String key) {
        if (pricing == null) {
            return Double.NaN;
        }
        try {
            double value = Double.parseDouble(pricing.optString(key, ""));
            return Double.isFinite(value) && value >= 0 ? value : Double.NaN;
        } catch (NumberFormatException ignored) {
            return Double.NaN;
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
