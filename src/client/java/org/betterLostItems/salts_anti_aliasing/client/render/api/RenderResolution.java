package org.betterLostItems.salts_anti_aliasing.client.render.api;

public record RenderResolution(int width, int height) {
    public RenderResolution {
        if (width < 1 || height < 1) {
            throw new IllegalArgumentException("Render resolution must be positive");
        }
    }

    public RenderResolution scaled(float scale) {
        int scaledWidth = Math.max(1, Math.round(width * scale));
        int scaledHeight = Math.max(1, Math.round(height * scale));
        return new RenderResolution(scaledWidth, scaledHeight);
    }
}
