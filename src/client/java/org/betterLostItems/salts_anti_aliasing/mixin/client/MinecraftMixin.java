package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;

/**
 * Implements minecraft mixin behavior for Salt's Anti Aliasing. Mixin bridge code that hooks
 * Minecraft internals at narrowly chosen call sites so the renderer can be redirected without
 * forking vanilla classes.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
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
    @Inject(method = "close", at = @At("HEAD"))
    private void saltsAntiAliasing$flushMetricsOnClose(CallbackInfo callbackInfo) {
        ModernMinecraftHooks.shutdownMetrics();
    }
}
