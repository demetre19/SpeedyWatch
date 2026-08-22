package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** De-duplication key and destination-resolution contracts for scraped X-thread links. */
public class ScrapedLinkStoreTest {

    @Test
    public void canonicalKeyIgnoresSchemeCaseHostAndFragment() {
        assertEquals(
                ScrapedLinkStore.canonicalUrlKey("https://example.com/Page"),
                ScrapedLinkStore.canonicalUrlKey("https://WWW.example.com/Page#section")
        );
        assertEquals(
                ScrapedLinkStore.canonicalUrlKey("http://example.com/a"),
                ScrapedLinkStore.canonicalUrlKey("https://example.com/a/")
        );
    }

    @Test
    public void canonicalKeyStripsTrackingParameters() {
        assertEquals(
                "x.com/user/status/123",
                ScrapedLinkStore.canonicalUrlKey(
                        "https://x.com/user/status/123?s=20&utm_source=share&ref_src=twsrc")
        );
        assertEquals(
                "youtu.be/abc123",
                ScrapedLinkStore.canonicalUrlKey("https://youtu.be/abc123?si=tracked&t=42")
        );
        // Non-tracking parameters must survive: they can change the destination.
        assertEquals(
                "example.com/watch?v=42",
                ScrapedLinkStore.canonicalUrlKey("https://www.example.com/watch?v=42&t=1")
        );
    }

    @Test
    public void canonicalKeyRejectsInvalidInput() {
        assertNull(ScrapedLinkStore.canonicalUrlKey(null));
        assertNull(ScrapedLinkStore.canonicalUrlKey(""));
        assertNull(ScrapedLinkStore.canonicalUrlKey("not a url"));
    }

    @Test
    public void displayTextRecoversRealDestinationBehindShortLink() {
        String resolved = ScrapedLinkStore.resolveRealUrl(
                "https://t.co/abcDEF123",
                "https://huggingface.co/VextLabsinc"
        );
        assertEquals("https://huggingface.co/VextLabsinc", resolved);
        assertFalse(ScrapedLinkStore.isShortLink(resolved));
    }

    @Test
    public void bareShortLinkIsKeptForLaterExpansion() {
        String resolved = ScrapedLinkStore.resolveRealUrl("https://t.co/abcDEF123", "");
        assertEquals("https://t.co/abcDEF123", resolved);
        assertTrue(ScrapedLinkStore.isShortLink(resolved));
    }

    @Test
    public void proseDisplayTextNeverBecomesTheDestination() {
        assertEquals(
                "https://example.com/page",
                ScrapedLinkStore.resolveRealUrl(
                        "https://example.com/page", "Read this great article about models"));
    }

    @Test
    public void schemelessDisplayTextIsAccepted() {
        assertEquals(
                "https://huggingface.co/VextLabsinc",
                ScrapedLinkStore.realUrlFromDisplay("https://huggingface.co/VextLabsinc")
        );
        assertNull(ScrapedLinkStore.realUrlFromDisplay("huggingface.co/VextLabsinc"));
    }

    @Test
    public void shortLinksAreRecognized() {
        assertTrue(ScrapedLinkStore.isShortLink("https://t.co/xyz"));
        assertFalse(ScrapedLinkStore.isShortLink("https://example.com/t.co/xyz"));
        assertFalse(ScrapedLinkStore.isShortLink("not a url"));
    }
}
