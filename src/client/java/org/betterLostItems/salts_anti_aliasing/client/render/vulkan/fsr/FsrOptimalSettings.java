package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

import org.betterLostItems.salts_anti_aliasing.client.config.FsrQualityPreset;

/**
 * Render sizing selected for the requested AMD FSR quality mode.
 */
public record FsrOptimalSettings(
        int renderWidth,
        int renderHeight,
        int outputWidth,
        int outputHeight
) {
    public static FsrOptimalSettings fallback(FsrQualityPreset preset, int outputWidth, int outputHeight) {
        float scale = FsrQualityPreset.clamp(preset).scaleFactor();
        return new FsrOptimalSettings(
                Math.max(1, Math.round(outputWidth * scale)),
                Math.max(1, Math.round(outputHeight * scale)),
                Math.max(1, outputWidth),
                Math.max(1, outputHeight)
        );
    }
}
