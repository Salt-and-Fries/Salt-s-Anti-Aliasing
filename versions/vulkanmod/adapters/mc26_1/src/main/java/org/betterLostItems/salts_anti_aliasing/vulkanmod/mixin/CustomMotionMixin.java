package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.CustomFeatureRenderer;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(CustomFeatureRenderer.class)
abstract class CustomMotionMixin {
    @WrapOperation(method = {"renderSolid", "renderTranslucent"}, at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SubmitNodeCollector$CustomGeometryRenderer;render(Lcom/mojang/blaze3d/vertex/PoseStack$Pose;Lcom/mojang/blaze3d/vertex/VertexConsumer;)V"))
    private void salts$capture(SubmitNodeCollector.CustomGeometryRenderer renderer, PoseStack.Pose pose, VertexConsumer consumer,
            Operation<Void> operation, @Local SubmitNodeStorage.CustomGeometrySubmit submit, @Local java.util.Map.Entry<?, ?> group) {
        operation.call(renderer, pose, EntityMotionGeometry.captureSubmitted(submit, "custom", group.getKey(), consumer));
    }
}
