package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.backend.vulkan.VulkanDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanPhysicalDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanQueue;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;
import org.lwjgl.vulkan.VK10;

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
            var properties = physicalDevice.vkPhysicalDeviceProperties();
            var limits = properties.limits();
            MetricsDiagnostics.info(
                    "vulkan.device",
                    "native_device_captured",
                    "deviceName", physicalDevice.deviceName(),
                    "vendorName", physicalDevice.vendorName(),
                    "driverInfo", physicalDevice.driverInfo(),
                    "deviceType", physicalDevice.deviceType(),
                    "apiVersion", version(properties.apiVersion()),
                    "apiVersionRaw", properties.apiVersion(),
                    "driverVersionRaw", properties.driverVersion(),
                    "vendorId", properties.vendorID(),
                    "deviceId", properties.deviceID(),
                    "maxImageDimension2D", limits.maxImageDimension2D(),
                    "maxFramebufferWidth", limits.maxFramebufferWidth(),
                    "maxFramebufferHeight", limits.maxFramebufferHeight(),
                    "maxFramebufferLayers", limits.maxFramebufferLayers(),
                    "maxColorAttachments", limits.maxColorAttachments(),
                    "framebufferColorSampleMask", limits.framebufferColorSampleCounts(),
                    "framebufferDepthSampleMask", limits.framebufferDepthSampleCounts(),
                    "framebufferStencilSampleMask", limits.framebufferStencilSampleCounts(),
                    "sampledImageColorSampleMask", limits.sampledImageColorSampleCounts(),
                    "sampledImageDepthSampleMask", limits.sampledImageDepthSampleCounts(),
                    "sampledImageIntegerSampleMask", limits.sampledImageIntegerSampleCounts(),
                    "maxMemoryAllocationCount", limits.maxMemoryAllocationCount(),
                    "bufferImageGranularity", limits.bufferImageGranularity(),
                    "graphicsQueueFamily", latest.graphicsQueueFamily(),
                    "frameGenerationAsyncComputeQueueFamily", latest.frameGenerationAsyncComputeQueueFamily(),
                    "frameGenerationPresentQueueFamily", latest.frameGenerationPresentQueueFamily(),
                    "frameGenerationImageAcquireQueueFamily", latest.frameGenerationImageAcquireQueueFamily(),
                    "coreHandlesComplete", latest.complete(),
                    "frameGenerationQueuesComplete", latest.frameGenerationQueuesComplete(),
                    "instanceHandle", hex(latest.instance()),
                    "physicalDeviceHandle", hex(latest.physicalDevice()),
                    "deviceHandle", hex(latest.device()),
                    "graphicsQueueHandle", hex(latest.graphicsQueue())
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
                MetricsDiagnostics.warn(
                        "vulkan.device",
                        "frame_generation_queues_unavailable",
                        "deviceName", physicalDevice.deviceName(),
                        "graphicsQueueFamily", latest.graphicsQueueFamily(),
                        "asyncComputeQueueFamily", latest.frameGenerationAsyncComputeQueueFamily(),
                        "presentQueueFamily", latest.frameGenerationPresentQueueFamily(),
                        "imageAcquireQueueFamily", latest.frameGenerationImageAcquireQueueFamily(),
                        "fsrUpscalingStillAvailable", true
                );
            }
            FsrRuntime.instance().onVulkanDeviceReady(latest);
            RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
            if (runtime != null) {
                runtime.requestPipelineRebuildWhenBackendReady();
            }
        } catch (RuntimeException exception) {
            SaltsAntiAliasing.LOGGER.warn("Failed to capture Vulkan handles for native upscalers", exception);
            MetricsDiagnostics.error(
                    "vulkan.device",
                    "native_device_capture_failed",
                    exception,
                    "device", device,
                    "physicalDevice", physicalDevice
            );
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

    private static String hex(long value) {
        return value == 0L ? "0x0" : "0x" + Long.toUnsignedString(value, 16);
    }

    private static String version(int value) {
        return VK10.VK_VERSION_MAJOR(value) + "."
                + VK10.VK_VERSION_MINOR(value) + "."
                + VK10.VK_VERSION_PATCH(value);
    }
}
