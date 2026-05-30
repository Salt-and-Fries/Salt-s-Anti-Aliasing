package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Group;
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
     * 1.21.9-1.21.11 debug-key descriptor.
     */
    @Group(name = "saltsAntiAliasing$debugKeys", min = 1, max = 1)
    @Inject(method = "handleDebugKeys(Lnet/minecraft/client/input/KeyEvent;)Z", at = @At("HEAD"), cancellable = true, require = 0)
    private void saltsAntiAliasing$toggleEdgeDebug(@Coerce Object keyEvent, CallbackInfoReturnable<Boolean> cir) {
        int key = saltsAntiAliasing$keyFromModernEvent(keyEvent);
        if (key != -1) {
            ModernMinecraftHooks.toggleEdgeDebug(minecraft, key, cir);
        }
    }

    /**
     * 1.21.8 debug-key descriptor.
     */
    @Dynamic("1.21.8 keeps the debug-key handler as method_1468(int).")
    @Group(name = "saltsAntiAliasing$debugKeys", min = 1, max = 1)
    @Inject(method = "method_1468(I)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void saltsAntiAliasing$toggleEdgeDebugLegacy(int key, CallbackInfoReturnable<Boolean> cir) {
        ModernMinecraftHooks.toggleEdgeDebug(Minecraft.getInstance(), key, cir);
    }

    private static int saltsAntiAliasing$keyFromModernEvent(Object keyEvent) {
        for (String accessor : new String[]{"key", "comp_4795", "method_74228"}) {
            try {
                Object value = keyEvent.getClass().getMethod(accessor).invoke(keyEvent);
                if (value instanceof Number number) {
                    return number.intValue();
                }
            } catch (ReflectiveOperationException | SecurityException ignored) {
                // Try the next mapping name.
            }
        }
        return -1;
    }
}
