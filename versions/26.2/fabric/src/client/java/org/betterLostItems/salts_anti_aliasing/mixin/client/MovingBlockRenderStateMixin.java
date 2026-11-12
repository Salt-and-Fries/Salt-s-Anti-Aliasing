package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.renderer.block.MovingBlockRenderState;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.EntityTemporalState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Retains the owning entity when a moving-block submission is deferred or rebuilt. */
@Mixin(MovingBlockRenderState.class)
public class MovingBlockRenderStateMixin implements EntityTemporalState {
    @Unique private int saltsAntiAliasing$entityId = -1;

    public int saltsAntiAliasing$entityId() {
        return saltsAntiAliasing$entityId;
    }

    public void saltsAntiAliasing$entityId(int id) {
        saltsAntiAliasing$entityId = id;
    }
}
