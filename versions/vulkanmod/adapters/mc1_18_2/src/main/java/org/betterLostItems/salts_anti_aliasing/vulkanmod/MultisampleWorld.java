package org.betterLostItems.salts_anti_aliasing.vulkanmod;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyFramebuffer;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

/** Dynamic-rendering framebuffer with explicit color and depth resolves. */
public final class MultisampleWorld extends LegacyFramebuffer implements AutoCloseable {
    private final VulkanMultisampleTarget target;
    public final boolean alphaToCoverage, hand;
    private long begunPasses;
    public MultisampleWorld(VulkanContext context, LegacyFramebuffer original, VulkanImageResource color,
            VulkanImageResource depth, int samples, boolean coverage, boolean hand) {
        super();
        target = new VulkanMultisampleTarget(context, color, depth, samples);
        this.width = color.width; this.height = color.height; this.format = color.format; this.depthFormat = depth.format;
        this.alphaToCoverage = coverage; this.hand = hand;
    }
    public int samples() { return target.samples; }
    public long begunPasses() { return begunPasses; }
    public static void clearPasses() {}
    public void seedHand(VkCommandBuffer commands, VulkanFrameResources frame, VulkanImageResource color, VulkanImageResource depth) {
        target.destinations(color, depth); target.seedHand(commands, frame);
    }
    public void newFrame(VulkanImageResource color, VulkanImageResource depth, java.nio.FloatBuffer clear) {
        target.destinations(color, depth); target.newFrame(clear.get(0), clear.get(1), clear.get(2), clear.get(3));
    }
    @Override public void beginRendering(VkCommandBuffer commands, MemoryStack stack) {
        target.begin(commands, stack); begunPasses++;
        org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyRenderer.setViewport(0, 0, width, height);
        org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyRenderer.setScissor(0, 0, width, height);
    }
    @Override public void endRendering(VkCommandBuffer commands) { target.end(commands); }
    @Override public long getDepthImageView() { return target.depth.view; }
    @Override public void close() { target.close(); }
}
