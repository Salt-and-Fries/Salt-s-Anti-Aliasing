package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks the 1.21.8-1.21.9 debug-key method signature.
 *
 * <p>This branch is the "modern" 1.21.8-1.21.11 jar, but Mojang still changed
 * the debug-key method signature inside that window. The 1.21.11 compile target
 * sees {@code handleDebugKeys(KeyEvent)}, while 1.21.8 and 1.21.9 expose the
 * older intermediary method {@code method_1468(int)} at runtime. Keeping this as
 * a tiny pseudo mixin isolates that compatibility wrinkle from the normal
 * 1.21.10-1.21.11 hook and avoids asking Loom to remap a method that is not
 * present on the current compile classpath.</p>
 *
 * <p>The mixin target is the normal named {@link KeyboardHandler} class so the
 * current development runtime can load the mixin without a missing-class warning.
 * Only the old method selector is written in intermediary form and marked
 * dynamic. Production Fabric loads Minecraft in intermediary namespace, so the
 * selector can still bind on older modern runtimes even though the named
 * 1.21.11 source jar no longer contains that method overload.</p>
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerLegacyDebugKeysMixin {
    /**
     * Toggles the edge-debug overlay from the old {@code F3 + N} style debug
     * key path without touching any shared anti-aliasing logic directly.
     */
    @Dynamic("1.21.8-1.21.9 keep the debug-key handler as method_1468(int).")
    @Inject(method = "method_1468(I)Z", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void saltsAntiAliasing$toggleEdgeDebugLegacy(int key, CallbackInfoReturnable<Boolean> cir) {
        ModernMinecraftHooks.toggleEdgeDebug(Minecraft.getInstance(), key, cir);
    }
}
