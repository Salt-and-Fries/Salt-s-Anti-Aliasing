package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
abstract class EntityDispatcherMixin {
    @WrapMethod(method = "submit")
    private void salts$scope(EntityRenderState state, CameraRenderState camera, double x, double y, double z,
                              PoseStack pose, SubmitNodeCollector collector, Operation<Void> operation) {
        boolean entered = EntityMotionGeometry.enterEntity(((EntityTemporalState) state).saltsAntiAliasing$entityId());
        try { operation.call(state, camera, x, y, z, pose, collector); }
        finally { if (entered) EntityMotionGeometry.exitSubmission(); }
    }
    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void salts$identity(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> result) {
        ((EntityTemporalState) result.getReturnValue()).saltsAntiAliasing$entityId(entity.getId());
        if (result.getReturnValue() instanceof net.minecraft.client.renderer.entity.state.FallingBlockRenderState falling)
            ((EntityTemporalState) falling.movingBlockRenderState).saltsAntiAliasing$entityId(entity.getId());
    }
}
