package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ItemFeatureRenderer;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ItemFeatureRenderer.class)
abstract class ItemMotionMixin {
    @WrapOperation(method = "renderItem", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vertex/VertexConsumer;putBakedQuad(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lnet/minecraft/client/resources/model/geometry/BakedQuad;Lcom/mojang/blaze3d/vertex/QuadInstance;)V", ordinal = 2))
    private void salts$capture(VertexConsumer consumer, PoseStack.Pose pose, BakedQuad quad, QuadInstance instance,
                              Operation<Void> operation, @Local(argsOnly = true) SubmitNodeStorage.ItemSubmit submit) {
        operation.call(EntityMotionGeometry.captureSubmitted(submit, "item", quad.materialInfo().itemRenderType(), consumer), pose, quad, instance);
    }
}
