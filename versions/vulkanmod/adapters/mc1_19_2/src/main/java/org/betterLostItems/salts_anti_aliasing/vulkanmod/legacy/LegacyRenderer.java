package org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy;

import net.vulkanmod.vulkan.*;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin.DrawerAccessor;
import org.lwjgl.vulkan.VkCommandBuffer;

/** Typed boundary for VulkanMod 0.3.x's dynamic-rendering Drawer. */
public final class LegacyRenderer implements LegacyRendererAccessor {
    private static final LegacyRenderer INSTANCE = new LegacyRenderer();
    private static final LegacyRenderPass PASS = new LegacyRenderPass();
    private static boolean recording;
    private static int imageIndex;
    public static LegacyRenderer getInstance() { return INSTANCE; }
    public static int getFramesNum() { return Vulkan.getSwapChainImages().size(); }
    public static int getCurrentFrame() { return Drawer.getCurrentFrame(); }
    public static VkCommandBuffer getCommandBuffer() { return Drawer.getCommandBuffer(); }
    public static boolean isRecording() { return recording && !Drawer.skipRendering; }
    public static void recording(boolean value) { recording = value; }
    public static int imageIndex() { return imageIndex; }
    public static void imageIndex(int value) { imageIndex = value; }
    public static void scheduleSwapChainUpdate() { Drawer.shouldRecreate = true; }
    public Framebuffer salts$boundFramebuffer() { return ((DrawerAccessor) Drawer.getInstance()).salts$boundFramebuffer(); }
    public LegacyRenderPass getBoundRenderPass() { return salts$boundFramebuffer() == null ? null : PASS; }
    public void endRenderPass() { if (salts$boundFramebuffer() != null) Drawer.getInstance().endRendering(); }
    public void beginRendering(LegacyRenderPass ignored, Framebuffer framebuffer) {
        var multisample = SaltsVulkanModClient.multisample(framebuffer);
        var target = multisample == null ? framebuffer : multisample;
        Drawer.getInstance().beginRendering(target);
        // Drawer assigns boundFramebuffer after beginRendering returns. Its scissor
        // conversion reads that field, so restore dynamic state only after the bind.
        setViewport(0, 0, target.width, target.height);
        setScissor(0, 0, target.width, target.height);
    }
    public static void setViewport(int x, int y, int width, int height) { Drawer.setViewport(x, y, width, height); }
    public static void setScissor(int x, int y, int width, int height) { Drawer.setScissor(x, y, width, height); }
    public static void resetViewport() {
        var target = INSTANCE.salts$boundFramebuffer();
        if (target != null) setViewport(0, 0, target.width, target.height);
    }
    public static void resetScissor() {
        var target = INSTANCE.salts$boundFramebuffer();
        if (target != null) setScissor(0, 0, target.width, target.height);
    }
}
