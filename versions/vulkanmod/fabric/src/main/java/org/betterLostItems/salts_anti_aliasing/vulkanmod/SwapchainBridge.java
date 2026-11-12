package org.betterLostItems.salts_anti_aliasing.vulkanmod;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.util.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRSurface.*;

/** Records actual swapchain image usage, including VulkanMod's externally wrapped images. */
public final class SwapchainBridge {
    private record Images(int usage, Set<Long> images) {}
    private static final Map<Long, Images> swapchains = new HashMap<>();
    private static final Map<Long, Integer> usages = new HashMap<>();
    private static final Map<Long, Integer> retired = new HashMap<>();
    private SwapchainBridge() {}
    public static int usage(long image) {
        var usage = usages.get(image);
        if (usage == null) throw new IllegalStateException("Unknown external swapchain image usage");
        return usage;
    }
    public static int create(VkDevice device, VkSwapchainCreateInfoKHR info, VkAllocationCallbacks allocator, LongBuffer result) {
        var fsr = FsrRuntime.instance();
        long old = info.oldSwapchain();
        if (old != 0 && (fsr.isFrameGenerationSwapchainRequested() || fsr.isFrameGenerationSwapchainOwned())) {
            // Older VulkanMod releases create the replacement before destroying the old
            // swapchain. The SDK owns its replacement lifetime and requires a free window.
            // Re-creation already waits for all queues; consume the old handle once here.
            destroy(device, old, allocator);
            retired.merge(old, 1, Integer::sum);
            info.oldSwapchain(0);
        }
        try (var stack = MemoryStack.stackPush()) {
            var surface = VkSurfaceCapabilitiesKHR.calloc(stack);
            VulkanContextCheck.check(vkGetPhysicalDeviceSurfaceCapabilitiesKHR(device.getPhysicalDevice(), info.surface(), surface));
            int needed = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT;
            if ((surface.supportedUsageFlags() & needed) != needed)
                throw new IllegalStateException("Surface cannot provide sampled and transferable world attachments");
            info.imageUsage(info.imageUsage() | needed);
        }
        int status = FsrRuntime.instance().createFrameGenerationSwapchain(device, info, allocator, result);
        if (status == VK_SUCCESS) swapchains.put(result.get(result.position()), new Images(info.imageUsage(), new HashSet<>()));
        return status;
    }
    public static int images(VkDevice device, long swapchain, IntBuffer count, LongBuffer images) {
        int status = FsrRuntime.instance().getFrameGenerationSwapchainImages(device, swapchain, count, images);
        if (images != null && (status == VK_SUCCESS || status == VK_INCOMPLETE)) {
            var record = swapchains.get(swapchain);
            if (record == null) throw new IllegalStateException("Swapchain images queried before creation was tracked");
            for (int i = 0; i < count.get(count.position()); i++) {
                long image = images.get(images.position() + i);
                record.images().add(image); usages.put(image, record.usage());
            }
        }
        return status;
    }
    public static void destroy(VkDevice device, long swapchain, VkAllocationCallbacks allocator) {
        var pending = retired.get(swapchain);
        if (pending != null) {
            if (pending == 1) retired.remove(swapchain); else retired.put(swapchain, pending - 1);
            return;
        }
        FsrRuntime.instance().destroyFrameGenerationSwapchain(device, swapchain, allocator);
        var record = swapchains.remove(swapchain);
        if (record != null) for (long image : record.images()) usages.remove(image);
    }
    private static final class VulkanContextCheck {
        static void check(int status) { if (status != VK_SUCCESS) throw new IllegalStateException("Surface capabilities query failed: " + status); }
    }
}
