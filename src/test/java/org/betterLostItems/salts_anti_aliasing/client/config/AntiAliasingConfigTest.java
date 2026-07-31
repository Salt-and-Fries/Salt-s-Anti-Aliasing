package org.betterLostItems.salts_anti_aliasing.client.config;

import com.google.gson.Gson;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AntiAliasingConfigTest {
    private static final Gson GSON = new Gson();

    @Test
    void defaultsUniversalSharpnessToZero() {
        AntiAliasingConfig config = new AntiAliasingConfig();
        config.sanitize();

        assertEquals(0.0f, config.sharpenStrength);
        assertEquals(AntiAliasingConfig.CURRENT_CONFIG_VERSION, config.configVersion);
    }

    @Test
    void defaultsMsaaAlphaToCoverageOffAndCopiesAnExplicitChoice() {
        AntiAliasingConfig config = new AntiAliasingConfig();
        config.sanitize();

        assertFalse(config.msaaAlphaToCoverage);

        config.msaaAlphaToCoverage = true;
        assertTrue(config.copy().msaaAlphaToCoverage);
    }

    @Test
    void oldConfigsWithoutTheMsaaOptionRemainOff() {
        AntiAliasingConfig config = GSON.fromJson("""
                {
                  "configVersion": 2,
                  "mode": "MSAA"
                }
                """, AntiAliasingConfig.class);

        config.sanitize();

        assertFalse(config.msaaAlphaToCoverage);
    }

    @Test
    void clampsUniversalSharpnessAcrossTheFullSliderRange() {
        AntiAliasingConfig config = new AntiAliasingConfig();
        config.sanitize();

        config.sharpenStrength = 2.0f;
        config.sanitize();
        assertEquals(1.0f, config.sharpenStrength);

        config.sharpenStrength = -1.0f;
        config.sanitize();
        assertEquals(0.0f, config.sharpenStrength);
    }

    @Test
    void migratesFsrSharpnessIntoTheUniversalSetting() {
        AntiAliasingConfig config = GSON.fromJson("""
                {
                  "mode": "FSR3_SUPER_RESOLUTION",
                  "sharpenStrength": 0.25,
                  "fsrSharpness": 0.8
                }
                """, AntiAliasingConfig.class);

        config.sanitize();

        assertEquals(0.8f, config.sharpenStrength, 0.0001f);
        assertFalse(GSON.toJson(config).contains("fsrSharpness"));
    }

    @Test
    void mapsLegacyCombinedModeWithoutLosingItsSharpeningChoice() {
        AntiAliasingConfig config = GSON.fromJson("""
                {
                  "mode": "SMAA_NIS_SHARPEN",
                  "sharpenStrength": 0.6,
                  "fsrSharpness": 0.25
                }
                """, AntiAliasingConfig.class);

        config.sanitize();

        assertEquals(AntiAliasingMode.SMAA, config.mode);
        assertEquals(0.6f, config.sharpenStrength, 0.0001f);
    }

    @Test
    void discardsTheOldUnusedGeneralDefaultForUnsharpenedModes() {
        AntiAliasingConfig config = GSON.fromJson("""
                {
                  "mode": "FXAA",
                  "sharpenStrength": 0.25,
                  "fsrSharpness": 0.25
                }
                """, AntiAliasingConfig.class);

        config.sanitize();

        assertEquals(0.0f, config.sharpenStrength);
    }

    @Test
    void preservesAUserSelectedLegacyValueAfterSwitchingModes() {
        AntiAliasingConfig config = GSON.fromJson("""
                {
                  "mode": "FXAA",
                  "sharpenStrength": 0.6,
                  "fsrSharpness": 0.25
                }
                """, AntiAliasingConfig.class);

        config.sanitize();

        assertEquals(0.6f, config.sharpenStrength, 0.0001f);
    }
}
