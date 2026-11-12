package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import java.util.function.Supplier;
import net.fabricmc.fabric.api.renderer.v1.render.RenderContext;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import net.vulkanmod.render.chunk.build.frapi.render.AbstractBlockRenderContext;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyBakedModelConsumer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** VulkanMod's 1.20.1 backport also runs against the original 1.20 renderer API. */
@Mixin(value = AbstractBlockRenderContext.class, remap = false)
abstract class LegacyFabricBlockContextMixin {
    @Shadow protected BlockState blockState;
    @Shadow @Final public Supplier<RandomSource> randomSupplier;
    @Shadow public abstract void emitBlockQuads(BakedModel model, BlockState state,
                                                Supplier<RandomSource> random, RenderContext context);

    @Unique private final RenderContext.BakedModelConsumer salts$modelConsumer =
            new LegacyBakedModelConsumer(() -> blockState, (model, state) ->
                    emitBlockQuads(model, state, randomSupplier, (RenderContext) (Object) this));

    @Inject(method = "bakedModelConsumer", at = @At("HEAD"), cancellable = true)
    private void salts$legacyConsumer(CallbackInfoReturnable<RenderContext.BakedModelConsumer> info) {
        info.setReturnValue(salts$modelConsumer);
    }

    @Redirect(method = "emitBlockQuads", at = @At(value = "INVOKE", target =
            "Lnet/fabricmc/fabric/api/renderer/v1/render/RenderContext;isFaceCulled(Lnet/minecraft/core/Direction;)Z"))
    private boolean salts$legacyCulling(RenderContext context, Direction face) {
        // isFaceCulled was added to the Fabric interface after 1.20, but the
        // native block context already implements the same culling operation.
        return ((AbstractBlockRenderContext) context).isFaceCulled(face);
    }
}
