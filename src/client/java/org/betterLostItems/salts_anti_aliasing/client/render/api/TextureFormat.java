package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Documents texture format behavior for Salt's Anti Aliasing. Backend-neutral rendering API shared by
 * the planner and backend implementations.
 */
public enum TextureFormat {
    RGBA8,
    RGBA16F,
    R8,
    RG8,
    DEPTH24_STENCIL8
}
