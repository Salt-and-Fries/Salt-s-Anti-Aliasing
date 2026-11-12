package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelPartFeatureRenderer;
import net.minecraft.client.renderer.RenderType;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelPartFeatureRenderer.class)
abstract class ModelPartMotionMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/model/geom/ModelPart;render(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V", ordinal = 0))
    private void salts$capture(ModelPart model, PoseStack pose, VertexConsumer consumer, int light, int overlay, int tint,
            Operation<Void> operation, @Local SubmitNodeStorage.ModelPartSubmit submit, @Local RenderType type) {
        operation.call(model, pose, EntityMotionGeometry.captureSubmitted(submit, "model_part", type, consumer), light, overlay, tint);
    }
}
