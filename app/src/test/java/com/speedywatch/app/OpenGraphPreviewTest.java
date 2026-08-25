package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class OpenGraphPreviewTest {
    @Test
    public void imageUrlFromHtml_resolvesPublicRelativeImageAndEntities() {
        assertEquals(
                "https://example.com/images/card.jpg?size=large&mode=cover",
                OpenGraphPreview.imageUrlFromHtml(
                        "https://example.com/articles/story",
                        "<meta content=\"/images/card.jpg?size=large&amp;mode=cover\" "
                                + "property=\"og:image\">"
                )
        );
    }

    @Test
    public void imageUrlFromHtml_usesTwitterImageOnlyWhenOgImageIsMissing() {
        assertEquals(
                "https://example.com/twitter-card.jpg",
                OpenGraphPreview.imageUrlFromHtml(
                        "https://example.com/story",
                        "<meta name=\"twitter:image\" content=\"/twitter-card.jpg\">"
                )
        );
        assertEquals(
                "https://example.com/og-card.jpg",
                OpenGraphPreview.imageUrlFromHtml(
                        "https://example.com/story",
                        "<meta name=\"twitter:image\" content=\"/twitter-card.jpg\">"
                                + "<meta property=\"og:image\" content=\"/og-card.jpg\">"
                )
        );
    }

    @Test
    public void imageUrlFromHtml_rejectsMegaAndNonHttpsImages() {
        assertNull(OpenGraphPreview.imageUrlFromHtml(
                "https://example.com/story",
                "<meta property=\"og:image\" content=\"https://mega.nz/file/AbCdEf12#"
                        + "12345678901234567890123456789012345678901\">"
        ));
        assertNull(OpenGraphPreview.imageUrlFromHtml(
                "https://example.com/story",
                "<meta property=\"og:image\" content=\"http://images.example.com/card.jpg\">"
        ));
    }
}
