package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.vulkanmod.vulkan.SwapChain;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value = SwapChain.class, remap = false)
abstract class SwapchainImageIndexMixin {
    @ModifyExpressionValue(method = "beginRendering", at = @At(value = "INVOKE", target = "Lnet/vulkanmod/vulkan/Drawer;getCurrentFrame()I"))
    private int salts$view(int frame) { return LegacyRenderer.imageIndex(); }
    @ModifyVariable(method = {"colorAttachmentLayout", "presentLayout"}, at = @At("HEAD"), argsOnly = true)
    private int salts$layout(int frame) { return LegacyRenderer.imageIndex(); }
}
