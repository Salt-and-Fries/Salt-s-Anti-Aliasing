package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

import static org.lwjgl.vulkan.KHRSynchronization2.VK_ACCESS_2_SHADER_READ_BIT_KHR;
import static org.lwjgl.vulkan.VK10.VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL;

/**
 * Chooses the final Vulkan state of an image handed to a swapchain presenter.
 *
 * <p>A regular Vulkan swapchain consumes {@code PRESENT_SRC_KHR}. FidelityFX's replacement
 * swapchain instead samples the game backbuffer while constructing interpolated frames and
 * therefore requires a shader-readable image.</p>
 */
public final class FrameGenerationSwapchainImagePolicy {
    private FrameGenerationSwapchainImagePolicy() {
    }

    public static int finalLayout(boolean frameGenerationOwnsSwapchain, int regularLayout) {
        return frameGenerationOwnsSwapchain ? VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL : regularLayout;
    }

    public static long finalAccessMask(boolean frameGenerationOwnsSwapchain, long regularAccessMask) {
        return frameGenerationOwnsSwapchain ? VK_ACCESS_2_SHADER_READ_BIT_KHR : regularAccessMask;
    }
}
