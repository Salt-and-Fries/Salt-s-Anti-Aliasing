package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Documents minecraft mixin behavior for Salt's Anti Aliasing. Mixin bridge code for carefully scoped
 * hooks into Minecraft rendering and options screens.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    /**
     * Lets the renderer temporarily substitute the world render target for
     * internal-resolution modes while leaving HUD/menu rendering on Minecraft's target.
     */
    @Inject(method = "getMainRenderTarget", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$overrideMainRenderTarget(CallbackInfoReturnable<RenderTarget> callbackInfo) {
        RenderTarget overrideTarget = ModernMinecraftHooks.overrideMainRenderTarget();
        if (overrideTarget != null) {
            callbackInfo.setReturnValue(overrideTarget);
        }
    }

    /**
     * Flushes metrics when Minecraft exits through the normal stop path.
     */
    @Inject(method = "stop", at = @At("HEAD"))
    private void saltsAntiAliasing$flushMetricsOnStop(CallbackInfo callbackInfo) {
        ModernMinecraftHooks.shutdownMetrics();
    }

    /**
     * Flushes metrics when the client destroys the renderer/window path.
     */
    @Inject(method = "destroy", at = @At("HEAD"))
    private void saltsAntiAliasing$flushMetricsOnDestroy(CallbackInfo callbackInfo) {
        ModernMinecraftHooks.shutdownMetrics();
    }
}
