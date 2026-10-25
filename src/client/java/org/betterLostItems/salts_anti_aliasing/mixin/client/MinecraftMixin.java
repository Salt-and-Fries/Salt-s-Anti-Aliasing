package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.Minecraft;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Implements minecraft mixin behavior for Salt's Anti Aliasing. Mixin bridge code that hooks
 * Minecraft internals at narrowly chosen call sites so the renderer can be redirected without
 * forking vanilla classes.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
    @Inject(method = "reloadResourcePacks()Ljava/util/concurrent/CompletableFuture;", at = @At("RETURN"))
    private void saltsAntiAliasing$resetAfterReload(
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<java.util.concurrent.CompletableFuture<Void>> result) {
        Minecraft minecraft = (Minecraft)(Object)this;
        result.getReturnValue().thenRun(() -> minecraft.execute(() -> {
            var runtime = org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient.runtimeOrNull();
            if (runtime != null) runtime.retryRendering();
            org.betterLostItems.salts_anti_aliasing.client.render.vulkan.ParticleMotionGeometry.clear();
        }));
    }
    /**
     * Flushes metrics when Minecraft begins normal shutdown.
     */
    @Inject(method = "stop", at = @At("HEAD"))
    private void saltsAntiAliasing$flushMetricsOnStop(CallbackInfo callbackInfo) {
        ModernMinecraftHooks.flushMetrics();
    }

    /**
     * Flushes metrics when Minecraft tears down renderer resources.
     */
    @Inject(method = "close", at = @At("HEAD"))
    private void saltsAntiAliasing$flushMetricsOnClose(CallbackInfo callbackInfo) {
        ModernMinecraftHooks.flushMetrics();
    }

    /**
     * Keeps native swapchain routing alive until Minecraft has destroyed the surface-owned proxy.
     */
    @Inject(
            method = "close",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/api/device/GpuSurface;close()V",
                    shift = At.Shift.AFTER
            )
    )
    private void saltsAntiAliasing$shutdownNativeIntegrations(CallbackInfo callbackInfo) {
        ModernMinecraftHooks.shutdownNativeIntegrations();
        ModernMinecraftHooks.shutdownMetrics();
    }
}
