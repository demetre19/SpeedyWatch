package com.speedywatch.app;

import org.junit.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class GitHubUpdateCheckerTest {
    @Test
    public void validateAssetSize_acceptsCurrentApkSize() throws Exception {
        GitHubUpdateChecker.validateAssetSize(159_140_358L);
    }

    @Test(expected = GitHubUpdateChecker.UpdateException.class)
    public void validateAssetSize_rejectsAssetsAboveBound() throws Exception {
        GitHubUpdateChecker.validateAssetSize(256L * 1024L * 1024L + 1L);
    }

    @Test
    public void verifyDownloadedApk_acceptsExactSizeAndDigest() throws Exception {
        byte[] apk = "SpeedyWatch update".getBytes(StandardCharsets.UTF_8);

        assertTrue(GitHubUpdateChecker.verifyDownloadedApk(
                new ByteArrayInputStream(apk),
                18,
                "08daf9a31d721fa2b033bcdf5a342018fb93a94d9376e5444785deb10ce6192f"
        ));
    }

    @Test
    public void verifyDownloadedApk_rejectsWrongSize() throws Exception {
        byte[] apk = "SpeedyWatch update".getBytes(StandardCharsets.UTF_8);

        assertFalse(GitHubUpdateChecker.verifyDownloadedApk(
                new ByteArrayInputStream(apk),
                17,
                "08daf9a31d721fa2b033bcdf5a342018fb93a94d9376e5444785deb10ce6192f"
        ));
    }

    @Test
    public void verifyDownloadedApk_rejectsWrongDigest() throws Exception {
        byte[] apk = "SpeedyWatch update".getBytes(StandardCharsets.UTF_8);

        assertFalse(GitHubUpdateChecker.verifyDownloadedApk(
                new ByteArrayInputStream(apk),
                18,
                "18daf9a31d721fa2b033bcdf5a342018fb93a94d9376e5444785deb10ce6192f"
        ));
    }
}
