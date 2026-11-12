package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;

import org.betterLostItems.salts_anti_aliasing.client.platform.modern.ModernMinecraftHooks;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.EntityTemporalState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public class EntityRenderDispatcherMixin {
    @WrapMethod(method = "submit")
    private void saltsAntiAliasing$scope(
            EntityRenderState state,
            CameraRenderState camera,
            double x,
            double y,
            double z,
            PoseStack pose,
            SubmitNodeCollector collector,
            Operation<Void> original) {
        boolean scoped = ModernMinecraftHooks.beginEntityGeometrySubmission(state);
        try {
            original.call(state, camera, x, y, z, pose, collector);
        } finally {
            if (scoped) ModernMinecraftHooks.endGeometrySubmission();
        }
    }

    @Inject(method = "extractEntity", at = @At("RETURN"))
    private void saltsAntiAliasing$rememberIdentity(
            Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> result) {
        ((EntityTemporalState) result.getReturnValue()).saltsAntiAliasing$entityId(entity.getId());
        if (result.getReturnValue()
                instanceof
                net.minecraft.client.renderer.entity.state.FallingBlockRenderState falling) {
            ((EntityTemporalState) falling.movingBlockRenderState)
                    .saltsAntiAliasing$entityId(entity.getId());
        }
    }
}
