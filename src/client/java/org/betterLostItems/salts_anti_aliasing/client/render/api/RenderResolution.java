package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Documents render resolution behavior for Salt's Anti Aliasing. Backend-neutral rendering API shared
 * by the planner and backend implementations.
 */
public record RenderResolution(int width, int height) {
    public RenderResolution {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Render resolution must be positive");
        }
    }

    /**
     * Coordinates scaled within the anti-aliasing render, configuration, or compatibility flow.
     * @param scale scale supplied by Minecraft or the caller
     * @return scaled value produced or selected by this code path
     */
    public RenderResolution scaled(float scale) {
        int scaledWidth = Math.max(1, Math.round(width * scale));
        int scaledHeight = Math.max(1, Math.round(height * scale));
        return new RenderResolution(scaledWidth, scaledHeight);
    }
}
