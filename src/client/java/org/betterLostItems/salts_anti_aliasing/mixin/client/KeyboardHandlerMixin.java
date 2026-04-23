package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
    private static final long EDGE_DEBUG_TOGGLE_DEBOUNCE_MS = 250L;
    private static long saltsAntiAliasing$lastEdgeToggleMs;

    @Shadow
    @Final
    private Minecraft minecraft;

    @Inject(method = "handleDebugKeys", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$toggleEdgeDebug(net.minecraft.client.input.KeyEvent keyEvent, CallbackInfoReturnable<Boolean> cir) {
        if (keyEvent.key() != GLFW.GLFW_KEY_K) {
            return;
        }

        long now = Util.getMillis();
        if (now - saltsAntiAliasing$lastEdgeToggleMs < EDGE_DEBUG_TOGGLE_DEBOUNCE_MS) {
            cir.setReturnValue(true);
            return;
        }

        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        saltsAntiAliasing$lastEdgeToggleMs = now;
        boolean enabled = runtime.toggleDebugViews();
        minecraft.gui.setOverlayMessage(
                Component.literal("Salt's Anti Aliasing Edge View: " + (enabled ? "ON" : "OFF")),
                false
        );
        cir.setReturnValue(true);
    }
}
