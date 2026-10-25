package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.renderer.entity.state.EntityRenderState;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.EntityTemporalState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EntityRenderState.class)
public class EntityRenderStateMixin implements EntityTemporalState {
    @Unique private int saltsAntiAliasing$entityId = -1;

    public int saltsAntiAliasing$entityId() {
        return saltsAntiAliasing$entityId;
    }

    public void saltsAntiAliasing$entityId(int id) {
        saltsAntiAliasing$entityId = id;
    }
}
