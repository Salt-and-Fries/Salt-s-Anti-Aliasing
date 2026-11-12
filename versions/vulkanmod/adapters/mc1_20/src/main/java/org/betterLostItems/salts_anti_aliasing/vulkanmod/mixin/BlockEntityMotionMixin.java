package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityMotionMixin {
    @WrapMethod(method = "render")
    private void salts$geometry(BlockEntity entity, float partialTick, PoseStack pose, MultiBufferSource buffers, Operation<Void> operation) {
        boolean entered = EntityMotionGeometry.enterBlock(entity.getBlockPos().asLong());
        MultiBufferSource capture = layer -> EntityMotionGeometry.captureCurrent("block_entity", layer, buffers.getBuffer(layer));
        try { operation.call(entity, partialTick, pose, capture); }
        finally { if (entered) EntityMotionGeometry.exitSubmission(); }
    }
}
