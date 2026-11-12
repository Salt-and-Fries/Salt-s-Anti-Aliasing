package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class VulkanScenePostProcessorTest {
    private static final List<AntiAliasingMode> NATIVE_FSR_MODES = List.of(
            AntiAliasingMode.FSR2_SUPER_RESOLUTION,
            AntiAliasingMode.FSR3_SUPER_RESOLUTION,
            AntiAliasingMode.FSR3_SUPER_RESOLUTION_FRAME_GENERATION
    );

    @Test
    void usesNativeSharpeningAfterSuccessfulFsrEvaluation() {
        for (AntiAliasingMode mode : NATIVE_FSR_MODES) {
            assertFalse(VulkanScenePostProcessor.shouldApplyGenericSharpening(mode, 0.5f, true));
        }
    }

    @Test
    void appliesGenericSharpeningAfterFsrLinearFallback() {
        for (AntiAliasingMode mode : NATIVE_FSR_MODES) {
            assertTrue(VulkanScenePostProcessor.shouldApplyGenericSharpening(mode, 0.5f, false));
        }
    }

    @Test
    void appliesGenericSharpeningForNonFsrModes() {
        assertTrue(VulkanScenePostProcessor.shouldApplyGenericSharpening(
                AntiAliasingMode.TAA,
                0.5f,
                false
        ));
    }

    @Test
    void skipsAllSharpeningAtZero() {
        for (AntiAliasingMode mode : NATIVE_FSR_MODES) {
            assertFalse(VulkanScenePostProcessor.shouldApplyGenericSharpening(mode, 0.0f, false));
        }
        assertFalse(VulkanScenePostProcessor.shouldApplyGenericSharpening(
                AntiAliasingMode.SMAA,
                0.0f,
                false
        ));
    }
}
