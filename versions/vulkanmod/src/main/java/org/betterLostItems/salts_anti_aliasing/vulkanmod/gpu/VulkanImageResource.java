package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.vulkan.*;

import java.util.function.IntConsumer;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

/** Owned offscreen allocation or borrowed VulkanMod attachment with synchronized layout tracking. */
public final class VulkanImageResource implements AutoCloseable {
    private final VulkanContext context;
    public final long image, view;
    public final int width, height, format, aspect, samples;
    public final int usage, mipLevels;
    private final long allocation;
    private final IntConsumer layoutChanged;
    private int layout;
    private boolean closed;
    private final long allocatedBytes;
    private static final java.util.concurrent.atomic.AtomicLong ALLOCATED = new java.util.concurrent.atomic.AtomicLong();
    public static long ownedBytes() { return ALLOCATED.get(); }

    private VulkanImageResource(VulkanContext context, long image, long view, long allocation,
                                int width, int height, int format, int aspect, int samples,
                                int layout, IntConsumer layoutChanged, int usage, int mipLevels) {
        this.context = context;
        this.image = image; this.view = view; this.allocation = allocation;
        this.width = width; this.height = height; this.format = format; this.aspect = aspect;
        this.samples = samples; this.layout = layout; this.layoutChanged = layoutChanged;
        this.usage = usage;
        this.mipLevels = mipLevels;
        if (allocation == 0) allocatedBytes = 0;
        else {
            try (var stack = MemoryStack.stackPush()) {
                var info = org.lwjgl.util.vma.VmaAllocationInfo.calloc(stack);
                vmaGetAllocationInfo(context.allocator(), allocation, info);
                allocatedBytes = info.size();
            }
            ALLOCATED.addAndGet(allocatedBytes);
        }
    }

    public static VulkanImageResource borrow(VulkanContext context, long image, long view,
                                             int width, int height, int format, int aspect,
                                             int samples, int layout, IntConsumer layoutChanged) {
        if (image == 0 || view == 0 || width < 1 || height < 1) throw new IllegalArgumentException("Invalid borrowed image");
        return new VulkanImageResource(context, image, view, 0, width, height, format, aspect,
                samples, layout, layoutChanged, VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                | VK_IMAGE_USAGE_TRANSFER_DST_BIT | ((aspect & VK_IMAGE_ASPECT_DEPTH_BIT) != 0
                ? VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT : VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT), 1);
    }

    public static VulkanImageResource borrow(VulkanContext context, long image, long view,
                                             int width, int height, int format, int aspect, int samples,
                                             int layout, IntConsumer layoutChanged, int usage) {
        return borrow(context, image, view, width, height, format, aspect, samples, layout, layoutChanged, usage, 1);
    }

    /** Atlas views expose all mip levels; their layout transition must cover the same range. */
    public static VulkanImageResource borrow(VulkanContext context, long image, long view,
                                             int width, int height, int format, int aspect, int samples,
                                             int layout, IntConsumer layoutChanged, int usage, int mipLevels) {
        if (image == 0 || view == 0 || width < 1 || height < 1 || usage == 0 || mipLevels < 1
                || mipLevels > 32 - Integer.numberOfLeadingZeros(Math.max(width, height)))
            throw new IllegalArgumentException("Invalid borrowed image or unknown usage");
        return new VulkanImageResource(context, image, view, 0, width, height, format, aspect,
                samples, layout, layoutChanged, usage, mipLevels);
    }

    public static VulkanImageResource create(VulkanContext context, int width, int height,
                                             int format, int aspect, int samples) {
        if (width < 1 || height < 1 || samples < 1 || samples > 64 || Integer.bitCount(samples) != 1)
            throw new IllegalArgumentException("Invalid image dimensions/sample count");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int usage = VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_TRANSFER_DST_BIT
                    | ((aspect & VK_IMAGE_ASPECT_DEPTH_BIT) != 0 ? VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT : VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT);
            if (samples == 1 && (aspect & VK_IMAGE_ASPECT_DEPTH_BIT) == 0) {
                var properties = VkFormatProperties.calloc(stack);
                vkGetPhysicalDeviceFormatProperties(context.device().getPhysicalDevice(), format, properties);
                if ((properties.optimalTilingFeatures() & VK_FORMAT_FEATURE_STORAGE_IMAGE_BIT) != 0) usage |= VK_IMAGE_USAGE_STORAGE_BIT;
            }
            var info = VkImageCreateInfo.calloc(stack).sType$Default().imageType(VK_IMAGE_TYPE_2D)
                    .format(format).mipLevels(1).arrayLayers(1).samples(samples)
                    .tiling(VK_IMAGE_TILING_OPTIMAL).sharingMode(VK_SHARING_MODE_EXCLUSIVE)
                    .initialLayout(VK_IMAGE_LAYOUT_UNDEFINED)
                    .usage(usage);
            info.extent().set(width, height, 1);
            var allocationInfo = VmaAllocationCreateInfo.calloc(stack).usage(VMA_MEMORY_USAGE_GPU_ONLY);
            var image = stack.mallocLong(1); var allocation = stack.mallocPointer(1);
            VulkanContext.check(vmaCreateImage(context.allocator(), info, allocationInfo, image, allocation, null), "Create image");
            try {
                var viewInfo = VkImageViewCreateInfo.calloc(stack).sType$Default().image(image.get(0))
                        .viewType(VK_IMAGE_VIEW_TYPE_2D).format(format);
                viewInfo.subresourceRange().aspectMask(aspect).baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
                var view = stack.mallocLong(1);
                VulkanContext.check(vkCreateImageView(context.device(), viewInfo, null, view), "Create image view");
                return new VulkanImageResource(context, image.get(0), view.get(0), allocation.get(0),
                        width, height, format, aspect, samples, VK_IMAGE_LAYOUT_UNDEFINED, null, usage, 1);
            } catch (RuntimeException e) {
                vmaDestroyImage(context.allocator(), image.get(0), allocation.get(0));
                throw e;
            }
        }
    }

    public int layout() { return layout; }

    public void transition(VkCommandBuffer commands, int next) {
        barrier(commands, next, false);
    }

    /** Native SDKs can write without changing GENERAL layout; those writes still need a barrier. */
    public void synchronize(VkCommandBuffer commands, int next) {
        barrier(commands, next, true);
    }

    private void barrier(VkCommandBuffer commands, int next, boolean force) {
        if (closed) throw new IllegalStateException("Image is closed");
        if (layout == next && !force) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var barrier = VkImageMemoryBarrier.calloc(1, stack);
            barrier.get(0).sType$Default().oldLayout(layout).newLayout(next)
                    .srcQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK_QUEUE_FAMILY_IGNORED)
                    .image(image).srcAccessMask(layout == VK_IMAGE_LAYOUT_UNDEFINED ? 0 : VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT)
                    .dstAccessMask(VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT);
            barrier.get(0).subresourceRange().aspectMask(aspect).baseMipLevel(0).levelCount(mipLevels).baseArrayLayer(0).layerCount(1);
            vkCmdPipelineBarrier(commands, layout == VK_IMAGE_LAYOUT_UNDEFINED ? VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT
                    : VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, null, null, barrier);
        }
        layout = next;
        if (layoutChanged != null) layoutChanged.accept(next);
    }

    public void clear(VkCommandBuffer commands, float red, float green, float blue, float alpha) {
        if (aspect != VK_IMAGE_ASPECT_COLOR_BIT) throw new IllegalStateException("Color clear requires a color image");
        transition(commands, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var color = VkClearColorValue.calloc(stack).float32(0, red).float32(1, green).float32(2, blue).float32(3, alpha);
            var range = VkImageSubresourceRange.calloc(1, stack).aspectMask(aspect).baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            vkCmdClearColorImage(commands, image, layout, color, range);
        }
    }

    public void clearDepth(VkCommandBuffer commands, float value) {
        if ((aspect & VK_IMAGE_ASPECT_DEPTH_BIT) == 0) throw new IllegalStateException("Depth clear requires a depth image");
        transition(commands, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var clear = VkClearDepthStencilValue.calloc(stack).depth(value).stencil(0);
            var range = VkImageSubresourceRange.calloc(1, stack).aspectMask(aspect).baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
            vkCmdClearDepthStencilImage(commands, image, layout, clear, range);
        }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        if (allocation == 0) return;
        ALLOCATED.addAndGet(-allocatedBytes);
        vkDestroyImageView(context.device(), view, null);
        vmaDestroyImage(context.allocator(), image, allocation);
    }
}
