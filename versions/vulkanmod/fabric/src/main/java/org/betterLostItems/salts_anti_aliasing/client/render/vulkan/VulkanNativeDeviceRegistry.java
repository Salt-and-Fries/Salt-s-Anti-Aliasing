package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.xess.XessRuntime;

/** Raw handles from VulkanMod; no dependency on Minecraft's later Vulkan implementation. */
public final class VulkanNativeDeviceRegistry {
    private static volatile VulkanNativeDeviceInfo latest;
    private VulkanNativeDeviceRegistry() {}
    public static VulkanNativeDeviceInfo latest() { return latest; }
    public static void capture(VulkanNativeDeviceInfo info) {
        latest = info;
        FsrRuntime.instance().onVulkanDeviceReady(info);
        XessRuntime.instance().onVulkanDeviceReady(info);
    }
}
