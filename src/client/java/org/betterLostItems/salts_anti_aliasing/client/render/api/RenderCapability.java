package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Documents render capability behavior for Salt's Anti Aliasing. Backend-neutral rendering API shared
 * by the planner and backend implementations.
 */
public enum RenderCapability {
    POST_PROCESSING,
    SHARPENING,
    MULTISAMPLE_AA,
    INTERNAL_RESOLUTION,
    SPATIAL_UPSCALING,
    TEMPORAL_AA,
    VENDOR_UPSCALING
}
