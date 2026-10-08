package com.speedywatch.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class AiEndpointTest {
    @Test
    public void cloudflareEndpoint_buildsAccountScopedRoutes() {
        AiEndpoint endpoint = AiEndpoint.cloudflare(
                " e28ca40f3222016020f8b963154cf73c ", " token ");
        assertEquals(
                "https://api.cloudflare.com/client/v4/accounts/"
                        + "e28ca40f3222016020f8b963154cf73c/ai/v1/chat/completions",
                endpoint.chatUrl);
        assertEquals("token", endpoint.bearer);
        assertTrue(endpoint.cloudflare);
    }

    @Test
    public void cloudflareCatalog_listsCuratedModels() {
        assertTrue(AiEndpoint.cloudflareCatalog().size() >= 5);
        assertEquals(SpeedyWatchSettings.CLOUDFLARE_DEFAULT_MODEL_ID,
                AiEndpoint.cloudflareCatalog().get(0).id);
    }

    @Test
    public void openRouterEndpoint_usesOfficialUrls() {
        AiEndpoint endpoint = AiEndpoint.openRouter(" sk-or-v1-abc ");
        assertEquals("https://openrouter.ai/api/v1/chat/completions", endpoint.chatUrl);
        assertEquals("https://openrouter.ai/api/v1/models", endpoint.modelsUrl);
        assertEquals("sk-or-v1-abc", endpoint.bearer);
        assertFalse(endpoint.cloudflare);
    }
}
