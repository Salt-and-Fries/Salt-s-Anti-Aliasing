package org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy;

import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.level.block.state.BlockState;

/** Bridges both overloads of the original Fabric renderer model consumer. */
public final class LegacyBakedModelConsumer implements RenderContext.BakedModelConsumer {
    private final Supplier<BlockState> currentState;
    private final BiConsumer<BakedModel, BlockState> renderer;

    public LegacyBakedModelConsumer(Supplier<BlockState> currentState,
                                    BiConsumer<BakedModel, BlockState> renderer) {
        this.currentState = currentState;
        this.renderer = renderer;
    }

    @Override public void accept(BakedModel model) { accept(model, currentState.get()); }
    @Override public void accept(BakedModel model, BlockState state) { renderer.accept(model, state); }
}
