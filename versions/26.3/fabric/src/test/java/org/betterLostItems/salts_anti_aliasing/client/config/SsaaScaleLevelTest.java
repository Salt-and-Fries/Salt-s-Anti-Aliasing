package org.betterLostItems.salts_anti_aliasing.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class SsaaScaleLevelTest {
    @Test
    void extendsTheSelectableRangeToEightHundredPercent() {
        SsaaScaleLevel[] levels = SsaaScaleLevel.values();

        assertEquals(SsaaScaleLevel.X800, levels[levels.length - 1]);
        assertEquals(800, SsaaScaleLevel.X800.percentage());
        assertEquals(8.0f, SsaaScaleLevel.X800.scaleFactor());
        assertEquals(64.0f, SsaaScaleLevel.X800.pixelMultiplier());
    }

    @Test
    void warnsOnlyAboveFourHundredPercent() {
        assertFalse(SsaaScaleLevel.X400.requiresPerformanceWarning());
        assertTrue(SsaaScaleLevel.X500.requiresPerformanceWarning());
        assertTrue(SsaaScaleLevel.X800.requiresPerformanceWarning());
    }
}
