package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.VulkanQueue;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;

/**
 * Captures Vulkan handles exposed during Minecraft's Vulkan device creation.
 */
public final class DlssVulkanDeviceRegistry {
    private static volatile DlssVulkanDeviceInfo latest = new DlssVulkanDeviceInfo(0L, 0L, 0L, 0L, -1);

    private DlssVulkanDeviceRegistry() {
    }

    public static void capture(VulkanDevice device, VulkanPhysicalDevice physicalDevice) {
        if (device == null || physicalDevice == null) {
            return;
        }

        try {
            VulkanQueue graphicsQueue = device.graphicsQueue();
            latest = new DlssVulkanDeviceInfo(
                    device.instance().vkInstance().address(),
                    physicalDevice.vkPhysicalDevice().address(),
                    device.vkDevice().address(),
                    graphicsQueue.vkQueue().address(),
                    graphicsQueue.queueFamilyIndex()
            );
            DlssRuntime.instance().onVulkanDeviceReady(latest);
        } catch (RuntimeException exception) {
            SaltsAntiAliasing.LOGGER.warn("Failed to capture Vulkan handles for DLSS", exception);
        }
    }

    public static DlssVulkanDeviceInfo latest() {
        return latest;
    }
}
