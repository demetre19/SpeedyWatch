package com.speedywatch.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class MegaBookmarkStoreTest {
    @Test
    public void suggestedName_usesPageTitleWithoutMegaSuffix() {
        assertEquals(
                "Technical SEO Deep Dive",
                MegaBookmarkStore.suggestedName("Technical SEO Deep Dive - MEGA")
        );
        assertEquals("MEGA folder", MegaBookmarkStore.suggestedName("MEGA"));
    }

    @Test
    public void bookmarkName_isBoundedAndControlCharactersAreRemoved() {
        String longName = "Folder\nName " + "x".repeat(200);
        String normalized = MegaBookmarkStore.normalizeName(longName);

        assertTrue(normalized.startsWith("Folder Name "));
        assertEquals(120, normalized.length());
    }

    @Test
    public void resumeLabel_formatsMinutesAndHours() {
        assertEquals("Continue at 2:05", MegaBookmarkStore.resumeLabel(125));
        assertEquals("Continue at 1:02:03", MegaBookmarkStore.resumeLabel(3723));
    }

    @Test
    public void accesslessIdentity_keepsOnlyPublicHandleAndSelectedNode() {
        String folder = "https://mega.nz/folder/AbCdEf12#AbCdEfGhIjKlMnOpQrStUv";
        String selectedFile = folder + "/file/ZyXwVu12";

        assertEquals(
                "https://mega.nz/folder/AbCdEf12",
                MegaBookmarkStore.accesslessFolderIdentity(selectedFile)
        );
        assertEquals("/file/ZyXwVu12", MegaBookmarkStore.selectionSuffix(selectedFile));
        String selectedWithQuery = "https://mega.nz/folder/AbCdEf12"
                + "?utm_source=share#AbCdEfGhIjKlMnOpQrStUv/file/ZyXwVu12";
        assertEquals(
                "https://mega.nz/folder/AbCdEf12",
                MegaBookmarkStore.accesslessFolderIdentity(selectedWithQuery)
        );
        assertEquals("/file/ZyXwVu12", MegaBookmarkStore.selectionSuffix(selectedWithQuery));
    }

    @Test
    public void encryptedCompleteUrl_reopensSavedSelectionWithoutCandidate() {
        String completeUrl = "https://mega.nz/folder/AbCdEf12#AbCdEfGhIjKlMnOpQrStUv";
        MegaBookmarkStore.Entry entry = new MegaBookmarkStore.Entry(
                "Conference recordings",
                "https://mega.nz/folder/AbCdEf12",
                completeUrl,
                "/file/ZyXwVu12",
                3723,
                1
        );

        assertEquals(
                completeUrl + "/file/ZyXwVu12",
                MegaBookmarkStore.resolveResumeUrl(entry, null)
        );
    }

    @Test
    public void legacyKeylessBookmark_acceptsOnlyMatchingCompleteUrl() {
        MegaBookmarkStore.Entry entry = new MegaBookmarkStore.Entry(
                "Conference recordings",
                "https://mega.nz/folder/AbCdEf12",
                null,
                "/folder/ZyXwVu12",
                125,
                1
        );

        assertNull(MegaBookmarkStore.resolveResumeUrl(entry, null));
        assertNull(MegaBookmarkStore.resolveResumeUrl(
                entry,
                "https://mega.nz/folder/ZyXwVu12#AbCdEfGhIjKlMnOpQrStUv"
        ));
        assertEquals(
                "https://mega.nz/folder/AbCdEf12"
                        + "#AbCdEfGhIjKlMnOpQrStUv/folder/ZyXwVu12",
                MegaBookmarkStore.resolveResumeUrl(
                        entry,
                        "https://mega.nz/folder/AbCdEf12#AbCdEfGhIjKlMnOpQrStUv"
                )
        );
    }
}
