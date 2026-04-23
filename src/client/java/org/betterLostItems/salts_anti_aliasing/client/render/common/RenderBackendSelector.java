package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.RenderBackendPreference;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackend;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlRenderBackend;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanRenderBackend;

public final class RenderBackendSelector {
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
