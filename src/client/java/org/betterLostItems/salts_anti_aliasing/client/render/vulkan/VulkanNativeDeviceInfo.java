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
        long computeQueue,
        int computeQueueFamily,
        long transferQueue,
        int transferQueueFamily
) {
    public boolean complete() {
        return instance != 0L
                && physicalDevice != 0L
                && device != 0L
                && graphicsQueue != 0L
                && graphicsQueueFamily >= 0
                && computeQueue != 0L
                && computeQueueFamily >= 0
                && transferQueue != 0L
                && transferQueueFamily >= 0;
    }
}
