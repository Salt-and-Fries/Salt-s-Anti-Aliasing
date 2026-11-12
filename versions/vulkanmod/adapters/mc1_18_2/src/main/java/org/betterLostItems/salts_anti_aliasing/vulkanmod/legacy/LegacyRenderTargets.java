package org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy;
import com.mojang.blaze3d.pipeline.*;
import net.vulkanmod.vulkan.Vulkan;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.*;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.*;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin.VulkanWindowAccessor;
import static org.lwjgl.vulkan.VK10.*;
public final class LegacyRenderTargets {
    private static Window window;
    private LegacyRenderTargets(){}
    public static void invalidate(){window=null;}
    public static Window window(){
        if(window==null||window.handle!=Vulkan.getSwapChain())window=new Window();
        return window;
    }
    public static LegacyFramebuffer framebuffer(RenderTarget target){return target instanceof MainTarget?window():((LegacyTargetAccess)target).salts$framebuffer();}
    public static final class Window extends LegacyFramebuffer {
        public final long handle=Vulkan.getSwapChain();
        private final VulkanContext context=new VulkanContext(Vulkan.getDevice(),Vulkan.getAllocator());
        private final int[] layouts=new int[Vulkan.getSwapChainImages().size()];
        private int depthLayout=VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL;
        private Window(){width=Vulkan.getSwapchainExtent().width();height=Vulkan.getSwapchainExtent().height();format=VulkanWindowAccessor.salts$format();depthFormat=VulkanWindowAccessor.salts$depthFormat();}
        public VulkanImageResource color(){
            int index=LegacyRenderer.imageIndex();long image=Vulkan.getSwapChainImages().get(index);
            return VulkanImageResource.borrow(context,image,VulkanWindowAccessor.salts$views().get(index),width,height,format,
                    VK_IMAGE_ASPECT_COLOR_BIT,1,layouts[index],next->layouts[index]=next,SwapchainBridge.usage(image));
        }
        public VulkanImageResource depth(){
            return VulkanImageResource.borrow(context,VulkanWindowAccessor.salts$depth(),VulkanWindowAccessor.salts$depthView(),
                    width,height,depthFormat,LegacyTextures.aspect(depthFormat),1,depthLayout,next->depthLayout=next,
                    VK_IMAGE_USAGE_DEPTH_STENCIL_ATTACHMENT_BIT|VK_IMAGE_USAGE_SAMPLED_BIT|VK_IMAGE_USAGE_TRANSFER_SRC_BIT|VK_IMAGE_USAGE_TRANSFER_DST_BIT);
        }
        public void close(){} // Borrowed swapchain/depth objects are owned by VulkanMod.
    }
}
