package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionsToRender;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneFsrController;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneMsaaController;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Captures opaque FSR color between solid and translucent rendering, outside a render pass.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {
    @Shadow
    private void executeClassicTransparency(
            ChunkSectionsToRender sections, FeatureRenderDispatcher.PreparedFrame frame, RenderPass pass
    ) {
        throw new AssertionError();
    }

    @Redirect(method = "lambda$addMainPass$0", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/LevelRenderer;executeClassicTransparency(Lnet/minecraft/client/renderer/chunk/ChunkSectionsToRender;Lnet/minecraft/client/renderer/feature/FeatureRenderDispatcher$PreparedFrame;Lcom/mojang/renderpearl/api/commands/RenderPass;)V"))
    private void saltsAntiAliasing$captureBeforeClassicTransparency(
            LevelRenderer renderer, ChunkSectionsToRender sections,
            FeatureRenderDispatcher.PreparedFrame frame, RenderPass solidPass
    ) {
        VulkanSceneFsrController controller = VulkanSceneFsrController.instance();
        RenderTarget scene = controller.overrideMainTarget();
        if (scene == null) {
            executeClassicTransparency(sections, frame, solidPass);
            return;
        }

        // 26.3 combines solid and classic transparency in one pass. End it before copying;
        // the outer try-with-resources can safely close this already closed frontend pass.
        solidPass.close();
        controller.captureOpaqueScene();
        try (RenderPass translucentPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Salt's FSR Classic Transparency", scene.getColorTextureView(), Optional.empty(),
                scene.getDepthTextureView(), OptionalDouble.empty()
        )) {
            RenderSystem.bindDefaultUniforms(translucentPass);
            executeClassicTransparency(sections, frame, translucentPass);
        }
    }

    @Inject(method = "executeOit", at = @At("HEAD"))
    private void saltsAntiAliasing$captureBeforeImprovedTransparency(CallbackInfo callbackInfo) {
        VulkanSceneMsaaController.instance().prepareTransparencyDepth();
        VulkanSceneFsrController.instance().captureOpaqueScene();
    }

    @Redirect(method = "executeOit", at = @At(value = "INVOKE", ordinal = 0, target =
            "Lcom/mojang/blaze3d/pipeline/RenderTarget;getDepthTextureView()Lcom/mojang/renderpearl/api/textures/GpuTextureView;"))
    private GpuTextureView saltsAntiAliasing$resolvedOitDepth(RenderTarget target) {
        return VulkanSceneMsaaController.instance().transparencyDepthView(target);
    }

    @Redirect(method = "executeDepthBoundsCull", at = @At(value = "INVOKE", target =
            "Lcom/mojang/blaze3d/pipeline/RenderTarget;getDepthTextureView()Lcom/mojang/renderpearl/api/textures/GpuTextureView;"))
    private GpuTextureView saltsAntiAliasing$matchingDepthBoundsSamples(RenderTarget target) {
        return VulkanSceneMsaaController.instance().transparencyDepthView(target);
    }

    @Redirect(method = "executeOitWaterMask", at = @At(value = "INVOKE", ordinal = 1, target =
            "Lcom/mojang/blaze3d/pipeline/RenderTarget;getDepthTextureView()Lcom/mojang/renderpearl/api/textures/GpuTextureView;"))
    private GpuTextureView saltsAntiAliasing$sampledWaterMaskDepth(RenderTarget target) {
        return VulkanSceneMsaaController.instance().transparencyDepthView(target);
    }
}
