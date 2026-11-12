package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelFeatureRenderer.class)
public class ModelFeatureRendererMixin {
    @WrapOperation(
            method = "prepareModel",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void saltsAntiAliasing$captureModel(
            Model<?> model,
            PoseStack pose,
            VertexConsumer consumer,
            int light,
            int overlay,
            int tint,
            Operation<Void> original,
            ModelFeatureRenderer.Submit<?> submit) {
        original.call(
                model,
                pose,
                org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks
                        .captureModelGeometry(submit, consumer),
                light,
                overlay,
                tint);
    }
}
