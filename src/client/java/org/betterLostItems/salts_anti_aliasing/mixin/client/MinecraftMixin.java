package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlSceneScaleController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$overrideMainRenderTarget(CallbackInfoReturnable<RenderTarget> callbackInfo) {
        RenderTarget overrideTarget = OpenGlSceneScaleController.instance().overrideMainTarget();
        if (overrideTarget != null) {
            callbackInfo.setReturnValue(overrideTarget);
        }
    }

    @Inject(method = "stop", at = @At("HEAD"))
    private void saltsAntiAliasing$flushMetricsOnStop(CallbackInfo callbackInfo) {
        if (SaltsAntiAliasingClient.runtimeOrNull() != null) {
            SaltsAntiAliasingClient.runtime().shutdownMetrics();
        }
    }

    @Inject(method = "destroy", at = @At("HEAD"))
    private void saltsAntiAliasing$flushMetricsOnDestroy(CallbackInfo callbackInfo) {
        if (SaltsAntiAliasingClient.runtimeOrNull() != null) {
            SaltsAntiAliasingClient.runtime().shutdownMetrics();
        }
    }
}
