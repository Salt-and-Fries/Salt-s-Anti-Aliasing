package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityMotionGeometry;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockEntityRenderDispatcher.class)
abstract class BlockEntityMotionMixin {
    @WrapMethod(method = "submit")
    private void salts$scope(BlockEntityRenderState state, PoseStack pose, SubmitNodeCollector collector,
                              CameraRenderState camera, Operation<Void> operation) {
        boolean entered = EntityMotionGeometry.enterBlock(state.blockPos.asLong());
        try { operation.call(state, pose, collector, camera); }
        finally { if (entered) EntityMotionGeometry.exitSubmission(); }
    }
}
