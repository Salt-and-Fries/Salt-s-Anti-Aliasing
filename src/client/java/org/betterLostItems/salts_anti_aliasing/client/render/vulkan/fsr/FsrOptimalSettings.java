package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

import org.betterLostItems.salts_anti_aliasing.client.config.FsrQualityPreset;

/**
 * Render sizing selected for the requested AMD FSR quality mode.
 */
public record FsrOptimalSettings(
        int renderWidth,
        int renderHeight,
        int outputWidth,
        int outputHeight,
        int jitterPhaseCount
) {
    public static FsrOptimalSettings fallback(FsrQualityPreset preset, int outputWidth, int outputHeight) {
        FsrQualityPreset safePreset = FsrQualityPreset.clamp(preset);
        float scale = safePreset.scaleFactor();
        return new FsrOptimalSettings(
                Math.max(1, Math.round(outputWidth * scale)),
                Math.max(1, Math.round(outputHeight * scale)),
                Math.max(1, outputWidth),
                Math.max(1, outputHeight),
                safePreset.jitterPhaseCount()
        );
    }
}
