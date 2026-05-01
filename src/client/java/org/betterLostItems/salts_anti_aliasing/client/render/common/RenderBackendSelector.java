package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.RenderBackendPreference;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackend;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlRenderBackend;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanRenderBackend;

/**
 * Documents render backend selector behavior for Salt's Anti Aliasing. Shared render orchestration
 * code independent of a specific graphics backend.
 */
public final class RenderBackendSelector {
    /**
     * Coordinates select within the anti-aliasing render, configuration, or compatibility flow.
     * @param config configuration being read, normalized, or applied
     * @return select value produced or selected by this code path
     */
    public RenderBackend select(AntiAliasingConfig config) {
        RenderBackend preferred = switch (config.preferredBackend) {
            case VULKAN -> config.allowExperimentalVulkan ? new VulkanRenderBackend() : new OpenGlRenderBackend();
            case OPENGL, AUTO -> new OpenGlRenderBackend();
        };

        if (preferred.isAvailable()) {
            return preferred;
        }

        if (config.preferredBackend == RenderBackendPreference.VULKAN) {
            return new OpenGlRenderBackend();
        }

        return preferred;
    }
}
