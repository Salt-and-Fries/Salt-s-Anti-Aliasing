package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.backend.api.CommandEncoderBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import com.mojang.renderpearl.backend.vulkan.VulkanCommandEncoder;
import com.mojang.renderpearl.backend.vulkan.VulkanConst;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTexture;
import com.mojang.renderpearl.backend.vulkan.VulkanGpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssEvaluateParameters;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssOptimalSettings;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssRuntime;
import org.betterLostItems.salts_anti_aliasing.mixin.client.CommandEncoderAccessor;
import org.joml.Vector4f;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.system.MemoryStack;


/**
 * Owns the DLSS Super Resolution scene target, motion-vector target, and native Streamline
 * evaluate call.
 */
public final class VulkanSceneDlssController {
    private static final VulkanSceneDlssController INSTANCE = new VulkanSceneDlssController();
    private static final String SCENE_TARGET_LABEL = "Salt's DLSS Scene";
    private static final String MOTION_VECTOR_TARGET_LABEL = "Salt's DLSS Motion Vectors";
    private static final Vector4f ZERO = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);

    private boolean disabledAfterFailure;
    private boolean destroyResourcesAfterFrame;
    private boolean active;
    private TextureTarget sceneTarget;
    private TextureTarget motionVectorTarget;
    private VulkanStorageColorTarget outputTarget;
    private TextureTarget previousColorTarget;
    private TextureTarget colorRejectionTarget;
    private boolean colorHistoryValid;
    private RenderTarget mainTarget;
    private DlssOptimalSettings optimalSettings = DlssOptimalSettings.fallback(1, 1);

    private VulkanSceneDlssController() {
    }

    public static VulkanSceneDlssController instance() {
        return INSTANCE;
    }

    public void beginSceneRendering(GameRenderer gameRenderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        destroyResourcesIfPending();
        clearFrameState();

        if (disabledAfterFailure || config.mode != AntiAliasingMode.DLSS_SUPER_RESOLUTION || !DlssRuntime.instance().isReady()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        RenderTarget mainTarget = gameRenderer.mainRenderTarget();
        if (!mainTarget.hasDepth() || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        try {
            optimalSettings = DlssRuntime.instance().queryOptimalSettings(
                    config.dlssQualityPreset,
                    mainTarget.width,
                    mainTarget.height
            );
            ensureTargets(optimalSettings.renderWidth(), optimalSettings.renderHeight(), mainTarget.width, mainTarget.height);
            this.mainTarget = mainTarget;
            active = true;
        } catch (RuntimeException exception) {
            disableAfterFailure("Disabling DLSS after a setup failure", exception);
        }
    }

    public void endSceneRendering(GameRenderer gameRenderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        if (!active) {
            clearFrameState();
            return;
        }

        try {
            active = false;
            generateMotionVectors();
            generateColorRejection();
            int result = evaluateDlss();
            if (result != 0) {
                SaltsAntiAliasing.LOGGER.warn("DLSS evaluate failed with result {}; falling back to linear scene resolve", result);
                MetricsDiagnostics.warn(
                        "dlss.controller",
                        "linear_resolve_fallback",
                        "result", result,
                        "optimalSettings", optimalSettings,
                        "sceneSize", sceneTarget == null ? "unavailable" : sceneTarget.width + "x" + sceneTarget.height,
                        "outputSize", mainTarget == null ? "unavailable" : mainTarget.width + "x" + mainTarget.height
                );
                resolveSceneColor(sceneTarget, mainTarget);
                disabledAfterFailure = true;
                destroyResourcesAfterFrame = true;
            } else {
                VulkanColorBlitter.blitColor(outputTarget, mainTarget);
                MetricsDiagnostics.infoOnce("dlss.evaluate.success." + sceneTarget.width + "x" + sceneTarget.height
                                + "." + mainTarget.width + "x" + mainTarget.height,
                        "dlss.controller", "evaluation_succeeded", "renderWidth", sceneTarget.width,
                        "renderHeight", sceneTarget.height, "outputWidth", mainTarget.width, "outputHeight", mainTarget.height);
            }
            VulkanColorBlitter.blitColor(sceneTarget, previousColorTarget);
            colorHistoryValid = true;
        } catch (RuntimeException exception) {
            try {
                resolveSceneColor(sceneTarget, mainTarget);
            } catch (RuntimeException fallbackException) {
                exception.addSuppressed(fallbackException);
            }
            disableAfterFailure("Disabling DLSS after an evaluate failure", exception);
        } finally {
            clearFrameState();
        }
    }

    public RenderTarget overrideMainTarget() {
        return active ? sceneTarget : null;
    }

    public GpuTexture overrideColorTexture(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getColorTexture();
    }

    public GpuTextureView overrideColorTextureView(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getColorTextureView();
    }

    public GpuTexture overrideDepthTexture(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getDepthTexture();
    }

    public GpuTextureView overrideDepthTextureView(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getDepthTextureView();
    }

    public boolean redirectCopyDepth(RenderTarget target, RenderTarget sourceTarget) {
        RenderTarget redirectedTarget = mappedTarget(target);
        RenderTarget redirectedSource = mappedTarget(sourceTarget);
        if (redirectedTarget == null && redirectedSource == null) {
            return false;
        }

        RenderTarget resolvedTarget = redirectedTarget != null ? redirectedTarget : target;
        RenderTarget resolvedSource = redirectedSource != null ? redirectedSource : sourceTarget;
        if (resolvedTarget == resolvedSource) {
            return true;
        }

        if (resolvedTarget.getDepthTexture() == null || resolvedSource.getDepthTexture() == null) {
            return true;
        }

        resolvedTarget.copyDepthFrom(resolvedSource);
        return true;
    }

    private int evaluateDlss() {
        if (sceneTarget == null || mainTarget == null || motionVectorTarget == null) {
            return -1;
        }
        long inputColorImage = image(sceneTarget.getColorTexture());
        long inputColorView = view(sceneTarget.getColorTextureView());
        long outputColorImage = image(outputTarget.getColorTexture());
        long outputColorView = view(outputTarget.getColorTextureView());
        long depthImage = image(sceneTarget.getDepthTexture());
        long depthView = view(sceneTarget.getDepthTextureView());
        long motionVectorImage = image(motionVectorTarget.getColorTexture());
        long motionVectorView = view(motionVectorTarget.getColorTextureView());
        if (inputColorImage == 0L
                || inputColorView == 0L
                || outputColorImage == 0L
                || outputColorView == 0L
                || depthImage == 0L
                || depthView == 0L
                || motionVectorImage == 0L
                || motionVectorView == 0L) {
            MetricsDiagnostics.warnOnce(
                    "dlss.controller.missing-handles",
                    "dlss.controller",
                    "evaluate_handles_incomplete",
                    "inputColorImage", hex(inputColorImage),
                    "inputColorView", hex(inputColorView),
                    "outputColorImage", hex(outputColorImage),
                    "outputColorView", hex(outputColorView),
                    "depthImage", hex(depthImage),
                    "depthView", hex(depthView),
                    "motionVectorImage", hex(motionVectorImage),
                    "motionVectorView", hex(motionVectorView)
            );
            return -2;
        }

        VulkanSceneTemporalController temporal = VulkanSceneTemporalController.instance();
        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        CommandEncoderBackend backend = ((CommandEncoderAccessor) commandEncoder).saltsAntiAliasing$backend();
        if (!(backend instanceof VulkanCommandEncoder vulkanCommandEncoder)) {
            MetricsDiagnostics.warnOnce(
                    "dlss.controller.command-encoder",
                    "dlss.controller",
                    "non_vulkan_command_encoder",
                    "backendType", backend == null ? "null" : backend.getClass().getName()
            );
            return -3;
        }

        VkCommandBuffer commandBuffer = vulkanCommandEncoder.allocateAndBeginTransientCommandBuffer();
        DlssEvaluateParameters parameters = new DlssEvaluateParameters(
                commandBuffer.address(),
                inputColorImage,
                inputColorView,
                outputColorImage,
                outputColorView,
                depthImage,
                depthView,
                motionVectorImage,
                motionVectorView,
                sceneTarget.width,
                sceneTarget.height,
                mainTarget.width,
                mainTarget.height,
                temporal.currentJitterTexelX(),
                temporal.currentJitterTexelY(),
                temporal.resetHistoryThisFrame(),
                temporal.frameIndex(),
                1.0f / Math.max(1, sceneTarget.width),
                1.0f / Math.max(1, sceneTarget.height),
                temporal.currentViewProjectionArray(),
                temporal.previousViewProjectionArray(),
                temporal.currentProjectionArray(),
                temporal.dlssCameraParameters(),
                resourceFormats(),
                resourceUsages(),
                image(colorRejectionTarget.getColorTexture()),
                view(colorRejectionTarget.getColorTextureView())
        );

        int result;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            nativeResourceBarrier(commandBuffer, stack);
            result = DlssRuntime.instance().evaluate(parameters);
            nativeResourceBarrier(commandBuffer, stack);
        }
        int endResult = VK12.vkEndCommandBuffer(commandBuffer);
        if (endResult != VK12.VK_SUCCESS) {
            MetricsDiagnostics.warn(
                    "dlss.controller",
                    "command_buffer_end_failed",
                    "result", endResult,
                    "commandBuffer", hex(commandBuffer.address())
            );
            return endResult;
        }
        vulkanCommandEncoder.execute(commandBuffer);
        vulkanCommandEncoder.submit();
        return result;
    }

    private GpuTexture[] nativeResources() {
        return new GpuTexture[] {sceneTarget.getColorTexture(), outputTarget.getColorTexture(),
                sceneTarget.getDepthTexture(), motionVectorTarget.getColorTexture(), colorRejectionTarget.getColorTexture()};
    }

    private int[] resourceFormats() {
        return java.util.Arrays.stream(nativeResources()).mapToInt(texture -> VulkanConst.toVk(texture.getFormat())).toArray();
    }

    private int[] resourceUsages() {
        return java.util.Arrays.stream(nativeResources()).mapToInt(texture ->
                VulkanStorageTextureUsage.toVulkanUsage(texture.usage(), texture.getFormat())).toArray();
    }

    private void nativeResourceBarrier(VkCommandBuffer commandBuffer, MemoryStack stack) {
        GpuTexture[] textures = nativeResources();
        var barriers = VkImageMemoryBarrier2.calloc(textures.length, stack);
        for (int index = 0; index < textures.length; index++) {
            var barrier = barriers.get(index).sType$Default()
                    .srcStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR)
                    .srcAccessMask(KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR | KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR)
                    .dstStageMask(KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR)
                    .dstAccessMask(KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR | KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR)
                    .oldLayout(VK12.VK_IMAGE_LAYOUT_GENERAL).newLayout(VK12.VK_IMAGE_LAYOUT_GENERAL)
                    .srcQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED).dstQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED)
                    .image(image(textures[index]));
            barrier.subresourceRange().aspectMask(VulkanConst.formatAspectMask(textures[index].getFormat()))
                    .baseMipLevel(0).levelCount(1).baseArrayLayer(0).layerCount(1);
        }
        KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer,
                VkDependencyInfo.calloc(stack).sType$Default().pImageMemoryBarriers(barriers));
    }

    private void clearMotionVectors() {
        if (motionVectorTarget == null || motionVectorTarget.getColorTexture() == null) {
            return;
        }

        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(motionVectorTarget.getColorTexture(), ZERO);
    }

    private void generateMotionVectors() {
        if (sceneTarget == null || motionVectorTarget == null) {
            return;
        }

        VulkanMotionVectorRenderer.dlss().render(
                sceneTarget,
                motionVectorTarget,
                VulkanSceneTemporalController.instance()
        );
    }

    private void generateColorRejection() {
        var temporal = VulkanSceneTemporalController.instance();
        if (!colorHistoryValid || temporal.resetHistoryThisFrame()) {
            VulkanColorBlitter.blitColor(sceneTarget, previousColorTarget);
        }
        VulkanTemporalColorRejectionRenderer.render(sceneTarget, previousColorTarget,
                motionVectorTarget, colorRejectionTarget, temporal);
    }

    private void ensureTargets(int width, int height, int outputWidth, int outputHeight) {
        if (previousColorTarget == null) {
            previousColorTarget = new TextureTarget("Salt's DLSS Color History", width, height, GpuFormat.RGBA8_UNORM, null);
            // Streamline's Vulkan format conversion supports R16F but omits R8_UNORM.
            colorRejectionTarget = new TextureTarget("Salt's DLSS History Rejection", width, height, GpuFormat.R16_FLOAT, null);
            colorHistoryValid = false;
        } else if (previousColorTarget.width != width || previousColorTarget.height != height) {
            previousColorTarget.resize(width, height);
            colorRejectionTarget.resize(width, height);
            colorHistoryValid = false;
        }
        if (outputTarget == null) {
            outputTarget = new VulkanStorageColorTarget("Salt's DLSS Output", outputWidth, outputHeight, GpuFormat.RGBA8_UNORM);
        } else if (outputTarget.width != outputWidth || outputTarget.height != outputHeight) {
            outputTarget.resize(outputWidth, outputHeight);
        }
        if (sceneTarget == null) {
            sceneTarget = new TextureTarget(SCENE_TARGET_LABEL, width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
            recordTargetChange("created", SCENE_TARGET_LABEL, sceneTarget, GpuFormat.RGBA8_UNORM);
        } else if (sceneTarget.width != width || sceneTarget.height != height) {
            int previousWidth = sceneTarget.width;
            int previousHeight = sceneTarget.height;
            sceneTarget.resize(width, height);
            recordTargetResize(SCENE_TARGET_LABEL, sceneTarget, previousWidth, previousHeight, GpuFormat.RGBA8_UNORM);
        }

        if (motionVectorTarget == null) {
            motionVectorTarget = new TextureTarget(MOTION_VECTOR_TARGET_LABEL, width, height, GpuFormat.RG16_FLOAT, null);
            recordTargetChange("created", MOTION_VECTOR_TARGET_LABEL, motionVectorTarget, GpuFormat.RG16_FLOAT);
        } else if (motionVectorTarget.width != width || motionVectorTarget.height != height) {
            int previousWidth = motionVectorTarget.width;
            int previousHeight = motionVectorTarget.height;
            motionVectorTarget.resize(width, height);
            recordTargetResize(
                    MOTION_VECTOR_TARGET_LABEL,
                    motionVectorTarget,
                    previousWidth,
                    previousHeight,
                    GpuFormat.RG16_FLOAT
            );
        }
    }

    private void resolveSceneColor(TextureTarget sceneTarget, RenderTarget mainTarget) {
        if (sceneTarget == null || mainTarget == null) {
            return;
        }

        VulkanColorBlitter.blitColor(sceneTarget, mainTarget);
    }

    private RenderTarget mappedTarget(RenderTarget target) {
        if (!active || target != mainTarget) {
            return null;
        }

        return sceneTarget;
    }

    private void disableAfterFailure(String message, RuntimeException exception) {
        disabledAfterFailure = true;
        destroyResourcesAfterFrame = mainTarget != null;
        if (!destroyResourcesAfterFrame) {
            destroyResources();
        }
        clearFrameState();
        SaltsAntiAliasing.LOGGER.error(message, exception);
        MetricsDiagnostics.error(
                "dlss.controller",
                "controller_disabled_after_failure",
                exception,
                "message", message,
                "optimalSettings", optimalSettings,
                "destroyResourcesAfterFrame", destroyResourcesAfterFrame
        );
    }

    private void destroyResourcesIfPending() {
        if (!destroyResourcesAfterFrame) {
            return;
        }

        destroyResourcesAfterFrame = false;
        destroyResources();
    }

    private void destroyResources() {
        if (previousColorTarget != null) {
            previousColorTarget.destroyBuffers();
            previousColorTarget = null;
        }
        if (colorRejectionTarget != null) {
            colorRejectionTarget.destroyBuffers();
            colorRejectionTarget = null;
        }
        colorHistoryValid = false;
        if (outputTarget != null) {
            outputTarget.destroyBuffers();
            outputTarget = null;
        }
        if (sceneTarget != null) {
            recordTargetChange("destroyed", SCENE_TARGET_LABEL, sceneTarget, GpuFormat.RGBA8_UNORM);
            sceneTarget.destroyBuffers();
            sceneTarget = null;
        }
        if (motionVectorTarget != null) {
            recordTargetChange("destroyed", MOTION_VECTOR_TARGET_LABEL, motionVectorTarget, GpuFormat.RG16_FLOAT);
            motionVectorTarget.destroyBuffers();
            motionVectorTarget = null;
        }
    }

    private void clearFrameState() {
        active = false;
        mainTarget = null;
    }

    private static long image(GpuTexture texture) {
        return texture instanceof VulkanGpuTexture vulkanTexture ? vulkanTexture.vkImage() : 0L;
    }

    private static long view(GpuTextureView textureView) {
        return textureView instanceof VulkanGpuTextureView vulkanTextureView ? vulkanTextureView.vkImageView() : 0L;
    }

    private static void recordTargetChange(String action, String label, RenderTarget target, GpuFormat format) {
        MetricsDiagnostics.info(
                "dlss.resources",
                "target_" + action,
                "label", label,
                "width", target.width,
                "height", target.height,
                "useDepth", target.hasDepth(),
                "format", format
        );
    }

    private static void recordTargetResize(
            String label,
            RenderTarget target,
            int previousWidth,
            int previousHeight,
            GpuFormat format
    ) {
        MetricsDiagnostics.info(
                "dlss.resources",
                "target_resized",
                "label", label,
                "previousWidth", previousWidth,
                "previousHeight", previousHeight,
                "width", target.width,
                "height", target.height,
                "useDepth", target.hasDepth(),
                "format", format
        );
    }

    private static String hex(long value) {
        return value == 0L ? "0x0" : "0x" + Long.toUnsignedString(value, 16);
    }

}
