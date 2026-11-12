package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Minecraft.class, priority = 1100)
abstract class MinecraftMixin {
    @Inject(method="close",at=@At("HEAD"))
    private void salts$finishBeforeVanillaShutdown(org.spongepowered.asm.mixin.injection.callback.CallbackInfo info){
        SaltsVulkanModClient.close();
    }

    @Inject(method = {"stop", "close"}, at = @At("HEAD"))
    private void salts$flushMetrics(org.spongepowered.asm.mixin.injection.callback.CallbackInfo info) {
        var runtime = org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null) runtime.flushMetrics();
    }
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void salts$redirectScene(CallbackInfoReturnable<RenderTarget> result) {
        var scene = SaltsVulkanModClient.sceneOverride();
        if (scene != null) result.setReturnValue(scene);
    }
}
