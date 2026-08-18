package com.speedywatch.app;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class FloatingControlPositionTest {
    @Test
    public void normalizedPosition_resolvesAcrossDifferentScreenBounds() {
        float fraction = FloatingControlPosition.fraction(300, 20, 420);

        assertEquals(0.7f, fraction, 0.0001f);
        assertEquals(720, FloatingControlPosition.resolve(fraction, 20, 1020, -1));
    }

    @Test
    public void position_isClampedAndUsesDefaultWhenNotSaved() {
        assertEquals(20, FloatingControlPosition.resolve(-1f, 20, 420, -50));
        assertEquals(420, FloatingControlPosition.resolve(Float.NaN, 20, 420, 500));
        assertEquals(1f, FloatingControlPosition.fraction(800, 20, 420), 0.0001f);
    }

    @Test
    public void zeroTravelRange_staysAtSafeMinimum() {
        assertEquals(40, FloatingControlPosition.resolve(0.5f, 40, 40, 80));
        assertEquals(0f, FloatingControlPosition.fraction(80, 40, 40), 0.0001f);
    }
    @Test
    public void maximumCoordinate_keepsWholeControlInsideSafeViewport() {
        assertEquals(933, FloatingControlPosition.maximumCoordinate(
                1080, 0, 137, 10, 10
        ));
        assertEquals(909, FloatingControlPosition.maximumCoordinate(
                1080, 24, 137, 10, 10
        ));
        assertEquals(40, FloatingControlPosition.maximumCoordinate(
                100, 30, 80, 20, 40
        ));
    }
    @Test
    public void expansionTranslation_keepsGrowthInsideSafeViewport() {
        assertEquals(17.125f, FloatingControlPosition.expansionTranslation(
                10, 137, 1.25f, 10, 933
        ), 0.0001f);
        assertEquals(0f, FloatingControlPosition.expansionTranslation(
                400, 137, 1.25f, 10, 933
        ), 0.0001f);
        assertEquals(-17.125f, FloatingControlPosition.expansionTranslation(
                933, 137, 1.25f, 10, 933
        ), 0.0001f);
    }


}
