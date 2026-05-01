package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /**
     * Lets internal-resolution modes temporarily replace Minecraft's main render target.
     */
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$overrideMainRenderTarget(CallbackInfoReturnable<RenderTarget> callbackInfo) {
        RenderTarget overrideTarget = ModernMinecraftHooks.overrideMainRenderTarget();
        if (overrideTarget != null) {
            callbackInfo.setReturnValue(overrideTarget);
        }
    }

    /**
     * Flushes metrics when Minecraft begins normal shutdown.
     */
    @Inject(method = "stop", at = @At("HEAD"))
    private void saltsAntiAliasing$flushMetricsOnStop(CallbackInfo callbackInfo) {
        ModernMinecraftHooks.shutdownMetrics();
    }

    /**
     * Flushes metrics when Minecraft tears down renderer resources.
     */
    @Inject(method = "destroy", at = @At("HEAD"))
    private void saltsAntiAliasing$flushMetricsOnDestroy(CallbackInfo callbackInfo) {
        ModernMinecraftHooks.shutdownMetrics();
    }
}
