package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;

import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemFeatureRenderer.class)
public class ItemFeatureRendererMixin {
    @WrapOperation(
            method = "prepareMainSubmit",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/feature/ItemFeatureRenderer;getVertexBuilder(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer saltsAntiAliasing$capture(
            ItemFeatureRenderer renderer,
            RenderType layer,
            Operation<VertexConsumer> original,
            ItemFeatureRenderer.Submit submit) {
        return ModernMinecraftHooks.captureSubmittedGeometry(
                submit, "item", layer, original.call(renderer, layer));
    }
}
