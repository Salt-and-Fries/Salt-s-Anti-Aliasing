package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.vulkan.VulkanConst;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import org.betterLostItems.salts_anti_aliasing.client.config.MsaaSampleLevel;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkImageFormatProperties;
import org.lwjgl.vulkan.VkPhysicalDevice;

/**
 * Queries the actual Vulkan image-format sample masks used by Minecraft render targets.
 */
public final class VulkanMsaaCapabilities {
    private static final int RENDER_TARGET_TEXTURE_USAGE = 15;

    private VulkanMsaaCapabilities() {
    }

    public static int bestSupportedSceneSamples(
            VulkanDevice device,
            GpuFormat colorFormat,
            boolean useDepth,
            int requestedSamples
    ) {
        int supportedSampleMask = imageSampleMask(device, colorFormat);
        if (useDepth) {
            supportedSampleMask &= imageSampleMask(device, GpuFormat.D32_FLOAT);
        }

        int requested = Math.max(1, requestedSamples);
        for (MsaaSampleLevel level : MsaaSampleLevel.valuesDescending()) {
            int samples = level.samples();
            if (samples <= requested && (supportedSampleMask & samples) != 0) {
                return samples;
            }
        }

        return 1;
    }

    private static int imageSampleMask(VulkanDevice device, GpuFormat format) {
        VkPhysicalDevice physicalDevice = device.vkDevice().getPhysicalDevice();
        int usage = VulkanConst.textureUsageToVk(RENDER_TARGET_TEXTURE_USAGE, format);

        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkImageFormatProperties properties = VkImageFormatProperties.calloc(stack);
            int result = VK12.vkGetPhysicalDeviceImageFormatProperties(
                    physicalDevice,
                    VulkanConst.toVk(format),
                    VK12.VK_IMAGE_TYPE_2D,
                    VK12.VK_IMAGE_TILING_OPTIMAL,
                    usage,
                    0,
                    properties
            );
            return result == VK12.VK_SUCCESS ? properties.sampleCounts() : VK12.VK_SAMPLE_COUNT_1_BIT;
        }
    }
}
