package org.betterLostItems.salts_anti_aliasing.mixin.client;

import org.betterLostItems.salts_anti_aliasing.client.render.common.ShaderCompilationExecutor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

import java.util.concurrent.Executor;

/** Preserve the asynchronous supplier and its original deferred GPU-finalization scheduling. */
@Mixin(targets = "com.mojang.renderpearl.frontend.shaders.PipelineBuilder")
public abstract class PipelineBuilderMixin {
    @ModifyArg(method = "compilePipeline", at = @At(value = "INVOKE", target =
            "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"), index = 1)
    private Executor saltsAntiAliasing$nativeCompilerStack(Executor original) {
        return ShaderCompilationExecutor.forPipelineCompilation(original);
    }
}
