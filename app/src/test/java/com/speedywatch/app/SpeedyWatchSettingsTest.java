package com.speedywatch.app;

import org.junit.Test;
import java.util.EnumMap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
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

    @Test
    public void savedFloatingPositions_acceptOnlyUnsetOrNormalizedFractions() {
        assertTrue(SpeedyWatchSettings.isSavedPosition(-1f));
        assertTrue(SpeedyWatchSettings.isSavedPosition(0f));
        assertTrue(SpeedyWatchSettings.isSavedPosition(1f));
        assertFalse(SpeedyWatchSettings.isSavedPosition(-0.1f));
        assertFalse(SpeedyWatchSettings.isSavedPosition(1.1f));
        assertFalse(SpeedyWatchSettings.isSavedPosition(Float.NaN));
    }

    @Test
    public void pictureInPictureControl_acceptsOnlyButtonOrPinch() {
        assertTrue(SpeedyWatchSettings.isPictureInPictureControl(
                SpeedyWatchSettings.PIP_CONTROL_BUTTON
        ));
        assertTrue(SpeedyWatchSettings.isPictureInPictureControl(
                SpeedyWatchSettings.PIP_CONTROL_PINCH
        ));
        assertFalse(SpeedyWatchSettings.isPictureInPictureControl("both"));
        assertFalse(SpeedyWatchSettings.isPictureInPictureControl(""));
        assertFalse(SpeedyWatchSettings.isPictureInPictureControl(null));
    }

    @Test
    public void omniButtonDefaults_matchApprovedEightDirectionLayout() {
        assertEquals(
                OmniButtonAction.YOUTUBE_HISTORY,
                OmniButtonAction.defaultFor(OmniButtonGesture.Direction.UP)
        );
        assertEquals(
                OmniButtonAction.SPEED_UP,
                OmniButtonAction.defaultFor(OmniButtonGesture.Direction.UP_RIGHT)
        );
        assertEquals(
                OmniButtonAction.NEXT_CHAPTER,
                OmniButtonAction.defaultFor(OmniButtonGesture.Direction.RIGHT)
        );
        assertEquals(
                OmniButtonAction.SEEK_FORWARD,
                OmniButtonAction.defaultFor(OmniButtonGesture.Direction.DOWN_RIGHT)
        );
        assertEquals(
                OmniButtonAction.WATCH_LATER,
                OmniButtonAction.defaultFor(OmniButtonGesture.Direction.DOWN)
        );
        assertEquals(
                OmniButtonAction.SEEK_BACKWARD,
                OmniButtonAction.defaultFor(OmniButtonGesture.Direction.DOWN_LEFT)
        );
        assertEquals(
                OmniButtonAction.PREVIOUS_CHAPTER,
                OmniButtonAction.defaultFor(OmniButtonGesture.Direction.LEFT)
        );
        assertEquals(
                OmniButtonAction.SPEED_DOWN,
                OmniButtonAction.defaultFor(OmniButtonGesture.Direction.UP_LEFT)
        );
        assertEquals(0.5, OmniButtonAction.defaultAmount(OmniButtonAction.SPEED_UP), 0.0);
        assertEquals(0.25, OmniButtonAction.defaultAmount(OmniButtonAction.SPEED_DOWN), 0.0);
        assertEquals(15.0, OmniButtonAction.defaultAmount(OmniButtonAction.SEEK_FORWARD), 0.0);
    }

    @Test
    public void omniWebDefaults_areBrowserFirstAndSeparateFromYouTube() {
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> web =
                SpeedyWatchSettings.defaultOmniWebButtonActions();
        assertEquals(OmniButtonAction.SUMMARY_ONE, web.get(OmniButtonGesture.Direction.UP));
        assertEquals(OmniButtonAction.SPEED_UP, web.get(OmniButtonGesture.Direction.UP_RIGHT));
        assertEquals(OmniButtonAction.BROWSER_FORWARD, web.get(OmniButtonGesture.Direction.RIGHT));
        assertEquals(OmniButtonAction.SHARE, web.get(OmniButtonGesture.Direction.DOWN_RIGHT));
        assertEquals(OmniButtonAction.SAVED, web.get(OmniButtonGesture.Direction.DOWN));
        assertEquals(OmniButtonAction.RELOAD, web.get(OmniButtonGesture.Direction.DOWN_LEFT));
        assertEquals(OmniButtonAction.BROWSER_BACK, web.get(OmniButtonGesture.Direction.LEFT));
        assertEquals(OmniButtonAction.SPEED_DOWN, web.get(OmniButtonGesture.Direction.UP_LEFT));
        assertFalse(web.containsValue(OmniButtonAction.YOUTUBE_HISTORY));
        assertFalse(web.containsValue(OmniButtonAction.WATCH_LATER));
        assertFalse(web.containsValue(OmniButtonAction.NEXT_CHAPTER));
        assertTrue(SpeedyWatchSettings.validOmniButtonBindings(
                web,
                SpeedyWatchSettings.defaultOmniWebButtonAmounts()
        ));
    }

    @Test
    public void omniButtonActionIdsAndAmounts_areValidatedBeforePersistence() {
        for (OmniButtonAction action : OmniButtonAction.values()) {
            assertEquals(action, OmniButtonAction.fromId(action.id));
        }
        EnumMap<OmniButtonGesture.Direction, OmniButtonAction> actions =
                SpeedyWatchSettings.defaultOmniButtonActions();
        EnumMap<OmniButtonGesture.Direction, Double> amounts =
                SpeedyWatchSettings.defaultOmniButtonAmounts();
        assertTrue(SpeedyWatchSettings.validOmniButtonBindings(actions, amounts));

        amounts.put(OmniButtonGesture.Direction.UP_RIGHT, 3.76);
        assertFalse(SpeedyWatchSettings.validOmniButtonBindings(actions, amounts));
        amounts.put(OmniButtonGesture.Direction.UP_RIGHT, 0.5);
        amounts.put(OmniButtonGesture.Direction.DOWN_RIGHT, 601.0);
        assertFalse(SpeedyWatchSettings.validOmniButtonBindings(actions, amounts));
    }

}
