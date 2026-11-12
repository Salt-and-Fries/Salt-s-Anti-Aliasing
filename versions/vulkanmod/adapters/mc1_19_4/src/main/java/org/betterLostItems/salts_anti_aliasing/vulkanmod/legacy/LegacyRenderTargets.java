package org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy;

import com.mojang.blaze3d.pipeline.*;
import net.vulkanmod.vulkan.*;

/** MainTarget aliases the swapchain; texture targets own independent Vulkan attachments. */
public final class LegacyRenderTargets {
    private LegacyRenderTargets() {}
    public static Framebuffer framebuffer(RenderTarget target) {
        return target instanceof MainTarget ? Vulkan.getSwapChain() : ((LegacyTargetAccess) target).salts$framebuffer();
    }
}
