package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public final class SavedThumbnailTest {
    @Test
    public void urlFor_buildsTrustedPreviewForCanonicalYouTubeVariants() {
        String expected = "https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg";

        assertEquals(expected, SavedThumbnail.urlFor(
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ&t=30"
        ));
        assertEquals(expected, SavedThumbnail.urlFor(
                "https://youtu.be/dQw4w9WgXcQ"
        ));
        assertEquals(expected, SavedThumbnail.urlFor(
                "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        ));
    }

    @Test
    public void urlFor_rejectsUnsupportedOrMalformedSources() {
        assertNull(SavedThumbnail.urlFor("https://vimeo.com/123456"));
        assertNull(SavedThumbnail.urlFor("http://www.youtube.com/watch?v=dQw4w9WgXcQ"));
        assertNull(SavedThumbnail.urlFor("https://www.youtube.com/watch?v=short"));
    }
}
