package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.gui.components.debug.DebugEntryFps;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Keep vanilla's FPS row and its limit/present-mode details, adding measured FG output. */
@Mixin(DebugEntryFps.class)
public abstract class DebugEntryFpsMixin {
    @ModifyArg(method="display",at=@At(value="INVOKE",target=
            "Lnet/minecraft/client/gui/components/debug/DebugScreenDisplayer;addPriorityLine(Ljava/lang/String;)V"),index=0)
    private String saltsAntiAliasing$showFrameGenerationRate(String line) {
        return FsrRuntime.instance().decorateDebugFps(line);
    }
}
