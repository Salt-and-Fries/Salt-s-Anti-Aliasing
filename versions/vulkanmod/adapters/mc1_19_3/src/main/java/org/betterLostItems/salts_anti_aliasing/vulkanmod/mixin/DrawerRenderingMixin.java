package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.Drawer;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyRenderer;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=Drawer.class,remap=false)
abstract class DrawerRenderingMixin {
    @Redirect(method="initiateRenderPass",at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCmdBeginRenderPass(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/vulkan/VkRenderPassBeginInfo;I)V"))
    private void salts$begin(VkCommandBuffer commands,VkRenderPassBeginInfo ignored,int contents){LegacyRenderer.beginFrame();}
    @Redirect(method="endRenderPass",at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCmdEndRenderPass(Lorg/lwjgl/vulkan/VkCommandBuffer;)V"))
    private void salts$end(VkCommandBuffer commands){LegacyRenderer.endFrame();}
}
