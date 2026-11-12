package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityTemporalState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
abstract class EntityExtractionMixin {
    @Inject(method = "createRenderState(Lnet/minecraft/world/entity/Entity;F)Lnet/minecraft/client/renderer/entity/state/EntityRenderState;", at = @At("RETURN"))
    private void salts$identity(Entity entity, float partialTick, CallbackInfoReturnable<EntityRenderState> result) {
        ((EntityTemporalState) result.getReturnValue()).saltsAntiAliasing$entityId(entity.getId());
    }
}
