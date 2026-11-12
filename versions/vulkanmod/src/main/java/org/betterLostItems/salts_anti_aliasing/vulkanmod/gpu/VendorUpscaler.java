package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.lwjgl.vulkan.VkCommandBuffer;

/** Native SDK boundary; implementation lives with Fabric's runtime discovery and JNI bridges. */
public interface VendorUpscaler {
    record Frame(VulkanContext context, VkCommandBuffer commands, AntiAliasingConfig config, TemporalView camera,
                 VulkanImageResource color, VulkanImageResource depth, VulkanImageResource motion,
                 VulkanImageResource output, VulkanImageResource opaque, VulkanImageResource reactive,
                 VulkanImageResource transparency, VulkanImageResource hudless,
                 VulkanImageResource presentationDepth, VulkanImageResource presentationMotion) {}
    void evaluate(Frame frame);
    void retire();
}
