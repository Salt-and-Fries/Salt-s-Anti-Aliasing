package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.resource.ResourceHandle;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.util.profiling.ProfilerFiller;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneFsrController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures the opaque scene color before translucent feature rendering so AMD FSR can build a
 * reactive mask from opaque versus full scene color.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Inject(
            method = "lambda$addMainPass$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;executeTranslucent()V"
            )
    )
    private void saltsAntiAliasing$captureFsrOpaqueScene(
            GpuBufferSlice fog,
            LevelRenderState levelRenderState,
            ProfilerFiller profiler,
            ChunkSectionsToRender chunkSections,
            ResourceHandle<RenderTarget> mainHandle,
            FeatureRenderDispatcher.PreparedFrame preparedFrame,
            ResourceHandle<RenderTarget> translucentHandle,
            ResourceHandle<RenderTarget> itemEntityHandle,
            ResourceHandle<RenderTarget> weatherHandle,
            ResourceHandle<RenderTarget> particlesHandle,
            CallbackInfo callbackInfo
    ) {
        VulkanSceneFsrController.instance().captureOpaqueScene();
    }
}
