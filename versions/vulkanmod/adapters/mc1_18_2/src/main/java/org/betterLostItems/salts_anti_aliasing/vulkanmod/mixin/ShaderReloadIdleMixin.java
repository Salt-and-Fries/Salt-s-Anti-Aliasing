package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.minecraft.client.renderer.GameRenderer;
import net.vulkanmod.vulkan.Vulkan;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=GameRenderer.class,priority=1100)
abstract class ShaderReloadIdleMixin {
    @Inject(method="reloadShaders",at=@At("HEAD"))
    private void salts$finishPreviousDraws(CallbackInfo info){
        if(Vulkan.getDevice()!=null)VulkanContext.check(VulkanContext.waitIdle(Vulkan.getDevice()),"Wait before shader reload");
    }
}
