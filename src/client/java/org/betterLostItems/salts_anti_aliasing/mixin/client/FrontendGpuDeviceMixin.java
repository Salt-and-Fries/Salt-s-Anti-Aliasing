package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.renderpearl.api.pipeline.CompiledRenderPipeline;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.ShaderSource;
import com.mojang.renderpearl.frontend.FrontendGpuDevice;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanMsaaPipelineVariants;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Retains the source of asynchronously compiled pipelines for MSAA variants. */
@Mixin(FrontendGpuDevice.class)
public abstract class FrontendGpuDeviceMixin {
    @ModifyVariable(method="compilePipeline",at=@At("HEAD"),argsOnly=true)
    private ShaderSource saltsAntiAliasing$textureDetailSource(ShaderSource source,RenderPipeline pipeline,ShaderSource originalSource,Executor executor) {
        return org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanTextureDetail.wrapSource(pipeline,source);
    }
    @Inject(method = "compilePipeline", at = @At("RETURN"), cancellable = true)
    private void saltsAntiAliasing$rememberPipelineSource(
            RenderPipeline pipeline,
            ShaderSource shaderSource,
            Executor executor,
            CallbackInfoReturnable<CompletableFuture<CompiledRenderPipeline.Pending>> callbackInfo
    ) {
        callbackInfo.setReturnValue(callbackInfo.getReturnValue().thenApply(pending ->
                () -> VulkanMsaaPipelineVariants.rememberSource(pipeline, pending.finishCompile())));
    }
}
