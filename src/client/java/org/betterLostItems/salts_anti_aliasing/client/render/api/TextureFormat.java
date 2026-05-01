package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Enumerates texture format values used by the anti-aliasing runtime and configuration UI. Backend-
 * neutral render API code shared by OpenGL, Vulkan placeholders, and pipeline planning.
 */
public enum TextureFormat {
    RGBA8,
    RGBA16F,
    R8,
    RG8,
    DEPTH24_STENCIL8
}
