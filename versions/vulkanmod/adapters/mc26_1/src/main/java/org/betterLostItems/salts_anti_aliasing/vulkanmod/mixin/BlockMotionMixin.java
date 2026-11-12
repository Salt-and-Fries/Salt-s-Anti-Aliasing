package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.feature.BlockFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(BlockFeatureRenderer.class)
abstract class BlockMotionMixin {
    @WrapOperation(method = "renderBlockModelSubmits", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private VertexConsumer salts$model(MultiBufferSource.BufferSource buffers, RenderType type, Operation<VertexConsumer> operation,
                                        @Local SubmitNodeStorage.BlockModelSubmit submit) {
        return EntityMotionGeometry.captureSubmitted(submit, "block_model", type, operation.call(buffers, type));
    }
    @ModifyArg(method = "renderMovingBlockSubmits", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/block/ModelBlockRenderer;tesselateBlock(Lnet/minecraft/client/renderer/block/BlockQuadOutput;FFFLnet/minecraft/client/renderer/block/BlockAndTintGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/client/renderer/block/dispatch/BlockStateModel;J)V"), index = 0)
    private BlockQuadOutput salts$moving(BlockQuadOutput output, @Local SubmitNodeStorage.MovingBlockSubmit submit) {
        return (x, y, z, quad, instance) -> {
            EntityMotionGeometry.enterRendered(submit, "moving_block");
            try { output.put(x, y, z, quad, instance); }
            finally { EntityMotionGeometry.exitRendered(); }
        };
    }
    @WrapOperation(method = "putBakedQuad", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/MultiBufferSource$BufferSource;getBuffer(Lnet/minecraft/client/renderer/rendertype/RenderType;)Lcom/mojang/blaze3d/vertex/VertexConsumer;"))
    private static VertexConsumer salts$movingConsumer(MultiBufferSource.BufferSource buffers, RenderType type, Operation<VertexConsumer> operation) {
        return EntityMotionGeometry.captureRendered(type, operation.call(buffers, type));
    }
}
