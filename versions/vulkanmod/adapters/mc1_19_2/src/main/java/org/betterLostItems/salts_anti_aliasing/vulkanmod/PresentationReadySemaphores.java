package org.betterLostItems.salts_anti_aliasing.vulkanmod;

import org.lwjgl.vulkan.*;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;

/** Present waits complete when their swapchain image is acquired again, independently of frame slots. */
public final class PresentationReadySemaphores {
    private record Image(long swapchain, int index) {}
    private static final Map<Image, Long> semaphores = new HashMap<>();
    private static Image current;
    private static VkDevice device;
    private PresentationReadySemaphores() {}
    public static void acquired(VkDevice owner, long swapchain, int index) {
        device = owner; current = new Image(swapchain, index);
    }
    public static long current() {
        if (current == null) throw new IllegalStateException("No acquired image for presentation");
        return semaphores.computeIfAbsent(current, key -> {
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                var info = VkSemaphoreCreateInfo.calloc(stack).sType$Default(); var handle = stack.mallocLong(1);
                org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext.check(
                        vkCreateSemaphore(device, info, null, handle), "Create acquired-image presentation semaphore");
                return handle.get(0);
            }
        });
    }
    public static void destroyed(VkDevice owner, long swapchain) {
        var iterator = semaphores.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            if (entry.getKey().swapchain() == swapchain) {
                vkDestroySemaphore(owner, entry.getValue(), null); iterator.remove();
            }
        }
        if (current != null && current.swapchain() == swapchain) current = null;
    }
}
