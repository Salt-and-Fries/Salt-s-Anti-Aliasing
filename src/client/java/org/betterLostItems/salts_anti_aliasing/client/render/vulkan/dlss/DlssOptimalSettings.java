package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

/**
 * Render sizing returned by Streamline for the selected DLSS quality mode.
 */
public record DlssOptimalSettings(
        int renderWidth,
        int renderHeight,
        int minRenderWidth,
        int minRenderHeight,
        int maxRenderWidth,
        int maxRenderHeight,
        float sharpness
) {
    public static DlssOptimalSettings fallback(int outputWidth, int outputHeight) {
        int renderWidth = Math.max(1, Math.round(outputWidth * 0.5f));
        int renderHeight = Math.max(1, Math.round(outputHeight * 0.5f));
        return new DlssOptimalSettings(
                renderWidth,
                renderHeight,
                renderWidth,
                renderHeight,
                outputWidth,
                outputHeight,
                0.0f
        );
    }
}
