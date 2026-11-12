package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.vulkan.VulkanDevice;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanTextureDetail;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.shaders.ShaderType;
import com.mojang.blaze3d.vulkan.glsl.GlslCompiler;
import com.mojang.blaze3d.vulkan.glsl.IntermediaryShaderModule;
import com.mojang.blaze3d.vulkan.glsl.ShaderCompileException;
import org.betterLostItems.salts_anti_aliasing.client.render.common.ShaderCompilationExecutor;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/** Applies optional terrain texture detail when the 26.2 Vulkan backend compiles a variant. */
@Mixin(VulkanDevice.class)
public abstract class VulkanPipelineSourceMixin {
    @Shadow @Final private ShaderSource defaultShaderSource;

    @ModifyVariable(method = "compilePipeline", at = @At("HEAD"), argsOnly = true)
    private ShaderSource saltsAntiAliasing$textureDetailSource(
            ShaderSource source, RenderPipeline pipeline, ShaderSource originalSource) {
        return VulkanTextureDetail.wrapSource(pipeline, source == null ? defaultShaderSource : source);
    }

    // Shader parsing is CPU-only. VkShaderModule and VkPipeline creation stay on the render thread.
    @WrapOperation(method = "compileShader", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/vulkan/glsl/GlslCompiler;createIntermediary(Ljava/lang/String;Ljava/lang/String;Lcom/mojang/blaze3d/shaders/ShaderType;)Lcom/mojang/blaze3d/vulkan/glsl/IntermediaryShaderModule;"))
    private IntermediaryShaderModule saltsAntiAliasing$nativeCompilerStack(
            GlslCompiler compiler, String filename, String source, ShaderType type,
            Operation<IntermediaryShaderModule> original) throws ShaderCompileException {
        try {
            return CompletableFuture.supplyAsync(() -> original.call(compiler, filename, source, type),
                    ShaderCompilationExecutor.forPipelineCompilation(Runnable::run)).join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof ShaderCompileException shaderFailure) throw shaderFailure;
            throw exception;
        }
    }
}
