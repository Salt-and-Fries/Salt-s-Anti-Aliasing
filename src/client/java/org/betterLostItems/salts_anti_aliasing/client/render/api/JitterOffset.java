package org.betterLostItems.salts_anti_aliasing.client.render.api;

/**
 * Immutable value object carrying jitter offset data between render-planning and runtime code.
 * Backend-neutral render API code shared by Vulkan runtime code and pipeline planning.
 */
public record JitterOffset(float x, float y) {
    /**
     * Handles none as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return zero jitter offset for frames that should not be temporally shifted
     */
    public static JitterOffset none() {
        return new JitterOffset(0.0f, 0.0f);
    }
}
