package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.lwjgl.vulkan.VkDevice;

/** Borrowed device/allocator: VulkanMod retains ownership of their lifetimes and submission. */
public record VulkanContext(VkDevice device, long allocator) {
    private static volatile Runnable idleBarrier = () -> {};

    public static void idleBarrier(Runnable barrier) {
        idleBarrier = java.util.Objects.requireNonNull(barrier);
    }
    public VulkanContext {
        if (device == null || allocator == 0) throw new IllegalArgumentException("Incomplete Vulkan context");
    }

    public static void check(int result, String operation) {
        if (result != 0) throw new IllegalStateException(operation + " failed with VkResult " + result);
    }
    public static int waitIdle(VkDevice device) {
        idleBarrier.run();
        return org.lwjgl.vulkan.VK10.vkDeviceWaitIdle(device);
    }
    public void debugName(int type, long object, String name) {
        if (!device.getPhysicalDevice().getInstance().getCapabilities().VK_EXT_debug_utils) return;
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var info = org.lwjgl.vulkan.VkDebugUtilsObjectNameInfoEXT.calloc(stack).sType$Default()
                    .objectType(type).objectHandle(object).pObjectName(stack.UTF8(name));
            check(org.lwjgl.vulkan.EXTDebugUtils.vkSetDebugUtilsObjectNameEXT(device, info), "Name GPU resource");
        }
    }
}
