package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Immutable value object carrying render resolution data between render-planning and runtime code.
 * Backend-neutral render API code shared by OpenGL, Vulkan placeholders, and pipeline planning.
 */
public record RenderResolution(int width, int height) {
    public RenderResolution {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Render resolution must be positive");
        }
    }

    /**
     * Handles scaled as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param scale scale value supplied by the caller or Minecraft callback
     * @return scaled produced by this helper
     */
    public RenderResolution scaled(float scale) {
        int scaledWidth = Math.max(1, Math.round(width * scale));
        int scaledHeight = Math.max(1, Math.round(height * scale));
        return new RenderResolution(scaledWidth, scaledHeight);
    }
}
