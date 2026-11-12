package org.betterLostItems.salts_anti_aliasing.validation.mixin;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(GameRenderer.class)
abstract class ScreenshotFrameMixin {
    @Inject(method = "render", at = @At("TAIL"))
    private void salts$captureInFrame(CallbackInfo info) {
        org.betterLostItems.salts_anti_aliasing.validation.ValidationScreenshots.afterRender();
    }
}
