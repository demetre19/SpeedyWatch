package com.speedywatch.app;

import java.util.ArrayList;
import java.util.List;

final class AiEndpoint {
    final String chatUrl;
    final String modelsUrl;
    final String bearer;
    final boolean cloudflare;

    private AiEndpoint(String chatUrl, String modelsUrl, String bearer, boolean cloudflare) {
        this.chatUrl = chatUrl;
        this.modelsUrl = modelsUrl;
        this.bearer = bearer;
        this.cloudflare = cloudflare;
    }

    static AiEndpoint openRouter(String apiKey) {
        return new AiEndpoint(
                "https://openrouter.ai/api/v1/chat/completions",
                "https://openrouter.ai/api/v1/models",
                apiKey == null ? "" : apiKey.trim(),
                false
        );
    }

    static AiEndpoint cloudflare(String accountId, String apiToken) {
        String id = accountId == null ? "" : accountId.trim();
        return new AiEndpoint(
                "https://api.cloudflare.com/client/v4/accounts/" + id
                        + "/ai/v1/chat/completions",
                "",
                apiToken == null ? "" : apiToken.trim(),
                true
        );
    }

    /**
     * Curated Workers AI catalog, previously served by the deployed worker.
     * The Cloudflare model picker is static, so no catalog request is needed.
     */
    static List<OpenRouterClient.Model> cloudflareCatalog() {
        List<OpenRouterClient.Model> models = new ArrayList<>();
        models.add(new OpenRouterClient.Model(
                "@cf/meta/llama-3.1-8b-instruct-fp8-fast",
                "@cf/meta/llama-3.1-8b-instruct-fp8-fast",
                0, Double.NaN, Double.NaN,
                "Default. Fastest verified, reliable strict-JSON output."));
        models.add(new OpenRouterClient.Model(
                "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
                "@cf/meta/llama-3.3-70b-instruct-fp8-fast",
                0, Double.NaN, Double.NaN,
                "Strongest Llama. Strict JSON verified."));
        models.add(new OpenRouterClient.Model(
                "@cf/openai/gpt-oss-20b",
                "@cf/openai/gpt-oss-20b",
                0, Double.NaN, Double.NaN,
                "OpenAI open-weights. Strict JSON verified."));
        models.add(new OpenRouterClient.Model(
                "@cf/openai/gpt-oss-120b",
                "@cf/openai/gpt-oss-120b",
                0, Double.NaN, Double.NaN,
                "Best quality open-weights."));
        models.add(new OpenRouterClient.Model(
                "@cf/mistralai/mistral-small-3.1-24b-instruct",
                "@cf/mistralai/mistral-small-3.1-24b-instruct",
                0, Double.NaN, Double.NaN,
                "Strict JSON verified."));
        return models;
    }
}
