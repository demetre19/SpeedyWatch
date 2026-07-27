package com.speedywatch.app;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public final class SpeedyWatchSettingsTest {
    @Test
    public void mp3QualityPresets_mapToExpectedBitrates() {
        assertEquals(
                "192K",
                SpeedyWatchSettings.mp3BitrateForQuality(SpeedyWatchSettings.MP3_QUALITY_HIGH)
        );
        assertEquals(
                "128K",
                SpeedyWatchSettings.mp3BitrateForQuality(SpeedyWatchSettings.MP3_QUALITY_STANDARD)
        );
        assertEquals(
                "64K",
                SpeedyWatchSettings.mp3BitrateForQuality(SpeedyWatchSettings.MP3_QUALITY_COMPACT)
        );
    }

    @Test
    public void nextMp3Quality_cyclesAllSupportedPresets() {
        String quality = SpeedyWatchSettings.MP3_QUALITY_STANDARD;
        quality = SpeedyWatchSettings.nextMp3Quality(quality);
        assertEquals(SpeedyWatchSettings.MP3_QUALITY_HIGH, quality);
        quality = SpeedyWatchSettings.nextMp3Quality(quality);
        assertEquals(SpeedyWatchSettings.MP3_QUALITY_COMPACT, quality);
        quality = SpeedyWatchSettings.nextMp3Quality(quality);
        assertEquals(SpeedyWatchSettings.MP3_QUALITY_STANDARD, quality);
        assertTrue(SpeedyWatchSettings.isMp3Quality(quality));
    }
}
