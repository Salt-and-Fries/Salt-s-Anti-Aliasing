package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

/** DLSS shares the same borrowed raw Vulkan handles with every legacy adapter. */
public final class DlssVulkanDeviceRegistry {
    private static volatile DlssVulkanDeviceInfo latest;
    private DlssVulkanDeviceRegistry() {}
    public static DlssVulkanDeviceInfo latest() { return latest; }
    public static void capture(DlssVulkanDeviceInfo info) {
        latest = info;
        DlssRuntime.instance().onVulkanDeviceReady(info);
    }
}
