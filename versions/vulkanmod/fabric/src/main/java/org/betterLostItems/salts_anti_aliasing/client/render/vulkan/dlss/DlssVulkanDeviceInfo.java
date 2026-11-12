package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

/**
 * Native Vulkan handles needed by Streamline.
 */
public record DlssVulkanDeviceInfo(
        long instance,
        long physicalDevice,
        long device,
        long graphicsQueue,
        int graphicsQueueFamily
) {
    public boolean complete() {
        return instance != 0L
                && physicalDevice != 0L
                && device != 0L
                && graphicsQueue != 0L
                && graphicsQueueFamily >= 0;
    }
}
