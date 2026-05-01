package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Enumerates render target type values used by the anti-aliasing runtime and configuration UI.
 * Backend-neutral render API code shared by OpenGL, Vulkan placeholders, and pipeline planning.
 */
public enum RenderTargetType {
    SCENE_COLOR,
    SCENE_DEPTH,
    INTERMEDIATE_COLOR,
    HISTORY_COLOR,
    AUXILIARY
}
