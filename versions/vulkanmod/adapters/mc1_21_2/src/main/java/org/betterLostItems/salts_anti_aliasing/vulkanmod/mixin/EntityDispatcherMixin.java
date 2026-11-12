package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
abstract class EntityDispatcherMixin {
    @WrapOperation(method = "render(Lnet/minecraft/world/entity/Entity;DDDFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;ILnet/minecraft/client/renderer/entity/EntityRenderer;)V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private <S extends EntityRenderState> void salts$geometry(EntityRenderer<?, S> renderer, S state, PoseStack pose,
            MultiBufferSource buffers, int light, Operation<Void> operation) {
        boolean entered = EntityMotionGeometry.enterEntity(((EntityTemporalState) state).saltsAntiAliasing$entityId());
        MultiBufferSource capture = layer -> EntityMotionGeometry.captureCurrent("entity", layer, buffers.getBuffer(layer));
        try { operation.call(renderer, state, pose, capture, light); }
        finally { if (entered) EntityMotionGeometry.exitSubmission(); }
    }
}
