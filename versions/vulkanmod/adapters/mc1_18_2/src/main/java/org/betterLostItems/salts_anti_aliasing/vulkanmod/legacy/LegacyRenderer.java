package org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy;
import net.vulkanmod.vulkan.*;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import static org.lwjgl.vulkan.VK10.*;
public final class LegacyRenderer implements LegacyRendererAccessor {
    private static final LegacyRenderer INSTANCE=new LegacyRenderer();
    private static final LegacyRenderPass PASS=new LegacyRenderPass();
    private static boolean recording;
    private static int imageIndex;
    private LegacyFramebuffer bound;
    public static LegacyRenderer getInstance(){return INSTANCE;}
    public static int getFramesNum(){return Vulkan.getSwapChainImages().size();}
    public static int getCurrentFrame(){return Drawer.getCurrentFrame();}
    public static VkCommandBuffer getCommandBuffer(){return org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin.DrawerAccessor.salts$commands().get(getCurrentFrame());}
    public static boolean isRecording(){return recording&&!Drawer.skipRendering;}
    public static void recording(boolean value){recording=value;}
    public static int imageIndex(){return imageIndex;}
    public static void imageIndex(int value){imageIndex=value;}
    public static void scheduleSwapChainUpdate(){Drawer.shouldRecreate=true;}
    public LegacyFramebuffer salts$boundFramebuffer(){return bound;}
    public LegacyRenderPass getBoundRenderPass(){return bound==null?null:PASS;}
    public void endRenderPass(){if(bound!=null){bound.endRendering(getCommandBuffer());bound=null;}}
    public void beginRendering(LegacyRenderPass ignored,LegacyFramebuffer framebuffer){
        var msaa=SaltsVulkanModClient.multisample(framebuffer);var target=msaa==null?framebuffer:msaa;
        if(bound!=target){endRenderPass();try(var stack=MemoryStack.stackPush()){target.beginRendering(getCommandBuffer(),stack);}bound=target;}
        setViewport(0,0,target.width,target.height);setScissor(0,0,target.width,target.height);
    }
    public static void beginFrame(){recording=true;vkCmdSetDepthBias(getCommandBuffer(),0,0,0);vkCmdSetLineWidth(getCommandBuffer(),1);var window=LegacyRenderTargets.window();window.clearOnBegin();INSTANCE.beginRendering(null,window);}
    public static void endFrame(){
        INSTANCE.endRenderPass();
        LegacyRenderTargets.window().color().transition(getCommandBuffer(),
                org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime.instance().isFrameGenerationSwapchainOwned()
                ?VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL:KHRSwapchain.VK_IMAGE_LAYOUT_PRESENT_SRC_KHR);
        recording=false;
    }
    public static void setViewport(int x,int y,int width,int height){
        try(var stack=MemoryStack.stackPush()){
            var view=VkViewport.calloc(1,stack).x(x).y(y+height).width(width).height(-height).minDepth(0).maxDepth(1);
            vkCmdSetViewport(getCommandBuffer(),0,view);
        }
    }
    public static void setScissor(int x,int y,int width,int height){
        try(var stack=MemoryStack.stackPush()){
            var rect=VkRect2D.calloc(1,stack);rect.offset().set(x,y);rect.extent().set(width,height);vkCmdSetScissor(getCommandBuffer(),0,rect);
        }
    }
    public static void resetViewport(){if(INSTANCE.bound!=null)setViewport(0,0,INSTANCE.bound.width,INSTANCE.bound.height);}
    public static void resetScissor(){if(INSTANCE.bound!=null)setScissor(0,0,INSTANCE.bound.width,INSTANCE.bound.height);}
}
