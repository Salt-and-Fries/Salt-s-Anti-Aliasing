package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.vulkan.VulkanDevice;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.VulkanQueue;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;

/**
 * Captures Vulkan handles exposed during Minecraft's Vulkan device creation.
 */
public final class VulkanNativeDeviceRegistry {
    private static volatile VulkanNativeDeviceInfo latest = new VulkanNativeDeviceInfo(
            0L,
            0L,
            0L,
            0L,
            -1,
            0L,
            -1,
            0L,
            -1
    );

    private VulkanNativeDeviceRegistry() {
    }

    public static void capture(VulkanDevice device, VulkanPhysicalDevice physicalDevice) {
        if (device == null || physicalDevice == null) {
            return;
        }

        try {
            VulkanQueue graphicsQueue = device.graphicsQueue();
            VulkanQueue computeQueue = device.computeQueue();
            VulkanQueue transferQueue = device.transferQueue();
            latest = new VulkanNativeDeviceInfo(
                    device.instance().vkInstance().address(),
                    physicalDevice.vkPhysicalDevice().address(),
                    device.vkDevice().address(),
                    graphicsQueue.vkQueue().address(),
                    graphicsQueue.queueFamilyIndex(),
                    computeQueue.vkQueue().address(),
                    computeQueue.queueFamilyIndex(),
                    transferQueue.vkQueue().address(),
                    transferQueue.queueFamilyIndex()
            );
            FsrRuntime.instance().onVulkanDeviceReady(latest);
            RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
            if (runtime != null) {
                runtime.requestPipelineRebuildWhenBackendReady();
            }
        } catch (RuntimeException exception) {
            SaltsAntiAliasing.LOGGER.warn("Failed to capture Vulkan handles for native upscalers", exception);
        }
    }

    public static VulkanNativeDeviceInfo latest() {
        return latest;
    }
}
