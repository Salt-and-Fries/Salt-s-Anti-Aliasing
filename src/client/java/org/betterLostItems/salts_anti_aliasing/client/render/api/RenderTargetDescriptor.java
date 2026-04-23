package org.betterLostItems.salts_anti_aliasing.client.render.api;

public record RenderTargetDescriptor(
        String id,
        RenderTargetType type,
        TextureFormat format,
        RenderTargetSizing sizing,
        float scale,
        boolean persistentAcrossFrames
) {
    public RenderTargetDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Render target id cannot be blank");
        }
        if (scale <= 0.0f || scale > 2.0f) {
            throw new IllegalArgumentException("Render target scale must be between 0 and 2");
        }
    }
}
