package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.feature.RenderTypeFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;

import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.MovingBlockMotionSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTypeFeatureRenderer.class)
public class RenderTypeFeatureRendererMixin {
    @Inject(method = "getVertexBuilder", at = @At("RETURN"), cancellable = true)
    private void saltsAntiAliasing$captureMovingBlock(
            RenderType layer, CallbackInfoReturnable<VertexConsumer> result) {
        if ((Object) this instanceof MovingBlockMotionSource source) {
            result.setReturnValue(
                    ModernMinecraftHooks.captureSubmittedGeometry(
                            source.saltsAntiAliasing$currentSubmission(),
                            "moving_block",
                            layer,
                            result.getReturnValue()));
        }
    }
}
