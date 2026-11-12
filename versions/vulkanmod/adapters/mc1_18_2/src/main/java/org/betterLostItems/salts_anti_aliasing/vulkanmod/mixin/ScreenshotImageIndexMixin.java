package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.mojang.blaze3d.platform.NativeImage;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value = NativeImage.class, priority = 1100)
abstract class ScreenshotImageIndexMixin {
    @ModifyExpressionValue(method = "downloadTexture", at = @At(value = "INVOKE", target = "Lnet/vulkanmod/vulkan/Drawer;getCurrentFrame()I", remap = false))
    private int salts$index(int frame) { return LegacyRenderer.imageIndex(); }
}
