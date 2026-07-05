package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Enumerates render capability values used by the anti-aliasing runtime and configuration UI.
 * Backend-neutral render API code shared by Vulkan runtime code and pipeline planning.
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
