package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
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

        int selectedSamples = VulkanMsaaCompatibility.bestSupportedSampleCount(supportedSampleMask, requestedSamples);
        MetricsDiagnostics.infoOnce(
                "vulkan.msaa.scene-capabilities."
                        + device.vkDevice().getPhysicalDevice().address() + "."
                        + colorFormat + "." + useDepth + "." + requestedSamples,
                "vulkan.msaa",
                "scene_sample_capabilities",
                "colorFormat", colorFormat,
                "depthFormat", useDepth ? GpuFormat.D32_FLOAT : "none",
                "useDepth", useDepth,
                "requestedSamples", requestedSamples,
                "combinedSampleMask", supportedSampleMask,
                "combinedSupportedSamples", describeSampleMask(supportedSampleMask),
                "selectedSamples", selectedSamples
        );
        return selectedSamples;
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
            int sampleMask = result == VK12.VK_SUCCESS
                    ? properties.sampleCounts()
                    : VK12.VK_SAMPLE_COUNT_1_BIT;
            MetricsDiagnostics.infoOnce(
                    "vulkan.msaa.image-capabilities."
                            + physicalDevice.address() + "." + format + "." + usage,
                    "vulkan.msaa",
                    "image_format_sample_capabilities",
                    "format", format,
                    "vkFormat", VulkanConst.toVk(format),
                    "imageType", VK12.VK_IMAGE_TYPE_2D,
                    "tiling", VK12.VK_IMAGE_TILING_OPTIMAL,
                    "usage", usage,
                    "usageHex", "0x" + Integer.toHexString(usage),
                    "queryResult", result,
                    "querySucceeded", result == VK12.VK_SUCCESS,
                    "sampleMask", sampleMask,
                    "sampleMaskHex", "0x" + Integer.toHexString(sampleMask),
                    "supportedSamples", describeSampleMask(sampleMask),
                    "maximumExtent", properties.maxExtent().width() + "x"
                            + properties.maxExtent().height() + "x" + properties.maxExtent().depth(),
                    "maximumMipLevels", properties.maxMipLevels(),
                    "maximumArrayLayers", properties.maxArrayLayers(),
                    "maximumResourceSizeBytes", properties.maxResourceSize()
            );
            return sampleMask;
        }
    }

    private static String describeSampleMask(int mask) {
        StringBuilder description = new StringBuilder();
        for (int samples = 1; samples <= 64; samples <<= 1) {
            if ((mask & samples) != 0) {
                if (!description.isEmpty()) {
                    description.append(',');
                }
                description.append(samples).append('x');
            }
        }
        return description.isEmpty() ? "none" : description.toString();
    }
}
