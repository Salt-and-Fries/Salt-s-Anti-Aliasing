package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.feature.BlockModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockModelFeatureRenderer.class)
public class BlockModelFeatureRendererMixin {
    @WrapOperation(method = "buildGroup", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/feature/BlockModelFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer saltsAntiAliasing$capture(BlockModelFeatureRenderer renderer, RenderType layer,
            Operation<VertexConsumer> original, @Local BlockModelFeatureRenderer.Submit submit) {
        return ModernMinecraftHooks.captureSubmittedGeometry(submit, "block_model", layer, original.call(renderer, layer));
    }
}
