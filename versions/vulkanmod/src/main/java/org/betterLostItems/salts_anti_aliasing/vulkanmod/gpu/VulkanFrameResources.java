package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import java.util.ArrayList;
import java.util.List;

/** One resource arena per in-flight VulkanMod frame, recycled only after its fence has signalled. */
public final class VulkanFrameResources implements AutoCloseable {
    private record Slot(VulkanGraphicsPass pass, VulkanGraphicsPass.DrawResources resources) {}
    private final List<Slot> slots = new ArrayList<>();
    private final List<AutoCloseable> uploads = new ArrayList<>();
    private final java.util.Map<Long, Long> depthViews = new java.util.HashMap<>();
    private int cursor;

    public void beginAfterFence() { releaseUploads(); cursor = 0; }

    public void keepUntilFence(AutoCloseable resource) { uploads.add(resource); }

    private void releaseUploads() {
        for (var resource : uploads) {
            try { resource.close(); }
            catch (Exception e) { throw new IllegalStateException("Cannot release completed upload", e); }
        }
        uploads.clear();
        depthViews.clear();
    }

    long sampledView(VulkanContext context, VulkanImageResource image) {
        if ((image.aspect & org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_STENCIL_BIT) == 0) return image.view;
        return depthViews.computeIfAbsent(image.image, key -> {
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                var info = org.lwjgl.vulkan.VkImageViewCreateInfo.calloc(stack).sType$Default()
                        .image(image.image).viewType(org.lwjgl.vulkan.VK10.VK_IMAGE_VIEW_TYPE_2D).format(image.format);
                info.subresourceRange().aspectMask(org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_DEPTH_BIT)
                        .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
                var handle = stack.mallocLong(1);
                VulkanContext.check(org.lwjgl.vulkan.VK10.vkCreateImageView(context.device(), info, null, handle), "Create depth sampling view");
                long view = handle.get(0);
                keepUntilFence(() -> org.lwjgl.vulkan.VK10.vkDestroyImageView(context.device(), view, null));
                return view;
            }
        });
    }

    VulkanGraphicsPass.DrawResources next(VulkanGraphicsPass pass) {
        int index = cursor++;
        if (index == slots.size()) slots.add(new Slot(pass, pass.newDrawResources()));
        else if (slots.get(index).pass != pass) {
            slots.get(index).resources.close();
            slots.set(index, new Slot(pass, pass.newDrawResources()));
        }
        return slots.get(index).resources;
    }

    @Override public void close() {
        releaseUploads();
        for (var slot : slots) slot.resources.close();
        slots.clear(); cursor = 0;
    }
}
