package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import static org.lwjgl.vulkan.KHRDynamicRendering.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceProperties2;
import static org.lwjgl.vulkan.VK12.*;

/** World attachments with a hardware color and depth resolve at every pass boundary. */
public final class VulkanMultisampleTarget implements AutoCloseable {
    public final VulkanImageResource color, depth;
    public final int samples;
    private final int depthResolveMode;
    private boolean clear;
    private final float[] clearColor = new float[4];
    private VulkanImageResource resolvedColor, resolvedDepth;
    private VulkanGraphicsPass backgroundCopy;
    private final VulkanContext context;

    public VulkanMultisampleTarget(VulkanContext context, VulkanImageResource destination,
                                   VulkanImageResource destinationDepth, int requestedSamples) {
        this.context = context;
        try (var stack = MemoryStack.stackPush()) {
            var properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(context.device().getPhysicalDevice(), properties);
            int supported = properties.limits().framebufferColorSampleCounts()
                    & properties.limits().framebufferDepthSampleCounts();
            supported &= imageSamples(context, stack, destination);
            supported &= imageSamples(context, stack, destinationDepth);
            int selected = Integer.highestOneBit(Math.min(64, requestedSamples));
            while (selected > 1 && (supported & selected) == 0) selected >>= 1;
            if (selected <= 1) throw new IllegalStateException("No supported multisample color/depth attachment pair");
            samples = selected;
            var resolve = VkPhysicalDeviceDepthStencilResolveProperties.calloc(stack).sType$Default();
            var properties2 = VkPhysicalDeviceProperties2.calloc(stack).sType$Default().pNext(resolve);
            vkGetPhysicalDeviceProperties2(context.device().getPhysicalDevice(), properties2);
            depthResolveMode = (resolve.supportedDepthResolveModes() & VK_RESOLVE_MODE_MIN_BIT) != 0
                    ? VK_RESOLVE_MODE_MIN_BIT : VK_RESOLVE_MODE_SAMPLE_ZERO_BIT;
            if ((resolve.supportedDepthResolveModes() & depthResolveMode) == 0)
                throw new IllegalStateException("Depth resolve is unavailable");
        }
        color = VulkanImageResource.create(context, destination.width, destination.height, destination.format,
                VK_IMAGE_ASPECT_COLOR_BIT, samples);
        try {
            depth = VulkanImageResource.create(context, destination.width, destination.height, destinationDepth.format,
                    destinationDepth.aspect, samples);
        } catch (RuntimeException e) { color.close(); throw e; }
        destinations(destination, destinationDepth);
    }

    private static int imageSamples(VulkanContext context, MemoryStack stack, VulkanImageResource image) {
        var properties = VkImageFormatProperties.calloc(stack);
        VulkanContext.check(vkGetPhysicalDeviceImageFormatProperties(context.device().getPhysicalDevice(), image.format,
                VK_IMAGE_TYPE_2D, VK_IMAGE_TILING_OPTIMAL, VK_IMAGE_USAGE_SAMPLED_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT
                        | VK_IMAGE_USAGE_TRANSFER_DST_BIT | ((image.aspect & VK_IMAGE_ASPECT_DEPTH_BIT) != 0
                        ? VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT : VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT), 0, properties),
                "Query multisample attachment support");
        return properties.sampleCounts();
    }

    public void destinations(VulkanImageResource color, VulkanImageResource depth) {
        if (this.color.width != color.width || this.color.height != color.height || this.color.format != color.format
                || this.depth.format != depth.format || color.samples != 1 || depth.samples != 1)
            throw new IllegalArgumentException("MSAA resolve attachments have incompatible dimensions or formats");
        resolvedColor = color; resolvedDepth = depth;
    }

    public void newFrame(float red, float green, float blue, float alpha) {
        clear = true;
        clearColor[0] = red; clearColor[1] = green; clearColor[2] = blue; clearColor[3] = alpha;
    }

    /** Rasterize the resolved world into every hand sample, keeping it out of temporal history. */
    public void seedHand(VkCommandBuffer commands, VulkanFrameResources frame) {
        if (backgroundCopy == null) backgroundCopy = VulkanGraphicsPass.multisample(context,
                "hand_background_copy", color.format, samples);
        backgroundCopy.draw(commands, frame, java.util.List.of(color),
                java.util.Map.of("WorldSampler", new VulkanGraphicsPass.Sample(resolvedColor, false)), java.util.Map.of());
        depth.clearDepth(commands, 1f);
        clear = false;
    }

    public void begin(VkCommandBuffer commands, MemoryStack stack) {
        color.synchronize(commands, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        depth.synchronize(commands, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        resolvedColor.synchronize(commands, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        resolvedDepth.synchronize(commands, VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        int load = clear ? VK_ATTACHMENT_LOAD_OP_CLEAR : VK_ATTACHMENT_LOAD_OP_LOAD;
        var colors = VkRenderingAttachmentInfo.calloc(1, stack).sType$Default()
                .imageView(color.view).imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .resolveMode(VK_RESOLVE_MODE_AVERAGE_BIT).resolveImageView(resolvedColor.view)
                .resolveImageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL)
                .loadOp(load).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        for (int i = 0; i < 4; i++) colors.clearValue().color().float32(i, clearColor[i]);
        var depths = VkRenderingAttachmentInfo.calloc(stack).sType$Default()
                .imageView(depth.view).imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                .resolveMode(depthResolveMode).resolveImageView(resolvedDepth.view)
                .resolveImageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                .loadOp(load).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        depths.clearValue().depthStencil().set(1f, 0);
        var info = VkRenderingInfo.calloc(stack).sType$Default().layerCount(1)
                .pColorAttachments(colors).pDepthAttachment(depths);
        info.renderArea().offset().set(0, 0);
        info.renderArea().extent().set(color.width, color.height);
        vkCmdBeginRenderingKHR(commands, info);
        clear = false;
    }

    public void end(VkCommandBuffer commands) { vkCmdEndRenderingKHR(commands); }

    @Override public void close() {
        if (backgroundCopy != null) backgroundCopy.close();
        depth.close(); color.close();
    }
}
