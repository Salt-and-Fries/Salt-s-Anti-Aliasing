package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

/**
 * Native Vulkan handles shared by optional SDK integrations.
 */
public record VulkanNativeDeviceInfo(
        long instance,
        long physicalDevice,
        long device,
        long graphicsQueue,
        int graphicsQueueFamily,
        long frameGenerationAsyncComputeQueue,
        int frameGenerationAsyncComputeQueueFamily,
        long frameGenerationPresentQueue,
        int frameGenerationPresentQueueFamily,
        long frameGenerationImageAcquireQueue,
        int frameGenerationImageAcquireQueueFamily
) {
    /**
     * Reports whether the core Vulkan handles needed by the upscaling integrations are present.
     * Frame generation is intentionally checked separately so FSR2/FSR3 upscaling remains
     * available on devices that cannot spare the SDK's three private queues.
     */
    public boolean complete() {
        return instance != 0L
                && physicalDevice != 0L
                && device != 0L
                && graphicsQueue != 0L
                && graphicsQueueFamily >= 0;
    }

    /**
     * FidelityFX 1.1.4 requires the game, async-compute, present, and image-acquire queues to be
     * four distinct handles. Sharing any of them is rejected by the replacement swapchain.
     */
    public boolean frameGenerationQueuesComplete() {
        return complete()
                && frameGenerationAsyncComputeQueue != 0L
                && frameGenerationAsyncComputeQueueFamily >= 0
                && frameGenerationPresentQueue != 0L
                && frameGenerationPresentQueueFamily >= 0
                && frameGenerationImageAcquireQueue != 0L
                && frameGenerationImageAcquireQueueFamily >= 0
                && graphicsQueue != frameGenerationAsyncComputeQueue
                && graphicsQueue != frameGenerationPresentQueue
                && graphicsQueue != frameGenerationImageAcquireQueue
                && frameGenerationAsyncComputeQueue != frameGenerationPresentQueue
                && frameGenerationAsyncComputeQueue != frameGenerationImageAcquireQueue
                && frameGenerationPresentQueue != frameGenerationImageAcquireQueue;
    }
}
