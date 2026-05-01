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
 * Documents keyboard handler mixin behavior for Salt's Anti Aliasing. Mixin bridge code for carefully
 * scoped hooks into Minecraft rendering and options screens.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    /**
     * 1.21.10-1.21.11 debug-key descriptor.
     */
    @Inject(method = "handleDebugKeys(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$toggleEdgeDebug(net.minecraft.client.input.KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        ModernMinecraftHooks.toggleEdgeDebug(minecraft, keyEvent.key(), cir);
    }
}
