package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(EntityRenderDispatcher.class)
abstract class EntityDispatcherMixin {
    @WrapOperation(method = "render(Lnet/minecraft/world/entity/Entity;DDDFFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/entity/EntityRenderer;render(Lnet/minecraft/world/entity/Entity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V"))
    private void salts$geometry(EntityRenderer<?> renderer, Entity entity, float yaw, float tick, PoseStack pose,
            MultiBufferSource buffers, int light, Operation<Void> operation) {
        boolean entered = EntityMotionGeometry.enterEntity(entity.getId());
        MultiBufferSource capture = layer -> EntityMotionGeometry.captureCurrent("entity", layer, buffers.getBuffer(layer));
        try { operation.call(renderer, entity, yaw, tick, pose, capture, light); }
        finally { if (entered) EntityMotionGeometry.exitSubmission(); }
    }
}
