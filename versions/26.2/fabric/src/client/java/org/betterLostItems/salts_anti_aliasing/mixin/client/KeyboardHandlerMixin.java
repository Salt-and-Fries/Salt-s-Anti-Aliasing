package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Implements keyboard handler mixin behavior for Salt's Anti Aliasing. Mixin bridge code that hooks
 * Minecraft internals at narrowly chosen call sites so the renderer can be redirected without
 * forking vanilla classes.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    /**
     * Delegates the debug-key path to the 26.2 platform bridge.
     */
    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$toggleEdgeDebug(net.minecraft.client.input.KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        ModernMinecraftHooks.toggleEdgeDebug(minecraft, keyEvent.key(), cir);
    }
}
