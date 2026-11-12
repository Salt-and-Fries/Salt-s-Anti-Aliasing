package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.motion.EntityTemporalState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin({EntityRenderState.class, MovingBlockRenderState.class})
abstract class EntityStateMixin implements EntityTemporalState {
    @Unique private int salts$identity = -1;
    public int saltsAntiAliasing$entityId() { return salts$identity; }
    public void saltsAntiAliasing$entityId(int id) { salts$identity = id; }
}
