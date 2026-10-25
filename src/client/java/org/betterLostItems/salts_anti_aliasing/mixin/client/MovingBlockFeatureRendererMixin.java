package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.feature.FeatureFrameContext;
import net.minecraft.client.renderer.feature.MovingBlockFeatureRenderer;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.MovingBlockMotionSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/** Tracks the submit before either vanilla or Fabric's replacement tessellator emits vertices. */
@Mixin(MovingBlockFeatureRenderer.class)
public class MovingBlockFeatureRendererMixin implements MovingBlockMotionSource {
    @Unique private MovingBlockFeatureRenderer.Submit saltsAntiAliasing$current;

    public MovingBlockFeatureRenderer.Submit saltsAntiAliasing$currentSubmission() {
        return saltsAntiAliasing$current;
    }

    @WrapOperation(
            method = "buildGroup",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/feature/MovingBlockFeatureRenderer$Submit;movingBlockRenderState()Lnet/minecraft/client/renderer/block/MovingBlockRenderState;"))
    private MovingBlockRenderState saltsAntiAliasing$selectSubmit(
            MovingBlockFeatureRenderer.Submit submit, Operation<MovingBlockRenderState> original) {
        saltsAntiAliasing$current = submit;
        return original.call(submit);
    }

    @Inject(method = "buildGroup", at = @At("TAIL"))
    private void saltsAntiAliasing$clearSubmit(
            FeatureFrameContext context,
            List<MovingBlockFeatureRenderer.Submit> submits,
            CallbackInfo callback) {
        saltsAntiAliasing$current = null;
    }
}
