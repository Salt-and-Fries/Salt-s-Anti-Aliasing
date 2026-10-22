package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import org.lwjgl.vulkan.VK12;

/**
 * Adds a mod-private texture usage flag for Vulkan storage images.
 */
public final class VulkanStorageTextureUsage {
    public static final int USAGE_STORAGE = 1 << 29;

    private VulkanStorageTextureUsage() {
    }

    public static int toVulkanUsage(int usage, GpuFormat format) {
        int sanitizedUsage = usage & ~USAGE_STORAGE;
        int vulkanUsage = VulkanConst.textureUsageToVk(sanitizedUsage, format);
        if ((usage & USAGE_STORAGE) != 0) {
            vulkanUsage |= VK12.VK_IMAGE_USAGE_STORAGE_BIT;
        }
        return vulkanUsage;
    }
}
