package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

/**
 * Exposes the Vulkan image sample count tracked by the texture-construction mixin.
 */
public interface VulkanSampledTexture {
    int saltsAntiAliasing$sampleCount();
}
