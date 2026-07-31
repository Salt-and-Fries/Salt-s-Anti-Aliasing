package org.betterLostItems.salts_anti_aliasing.client.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

final class AntiAliasingModeTest {
    @Test
    void hidesSharpeningCombinationModes() {
        assertFalse(AntiAliasingMode.implementedModes().contains(AntiAliasingMode.NIS_SHARPEN));
        assertFalse(AntiAliasingMode.implementedModes().contains(AntiAliasingMode.SMAA_NIS_SHARPEN));
        assertFalse(AntiAliasingMode.implementedModes().contains(AntiAliasingMode.FSR1_RCAS));
    }

    @Test
    void migratesLegacySharpeningModesToTheirBaseMode() {
        assertEquals(AntiAliasingMode.OFF, AntiAliasingMode.clampImplemented(AntiAliasingMode.NIS_SHARPEN));
        assertEquals(AntiAliasingMode.SMAA, AntiAliasingMode.clampImplemented(AntiAliasingMode.SMAA_NIS_SHARPEN));
        assertEquals(AntiAliasingMode.FSR1_UPSCALE, AntiAliasingMode.clampImplemented(AntiAliasingMode.FSR1_RCAS));
    }
}
