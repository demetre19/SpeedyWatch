package com.speedywatch.app;

import org.junit.Test;

public final class GitHubUpdateCheckerTest {
    @Test
    public void validateAssetSize_acceptsCurrentApkSize() throws Exception {
        GitHubUpdateChecker.validateAssetSize(159_140_358L);
    }

    @Test(expected = GitHubUpdateChecker.UpdateException.class)
    public void validateAssetSize_rejectsAssetsAboveBound() throws Exception {
        GitHubUpdateChecker.validateAssetSize(256L * 1024L * 1024L + 1L);
    }
}
