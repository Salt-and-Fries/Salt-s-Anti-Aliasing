package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BlockFeatureRenderer.class)
abstract class BlockMotionMixin {
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", ordinal = 0))
    private VertexConsumer salts$moving(MultiBufferSource.BufferSource buffers, RenderType type, Operation<VertexConsumer> operation,
                                       @Local SubmitNodeStorage.MovingBlockSubmit submit) {
        return EntityMotionGeometry.captureSubmitted(submit, "moving_block", type, operation.call(buffers, type));
    }
    @WrapOperation(method = "render", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;", ordinal = 1))
    private VertexConsumer salts$model(MultiBufferSource.BufferSource buffers, RenderType type, Operation<VertexConsumer> operation,
                                      @Local SubmitNodeStorage.BlockModelSubmit submit) {
        return EntityMotionGeometry.captureSubmitted(submit, "block_model", type, operation.call(buffers, type));
    }
}
