package org.betterLostItems.salts_anti_aliasing.client.gui;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingVideoSettingsSection.ControlKind.DLSS_QUALITY;
import static org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingVideoSettingsSection.ControlKind.FSR_QUALITY;
import static org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingVideoSettingsSection.ControlKind.MSAA_ALPHA_TO_COVERAGE;
import static org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingVideoSettingsSection.ControlKind.MSAA_SAMPLES;
import static org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingVideoSettingsSection.ControlKind.SHARPNESS;
import static org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingVideoSettingsSection.ControlKind.SPATIAL_UPSCALE_QUALITY;
import static org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingVideoSettingsSection.ControlKind.SSAA_SCALE;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class AntiAliasingVideoSettingsSectionTest {
    @Test
    void exposesUniversalSharpnessForEveryImplementedMode() {
        for (AntiAliasingMode mode : AntiAliasingMode.implementedModes()) {
            assertEquals(
                    SHARPNESS,
                    AntiAliasingVideoSettingsSection.controlLayout(mode).getFirst(),
                    () -> "Missing universal sharpness for " + mode
            );
        }
    }

    @Test
    void exposesEveryModeSpecificControl() {
        assertEquals(
                List.of(SHARPNESS, MSAA_SAMPLES, MSAA_ALPHA_TO_COVERAGE),
                AntiAliasingVideoSettingsSection.controlLayout(AntiAliasingMode.MSAA)
        );
        assertEquals(
                List.of(SHARPNESS, SSAA_SCALE),
                AntiAliasingVideoSettingsSection.controlLayout(AntiAliasingMode.SSAA)
        );
        assertEquals(
                List.of(SHARPNESS, SPATIAL_UPSCALE_QUALITY),
                AntiAliasingVideoSettingsSection.controlLayout(AntiAliasingMode.NIS_UPSCALE)
        );
        assertEquals(
                List.of(SHARPNESS, SPATIAL_UPSCALE_QUALITY),
                AntiAliasingVideoSettingsSection.controlLayout(AntiAliasingMode.FSR1_UPSCALE)
        );
        assertEquals(
                List.of(SHARPNESS, DLSS_QUALITY),
                AntiAliasingVideoSettingsSection.controlLayout(AntiAliasingMode.DLSS_SUPER_RESOLUTION)
        );
        assertEquals(
                List.of(SHARPNESS, FSR_QUALITY),
                AntiAliasingVideoSettingsSection.controlLayout(AntiAliasingMode.FSR2_SUPER_RESOLUTION)
        );
        assertEquals(
                List.of(SHARPNESS, FSR_QUALITY),
                AntiAliasingVideoSettingsSection.controlLayout(AntiAliasingMode.FSR3_SUPER_RESOLUTION)
        );
        assertEquals(
                List.of(SHARPNESS, FSR_QUALITY),
                AntiAliasingVideoSettingsSection.controlLayout(
                        AntiAliasingMode.FSR3_SUPER_RESOLUTION_FRAME_GENERATION
                )
        );
    }

    @Test
    void keepsSimplePostProcessModesFreeOfStaleQualityControls() {
        for (AntiAliasingMode mode : List.of(
                AntiAliasingMode.OFF,
                AntiAliasingMode.FXAA,
                AntiAliasingMode.SMAA,
                AntiAliasingMode.TAA
        )) {
            assertEquals(
                    List.of(SHARPNESS),
                    AntiAliasingVideoSettingsSection.controlLayout(mode),
                    () -> "Unexpected mode-specific control for " + mode
            );
        }
    }
}
