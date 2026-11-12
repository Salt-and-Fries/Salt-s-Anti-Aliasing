package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ModelFeatureRenderer.class)
abstract class ModelMotionMixin {
    @WrapOperation(method = "renderModel", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/model/Model;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    private void salts$capture(Model<?> model, PoseStack pose, VertexConsumer consumer, int light, int overlay, int tint,
            Operation<Void> operation, @Local(argsOnly = true) SubmitNodeStorage.ModelSubmit<?> submit,
            @Local(argsOnly = true) RenderType type) {
        operation.call(model, pose, EntityMotionGeometry.capture(submit, type, consumer), light, overlay, tint);
    }
}
