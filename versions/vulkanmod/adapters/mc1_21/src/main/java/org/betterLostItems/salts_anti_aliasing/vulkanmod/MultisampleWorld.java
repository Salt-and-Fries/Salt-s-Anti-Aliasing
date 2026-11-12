package org.betterLostItems.salts_anti_aliasing.vulkanmod;

import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;

/** Preserves VulkanMod's render-pass identity so its pipeline cache includes the sample count. */
public final class MultisampleWorld extends Framebuffer implements AutoCloseable {
    private final VulkanMultisampleTarget target;
    private long begunPasses;
    public long begunPasses() { return begunPasses; }
    public final Pass pass;
    private record Signature(int colorFormat, int depthFormat, int samples, boolean coverage, boolean hand) {}
    private static final java.util.Map<Signature, Pass> PASSES = new java.util.HashMap<>();
    public static void clearPasses() { PASSES.clear(); }

    public MultisampleWorld(VulkanContext context, Framebuffer original, VulkanImageResource color,
                            VulkanImageResource depth, int samples, boolean alphaToCoverage, boolean hand) {
        super(Framebuffer.builder(original.getColorAttachment(), original.getDepthAttachment()));
        if (!NativeVulkanSetup.dynamicRenderingEnabled())
            throw new IllegalStateException("MSAA requires Vulkan dynamic rendering support");
        target = new VulkanMultisampleTarget(context, color, depth, samples);
        pass = PASSES.computeIfAbsent(new Signature(color.format, depth.format, target.samples, alphaToCoverage, hand),
                key -> new Pass(this, alphaToCoverage, hand));
        pass.world = this;
        pass.setFramebuffer(this);
    }

    public int samples() { return target.samples; }
    public int colorFormat() { return target.color.format; }
    public int depthFormat() { return target.depth.format; }
    public boolean stencil() { return (target.depth.aspect & org.lwjgl.vulkan.VK10.VK_IMAGE_ASPECT_STENCIL_BIT) != 0; }
    public void seedHand(VkCommandBuffer commands, VulkanFrameResources frame, VulkanImageResource color, VulkanImageResource depth) {
        target.destinations(color, depth);
        target.seedHand(commands, frame);
    }
    public void newFrame(VulkanImageResource color, VulkanImageResource depth, java.nio.FloatBuffer clearColor) {
        target.destinations(color, depth);
        target.newFrame(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3));
    }
    @Override public void beginRenderPass(VkCommandBuffer commands, RenderPass ignored, MemoryStack stack) {
        target.begin(commands, stack);
        begunPasses++;
    }
    @Override public long getColorAttachmentView() { return target.color.view; }
    @Override public long getDepthImageView() { return target.depth.view; }
    @Override public void close() { target.close(); }

    public static final class Pass extends RenderPass {
        public final boolean alphaToCoverage;
        public final boolean hand;
        public MultisampleWorld world;
        Pass(MultisampleWorld framebuffer, boolean alphaToCoverage, boolean hand) {
            super(framebuffer, null, null);
            this.world = framebuffer; this.alphaToCoverage = alphaToCoverage;
            this.hand = hand;
        }
        @Override public void endRenderPass(VkCommandBuffer commands) { world.target.end(commands); }
    }
}
