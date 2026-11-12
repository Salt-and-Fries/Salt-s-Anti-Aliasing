package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
abstract class OpaqueSceneMixin {
    @Inject(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher;renderTranslucentFeatures()V"))
    private void salts$beforeTransparency(CallbackInfo info) { SaltsVulkanModClient.captureOpaque(); }
}
