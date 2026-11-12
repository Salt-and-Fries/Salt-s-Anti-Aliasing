package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Immutable value object carrying render target descriptor data between render-planning and runtime
 * code. Backend-neutral render API code shared by Vulkan runtime code and pipeline planning.
 */
public record RenderTargetDescriptor(
        String id,
        RenderTargetType type,
        TextureFormat format,
        RenderTargetSizing sizing,
        float scale,
        boolean persistentAcrossFrames
) {
    public static final float MAX_SCALE = 8.0f;

    public RenderTargetDescriptor {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Render target id cannot be blank");
        }
        if (scale <= 0.0f || scale > MAX_SCALE) {
            throw new IllegalArgumentException("Render target scale must be between 0 and " + MAX_SCALE);
        }
    }
}
