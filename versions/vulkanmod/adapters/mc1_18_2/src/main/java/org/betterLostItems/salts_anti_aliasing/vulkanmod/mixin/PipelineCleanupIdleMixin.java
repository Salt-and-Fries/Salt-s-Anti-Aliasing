package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.Vulkan;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=net.vulkanmod.vulkan.shader.Pipeline.class,remap=false)
abstract class PipelineCleanupIdleMixin {
    @Inject(method="cleanUp",at=@At("HEAD"))
    private void salts$completeUses(CallbackInfo info){
        VulkanContext.check(VulkanContext.waitIdle(Vulkan.getDevice()),"Wait before retiring native pipeline descriptors");
    }
}
