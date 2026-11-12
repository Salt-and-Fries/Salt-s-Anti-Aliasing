package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.xess;

public record XessOptimalSettings(int renderWidth, int renderHeight) {
    public XessOptimalSettings {
        if (renderWidth < 1 || renderHeight < 1) throw new IllegalArgumentException("Invalid XeSS input resolution");
    }
    /** Intel recommends at least eight samples per output/input pixel ratio. */
    public int jitterPhaseCount(int outputWidth, int outputHeight) {
        return (int) Math.max(8, Math.ceil(8.0 * outputWidth * outputHeight / ((double) renderWidth * renderHeight)));
    }
}
