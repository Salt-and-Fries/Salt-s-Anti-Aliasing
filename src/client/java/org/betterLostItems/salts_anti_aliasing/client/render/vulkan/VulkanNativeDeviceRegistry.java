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
            VulkanQueue frameGenerationAsyncComputeQueue = null;
            VulkanQueue frameGenerationPresentQueue = null;
            VulkanQueue frameGenerationImageAcquireQueue = null;

            Object physicalDeviceExtension = physicalDevice;
            if (physicalDeviceExtension instanceof VulkanFrameGenerationQueueAccess queueAccess) {
                VulkanFrameGenerationQueuePlanner.Plan plan = queueAccess.saltsAntiAliasing$frameGenerationQueuePlan();
                if (plan != null) {
                    frameGenerationAsyncComputeQueue = createQueue(device, plan.asyncCompute());
                    frameGenerationPresentQueue = createQueue(device, plan.present());
                    frameGenerationImageAcquireQueue = createQueue(device, plan.imageAcquire());
                }
            }

            latest = new VulkanNativeDeviceInfo(
                    device.instance().vkInstance().address(),
                    physicalDevice.vkPhysicalDevice().address(),
                    device.vkDevice().address(),
                    graphicsQueue.vkQueue().address(),
                    graphicsQueue.queueFamilyIndex(),
                    queueAddress(frameGenerationAsyncComputeQueue),
                    queueFamily(frameGenerationAsyncComputeQueue),
                    queueAddress(frameGenerationPresentQueue),
                    queueFamily(frameGenerationPresentQueue),
                    queueAddress(frameGenerationImageAcquireQueue),
                    queueFamily(frameGenerationImageAcquireQueue)
            );
            if (latest.frameGenerationQueuesComplete()) {
                SaltsAntiAliasing.LOGGER.info(
                        "Reserved private FidelityFX queues: async compute family {}, present family {}, image acquire family {}",
                        latest.frameGenerationAsyncComputeQueueFamily(),
                        latest.frameGenerationPresentQueueFamily(),
                        latest.frameGenerationImageAcquireQueueFamily()
                );
            } else {
                SaltsAntiAliasing.LOGGER.warn(
                        "Dedicated Vulkan queues for FSR3 frame generation are unavailable; FSR upscaling remains enabled"
                );
            }
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

    private static VulkanQueue createQueue(
            VulkanDevice device,
            VulkanFrameGenerationQueuePlanner.QueueRef queue
    ) {
        return new VulkanQueue(device, queue.family(), queue.index());
    }

    private static long queueAddress(VulkanQueue queue) {
        return queue == null ? 0L : queue.vkQueue().address();
    }

    private static int queueFamily(VulkanQueue queue) {
        return queue == null ? -1 : queue.queueFamilyIndex();
    }
}
