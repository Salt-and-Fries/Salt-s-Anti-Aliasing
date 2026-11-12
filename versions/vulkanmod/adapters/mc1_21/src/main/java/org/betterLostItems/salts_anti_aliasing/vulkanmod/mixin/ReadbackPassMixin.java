package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import net.vulkanmod.vulkan.pass.MainPass;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.ReadbackScope;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.vulkanmod.vulkan.Renderer", remap = false)
abstract class ReadbackPassMixin {
    @WrapOperation(method = "beginMainRenderPass", at = @At(value = "INVOKE", target =
            "Lnet/vulkanmod/vulkan/pass/MainPass;begin(Lorg/lwjgl/vulkan/VkCommandBuffer;Lorg/lwjgl/system/MemoryStack;)V"))
    private void salts$deferPass(MainPass pass, VkCommandBuffer commands, MemoryStack stack, Operation<Void> original) {
        if (!ReadbackScope.active()) original.call(pass, commands, stack);
    }
}
