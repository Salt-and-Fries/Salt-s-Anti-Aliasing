package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.RenderBackendPreference;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackend;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlRenderBackend;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanRenderBackend;

/**
 * Chooses the safest backend for the current config and installed-mod environment before the render
 * runtime starts building passes.
 */
public final class RenderBackendSelector {
    /**
     * Handles select as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param config configuration object being normalized, copied, or committed
     * @return backend selected for the current configuration and compatibility constraints
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
