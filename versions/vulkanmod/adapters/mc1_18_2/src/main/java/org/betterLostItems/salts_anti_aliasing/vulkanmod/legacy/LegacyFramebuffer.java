package org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.KHRDynamicRendering.*;

/** Dynamic attachments for the Drawer generation predating VulkanMod framebuffer objects. */
public class LegacyFramebuffer implements AutoCloseable {
    public int width,height,format,depthFormat;
    private VulkanImageResource color,depth;
    private boolean clear;
    protected LegacyFramebuffer() {}
    public LegacyFramebuffer(VulkanContext context,int width,int height,int format,int depthFormat) {
        this.width=width;this.height=height;this.format=format;this.depthFormat=depthFormat;
        color=VulkanImageResource.create(context,width,height,format,VK_IMAGE_ASPECT_COLOR_BIT,1);
        try { depth=VulkanImageResource.create(context,width,height,depthFormat,LegacyTextures.aspect(depthFormat),1); }
        catch (RuntimeException error) { color.close();throw error; }
        clear=true;
    }
    public VulkanImageResource color(){return color;}
    public VulkanImageResource depth(){return depth;}
    public void clearOnBegin(){clear=true;}
    public void beginRendering(VkCommandBuffer commands,MemoryStack stack) {
        var color=color();var depth=depth();
        color.synchronize(commands,VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        depth.synchronize(commands,VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
        int load=clear?VK_ATTACHMENT_LOAD_OP_CLEAR:VK_ATTACHMENT_LOAD_OP_LOAD;
        var colors=VkRenderingAttachmentInfo.calloc(1,stack).sType$Default().imageView(color.view)
                .imageLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL).loadOp(load).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        colors.clearValue().color().float32(0,0).float32(1,0).float32(2,0).float32(3,1);
        var depths=VkRenderingAttachmentInfo.calloc(stack).sType$Default().imageView(depth.view)
                .imageLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL).loadOp(load).storeOp(VK_ATTACHMENT_STORE_OP_STORE);
        depths.clearValue().depthStencil().set(1,0);
        var info=VkRenderingInfo.calloc(stack).sType$Default().layerCount(1).pColorAttachments(colors).pDepthAttachment(depths);
        if((depth.aspect&VK_IMAGE_ASPECT_STENCIL_BIT)!=0)info.pStencilAttachment(depths);
        info.renderArea().offset().set(0,0);info.renderArea().extent().set(width,height);
        vkCmdBeginRenderingKHR(commands,info);clear=false;
    }
    public void endRendering(VkCommandBuffer commands){vkCmdEndRenderingKHR(commands);}
    public long getDepthImageView(){return depth().view;}
    public void close(){if(color!=null)color.close();if(depth!=null)depth.close();color=depth=null;}
}
