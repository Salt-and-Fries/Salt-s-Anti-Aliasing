package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

/**
 * Implemented on Minecraft's physical-device wrapper by the queue-reservation mixin.
 */
public interface VulkanFrameGenerationQueueAccess {
    VulkanFrameGenerationQueuePlanner.Plan saltsAntiAliasing$frameGenerationQueuePlan();
}
