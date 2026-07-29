package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.systems.CommandEncoderBackend;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vulkan.VulkanCommandEncoder;
import com.mojang.blaze3d.vulkan.VulkanGpuTexture;
import com.mojang.blaze3d.vulkan.VulkanGpuTextureView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrEvaluateParameters;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrOptimalSettings;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;
import org.betterLostItems.salts_anti_aliasing.mixin.client.CommandEncoderAccessor;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.KHRSynchronization2;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;
import org.lwjgl.vulkan.VkDependencyInfo;
import org.lwjgl.vulkan.VkImageMemoryBarrier2;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Owns the AMD FSR2/FSR3 scene target, motion-vector target, reactive-mask inputs,
 * and native FidelityFX dispatch.
 */
public final class VulkanSceneFsrController {
    private static final VulkanSceneFsrController INSTANCE = new VulkanSceneFsrController();
    private static final String SCENE_TARGET_LABEL = "Salt's FSR Scene";
    private static final String MOTION_VECTOR_TARGET_LABEL = "Salt's FSR Motion Vectors";
    private static final String OPAQUE_SCENE_TARGET_LABEL = "Salt's FSR Opaque Scene";
    private static final String REACTIVE_MASK_TARGET_LABEL = "Salt's FSR Reactive Mask";
    private static final String TRANSPARENCY_MASK_TARGET_LABEL = "Salt's FSR Transparency Mask";
    private static final String UPSCALED_COLOR_TARGET_LABEL = "Salt's FSR Upscaled Color";
    private static final String HUDLESS_COLOR_TARGET_LABEL = "Salt's FSR HUDless Color";
    private static final Identifier FSR_SCENE_TARGET_ID = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr_scene");
    private static final Identifier FSR_MOTION_VECTOR_TARGET_ID =
            Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr_motion_vectors");
    private static final Identifier FSR_MOTION_EFFECT =
            Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr_motion_vectors");
    private static final Set<Identifier> FSR_MOTION_TARGETS = Set.of(FSR_SCENE_TARGET_ID, FSR_MOTION_VECTOR_TARGET_ID);
    private static final Vector4f ZERO = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);
    private static final float DEFAULT_CAMERA_NEAR = 0.05f;
    private static final float DEFAULT_CAMERA_FAR = 64.0f;
    private static final float DEFAULT_CAMERA_FOV_Y = (float) Math.toRadians(70.0);
    private static final float VIEW_SPACE_TO_METERS = 1.0f;

    private final CrossFrameResourcePool resourcePool = new CrossFrameResourcePool(3);
    private boolean disabledAfterFailure;
    private boolean destroyResourcesAfterFrame;
    private boolean active;
    private boolean opaqueSceneCaptured;
    private TextureTarget sceneTarget;
    private TextureTarget motionVectorTarget;
    private TextureTarget opaqueSceneTarget;
    private RenderTarget reactiveMaskTarget;
    private TextureTarget transparencyMaskTarget;
    private RenderTarget upscaledColorTarget;
    private TextureTarget hudlessColorTarget;
    private RenderTarget mainTarget;
    private AntiAliasingMode activeMode = AntiAliasingMode.OFF;
    private AntiAliasingConfig activeConfig;
    private FsrOptimalSettings optimalSettings = FsrOptimalSettings.fallback(null, 1, 1);

    private VulkanSceneFsrController() {
    }

    public static VulkanSceneFsrController instance() {
        return INSTANCE;
    }

    public void beginSceneRendering(GameRenderer gameRenderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        destroyResourcesIfPending();
        clearFrameState();

        if (disabledAfterFailure || !isFsrMode(config.mode) || !FsrRuntime.instance().isUpscalingReady()) {
            return;
        }

        if (config.mode.usesFsrFrameGeneration() && !FsrRuntime.instance().isFrameGenerationSwapchainActive()) {
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        RenderTarget mainTarget = gameRenderer.mainRenderTarget();
        if (!mainTarget.useDepth || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        try {
            optimalSettings = FsrRuntime.instance().queryOptimalSettings(
                    config.fsrQualityPreset,
                    mainTarget.width,
                    mainTarget.height
            );
            ensureTargets(optimalSettings.renderWidth(), optimalSettings.renderHeight(), mainTarget.width, mainTarget.height);
            clearAuxiliaryTargets();
            this.mainTarget = mainTarget;
            this.activeMode = config.mode;
            this.activeConfig = config.copy();
            active = true;
        } catch (RuntimeException exception) {
            disableAfterFailure("Disabling AMD FSR after a setup failure", exception);
        }
    }

    public void endSceneRendering(GameRenderer gameRenderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        if (!active) {
            clearFrameState();
            return;
        }

        AntiAliasingConfig frameConfig = activeConfig == null ? config : activeConfig;
        try {
            active = false;
            ensureOpaqueSceneCaptured();
            generateMotionVectors();
            int result = evaluateFsr(frameConfig);
            if (result != 0) {
                SaltsAntiAliasing.LOGGER.warn("AMD FSR evaluate failed with result {}; falling back to linear scene resolve", result);
                resolveSceneColor(sceneTarget, mainTarget);
            } else {
                copyColor(upscaledColorTarget, mainTarget, "Salt's FSR Upscaled Color Copy");
            }

            if (result == 0 && frameConfig.mode.usesFsrFrameGeneration()) {
                copyColor(mainTarget, hudlessColorTarget, "Salt's FSR HUDless Color Copy");
            }
        } catch (RuntimeException exception) {
            disableAfterFailure("Disabling AMD FSR after an evaluate failure", exception);
        } finally {
            resourcePool.endFrame();
            clearFrameState();
        }
    }

    public void captureOpaqueScene() {
        RenderSystem.assertOnRenderThread();
        if (!active || opaqueSceneCaptured || sceneTarget == null || opaqueSceneTarget == null) {
            return;
        }

        copyColor(sceneTarget, opaqueSceneTarget, "Salt's FSR Opaque Scene Copy");
        opaqueSceneCaptured = true;
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

    private int evaluateFsr(AntiAliasingConfig config) {
        if (sceneTarget == null
                || mainTarget == null
                || motionVectorTarget == null
                || opaqueSceneTarget == null
                || reactiveMaskTarget == null
                || transparencyMaskTarget == null
                || upscaledColorTarget == null
                || hudlessColorTarget == null) {
            return -1;
        }

        long inputColorImage = image(sceneTarget.getColorTexture());
        long inputColorView = view(sceneTarget.getColorTextureView());
        long outputColorImage = image(upscaledColorTarget.getColorTexture());
        long outputColorView = view(upscaledColorTarget.getColorTextureView());
        long depthImage = image(sceneTarget.getDepthTexture());
        long depthView = view(sceneTarget.getDepthTextureView());
        long motionVectorImage = image(motionVectorTarget.getColorTexture());
        long motionVectorView = view(motionVectorTarget.getColorTextureView());
        long opaqueColorImage = image(opaqueSceneTarget.getColorTexture());
        long opaqueColorView = view(opaqueSceneTarget.getColorTextureView());
        long reactiveMaskImage = image(reactiveMaskTarget.getColorTexture());
        long reactiveMaskView = view(reactiveMaskTarget.getColorTextureView());
        long transparencyMaskImage = image(transparencyMaskTarget.getColorTexture());
        long transparencyMaskView = view(transparencyMaskTarget.getColorTextureView());
        long hudlessColorImage = image(hudlessColorTarget.getColorTexture());
        long hudlessColorView = view(hudlessColorTarget.getColorTextureView());
        if (inputColorImage == 0L
                || inputColorView == 0L
                || outputColorImage == 0L
                || outputColorView == 0L
                || depthImage == 0L
                || depthView == 0L
                || motionVectorImage == 0L
                || motionVectorView == 0L
                || opaqueColorImage == 0L
                || opaqueColorView == 0L
                || reactiveMaskImage == 0L
                || reactiveMaskView == 0L
                || transparencyMaskImage == 0L
                || transparencyMaskView == 0L
                || hudlessColorImage == 0L
                || hudlessColorView == 0L) {
            return -2;
        }

        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        CommandEncoderBackend backend = ((CommandEncoderAccessor) commandEncoder).saltsAntiAliasing$backend();
        if (!(backend instanceof VulkanCommandEncoder vulkanCommandEncoder)) {
            return -3;
        }

        VulkanSceneTemporalController temporal = VulkanSceneTemporalController.instance();
        CameraParameters cameraParameters = CameraParameters.current();
        VkCommandBuffer commandBuffer = vulkanCommandEncoder.allocateAndBeginTransientCommandBuffer();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            prepareEvaluateResources(commandBuffer, stack);
        }
        FsrEvaluateParameters parameters = new FsrEvaluateParameters(
                commandBuffer.address(),
                fsrVersion(config.mode),
                config.mode.usesFsrFrameGeneration(),
                inputColorImage,
                inputColorView,
                outputColorImage,
                outputColorView,
                depthImage,
                depthView,
                motionVectorImage,
                motionVectorView,
                opaqueColorImage,
                opaqueColorView,
                reactiveMaskImage,
                reactiveMaskView,
                transparencyMaskImage,
                transparencyMaskView,
                hudlessColorImage,
                hudlessColorView,
                sceneTarget.width,
                sceneTarget.height,
                upscaledColorTarget.width,
                upscaledColorTarget.height,
                temporal.currentJitterTexelX(),
                temporal.currentJitterTexelY(),
                temporal.resetHistoryThisFrame(),
                temporal.frameIndex(),
                1.0f,
                1.0f,
                config.fsrSharpness,
                frameTimeDeltaMs(),
                cameraParameters.nearPlane(),
                cameraParameters.farPlane(),
                cameraParameters.fovY(),
                VIEW_SPACE_TO_METERS,
                cameraParameters.positionX(),
                cameraParameters.positionY(),
                cameraParameters.positionZ(),
                cameraParameters.upX(),
                cameraParameters.upY(),
                cameraParameters.upZ(),
                cameraParameters.rightX(),
                cameraParameters.rightY(),
                cameraParameters.rightZ(),
                cameraParameters.forwardX(),
                cameraParameters.forwardY(),
                cameraParameters.forwardZ()
        );

        int result = FsrRuntime.instance().evaluate(parameters);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            completeEvaluateResources(commandBuffer, stack);
        }
        int endResult = VK12.vkEndCommandBuffer(commandBuffer);
        if (endResult != VK12.VK_SUCCESS) {
            return endResult;
        }
        vulkanCommandEncoder.execute(commandBuffer);
        vulkanCommandEncoder.submit();
        return result;
    }

    private void prepareEvaluateResources(VkCommandBuffer commandBuffer, MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(7, stack);
        configureImageBarrier(
                barriers.get(0),
                vulkanTexture(sceneTarget.getColorTexture()),
                VK12.VK_IMAGE_ASPECT_COLOR_BIT,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_SHADER_READ_BIT_KHR
        );
        configureImageBarrier(
                barriers.get(1),
                vulkanTexture(sceneTarget.getDepthTexture()),
                VK12.VK_IMAGE_ASPECT_DEPTH_BIT,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR
                        | KHRSynchronization2.VK_ACCESS_2_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_SHADER_READ_BIT_KHR
        );
        configureImageBarrier(
                barriers.get(2),
                vulkanTexture(motionVectorTarget.getColorTexture()),
                VK12.VK_IMAGE_ASPECT_COLOR_BIT,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_SHADER_READ_BIT_KHR
        );
        configureImageBarrier(
                barriers.get(3),
                vulkanTexture(opaqueSceneTarget.getColorTexture()),
                VK12.VK_IMAGE_ASPECT_COLOR_BIT,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_SHADER_READ_BIT_KHR
        );
        configureImageBarrier(
                barriers.get(4),
                vulkanTexture(reactiveMaskTarget.getColorTexture()),
                VK12.VK_IMAGE_ASPECT_COLOR_BIT,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR
                        | KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_SHADER_WRITE_BIT_KHR
        );
        configureImageBarrier(
                barriers.get(5),
                vulkanTexture(transparencyMaskTarget.getColorTexture()),
                VK12.VK_IMAGE_ASPECT_COLOR_BIT,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_SHADER_READ_BIT_KHR
        );
        configureImageBarrier(
                barriers.get(6),
                vulkanTexture(upscaledColorTarget.getColorTexture()),
                VK12.VK_IMAGE_ASPECT_COLOR_BIT,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR
                        | KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_SHADER_WRITE_BIT_KHR
        );
        pipelineBarrier(commandBuffer, stack, barriers);
    }

    private void completeEvaluateResources(VkCommandBuffer commandBuffer, MemoryStack stack) {
        VkImageMemoryBarrier2.Buffer barriers = VkImageMemoryBarrier2.calloc(1, stack);
        configureImageBarrier(
                barriers.get(0),
                vulkanTexture(upscaledColorTarget.getColorTexture()),
                VK12.VK_IMAGE_ASPECT_COLOR_BIT,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_COMPUTE_SHADER_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_SHADER_WRITE_BIT_KHR,
                KHRSynchronization2.VK_PIPELINE_STAGE_2_ALL_COMMANDS_BIT_KHR,
                KHRSynchronization2.VK_ACCESS_2_MEMORY_READ_BIT_KHR
                        | KHRSynchronization2.VK_ACCESS_2_MEMORY_WRITE_BIT_KHR
        );
        pipelineBarrier(commandBuffer, stack, barriers);
    }

    private static void configureImageBarrier(
            VkImageMemoryBarrier2 barrier,
            VulkanGpuTexture texture,
            int aspectMask,
            long sourceStage,
            long sourceAccess,
            long destinationStage,
            long destinationAccess
    ) {
        barrier.sType$Default()
                .srcStageMask(sourceStage)
                .srcAccessMask(sourceAccess)
                .dstStageMask(destinationStage)
                .dstAccessMask(destinationAccess)
                .oldLayout(VK12.VK_IMAGE_LAYOUT_GENERAL)
                .newLayout(VK12.VK_IMAGE_LAYOUT_GENERAL)
                .srcQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED)
                .dstQueueFamilyIndex(VK12.VK_QUEUE_FAMILY_IGNORED)
                .image(texture.vkImage());
        barrier.subresourceRange()
                .aspectMask(aspectMask)
                .baseMipLevel(0)
                .levelCount(1)
                .baseArrayLayer(0)
                .layerCount(1);
    }

    private static void pipelineBarrier(
            VkCommandBuffer commandBuffer,
            MemoryStack stack,
            VkImageMemoryBarrier2.Buffer barriers
    ) {
        VkDependencyInfo dependencyInfo = VkDependencyInfo.calloc(stack)
                .sType$Default()
                .pImageMemoryBarriers(barriers);
        KHRSynchronization2.vkCmdPipelineBarrier2KHR(commandBuffer, dependencyInfo);
    }

    private void generateMotionVectors() {
        if (sceneTarget == null || motionVectorTarget == null) {
            return;
        }

        VulkanMotionVectorRenderer.fsr().render(
                sceneTarget,
                motionVectorTarget,
                VulkanSceneTemporalController.instance()
        );
    }

    private void ensureTargets(int renderWidth, int renderHeight, int outputWidth, int outputHeight) {
        sceneTarget = ensureTarget(sceneTarget, SCENE_TARGET_LABEL, renderWidth, renderHeight, true, GpuFormat.RGBA8_UNORM);
        motionVectorTarget = ensureTarget(
                motionVectorTarget,
                MOTION_VECTOR_TARGET_LABEL,
                renderWidth,
                renderHeight,
                false,
                GpuFormat.RG16_FLOAT
        );
        opaqueSceneTarget = ensureTarget(
                opaqueSceneTarget,
                OPAQUE_SCENE_TARGET_LABEL,
                renderWidth,
                renderHeight,
                false,
                GpuFormat.RGBA8_UNORM
        );
        reactiveMaskTarget = ensureStorageTarget(
                reactiveMaskTarget,
                REACTIVE_MASK_TARGET_LABEL,
                renderWidth,
                renderHeight,
                GpuFormat.R8_UNORM
        );
        transparencyMaskTarget = ensureTarget(
                transparencyMaskTarget,
                TRANSPARENCY_MASK_TARGET_LABEL,
                renderWidth,
                renderHeight,
                false,
                GpuFormat.R8_UNORM
        );
        hudlessColorTarget = ensureTarget(
                hudlessColorTarget,
                HUDLESS_COLOR_TARGET_LABEL,
                outputWidth,
                outputHeight,
                false,
                GpuFormat.RGBA8_UNORM
        );
        upscaledColorTarget = ensureStorageTarget(
                upscaledColorTarget,
                UPSCALED_COLOR_TARGET_LABEL,
                outputWidth,
                outputHeight,
                GpuFormat.RGBA8_UNORM
        );
    }

    private TextureTarget ensureTarget(
            TextureTarget target,
            String label,
            int width,
            int height,
            boolean useDepth,
            GpuFormat format
    ) {
        if (target == null || target.useDepth != useDepth) {
            if (target != null) {
                target.destroyBuffers();
            }
            return new TextureTarget(label, width, height, useDepth, format);
        }

        if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        return target;
    }

    private RenderTarget ensureStorageTarget(
            RenderTarget target,
            String label,
            int width,
            int height,
            GpuFormat format
    ) {
        if (!(target instanceof VulkanStorageColorTarget)) {
            if (target != null) {
                target.destroyBuffers();
            }
            return new VulkanStorageColorTarget(label, width, height, format);
        }

        if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        return target;
    }

    private void clearAuxiliaryTargets() {
        clearColorTarget(motionVectorTarget);
        clearColorTarget(opaqueSceneTarget);
        clearColorTarget(reactiveMaskTarget);
        clearColorTarget(transparencyMaskTarget);
        opaqueSceneCaptured = false;
    }

    private void clearColorTarget(RenderTarget target) {
        if (target == null || target.getColorTexture() == null) {
            return;
        }

        RenderSystem.getDevice().createCommandEncoder().clearColorTexture(target.getColorTexture(), ZERO);
    }

    private void ensureOpaqueSceneCaptured() {
        if (!opaqueSceneCaptured) {
            captureOpaqueScene();
        }
    }

    private void resolveSceneColor(TextureTarget sceneTarget, RenderTarget mainTarget) {
        if (sceneTarget == null || mainTarget == null) {
            return;
        }

        copyColor(sceneTarget, mainTarget, "Salt's FSR Fallback Resolve");
    }

    private void copyColor(RenderTarget source, RenderTarget destination, String label) {
        if (source == null
                || destination == null
                || source.getColorTexture() == null
                || destination.getColorTexture() == null) {
            return;
        }

        VulkanColorBlitter.blitColor(source, destination);
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
            resourcePool.clear();
        }
        clearFrameState();
        SaltsAntiAliasing.LOGGER.error(message, exception);
    }

    private void destroyResourcesIfPending() {
        if (!destroyResourcesAfterFrame) {
            return;
        }

        destroyResourcesAfterFrame = false;
        destroyResources();
        resourcePool.clear();
    }

    private void destroyResources() {
        destroyTarget(sceneTarget);
        destroyTarget(motionVectorTarget);
        destroyTarget(opaqueSceneTarget);
        destroyTarget(reactiveMaskTarget);
        destroyTarget(transparencyMaskTarget);
        destroyTarget(upscaledColorTarget);
        destroyTarget(hudlessColorTarget);
        sceneTarget = null;
        motionVectorTarget = null;
        opaqueSceneTarget = null;
        reactiveMaskTarget = null;
        transparencyMaskTarget = null;
        upscaledColorTarget = null;
        hudlessColorTarget = null;
    }

    private void destroyTarget(RenderTarget target) {
        if (target != null) {
            target.destroyBuffers();
        }
    }

    private void clearFrameState() {
        active = false;
        opaqueSceneCaptured = false;
        mainTarget = null;
        activeMode = AntiAliasingMode.OFF;
        activeConfig = null;
    }

    private static boolean isFsrMode(AntiAliasingMode mode) {
        return mode == AntiAliasingMode.FSR2_SUPER_RESOLUTION
                || mode == AntiAliasingMode.FSR3_SUPER_RESOLUTION
                || mode == AntiAliasingMode.FSR3_SUPER_RESOLUTION_FRAME_GENERATION;
    }

    private static int fsrVersion(AntiAliasingMode mode) {
        return mode == AntiAliasingMode.FSR2_SUPER_RESOLUTION ? 2 : 3;
    }

    private static float frameTimeDeltaMs() {
        long frameTimeNs = Minecraft.getInstance().getFrameTimeNs();
        return frameTimeNs <= 0L ? 16.6667f : frameTimeNs / 1_000_000.0f;
    }

    private static long image(GpuTexture texture) {
        return texture instanceof VulkanGpuTexture vulkanTexture ? vulkanTexture.vkImage() : 0L;
    }

    private static VulkanGpuTexture vulkanTexture(GpuTexture texture) {
        if (texture instanceof VulkanGpuTexture vulkanTexture) {
            return vulkanTexture;
        }

        throw new IllegalStateException("AMD FSR evaluate requires Vulkan texture objects");
    }

    private static long view(GpuTextureView textureView) {
        return textureView instanceof VulkanGpuTextureView vulkanTextureView ? vulkanTextureView.vkImageView() : 0L;
    }

    private record CameraParameters(
            float nearPlane,
            float farPlane,
            float fovY,
            float positionX,
            float positionY,
            float positionZ,
            float upX,
            float upY,
            float upZ,
            float rightX,
            float rightY,
            float rightZ,
            float forwardX,
            float forwardY,
            float forwardZ
    ) {
        private static CameraParameters current() {
            Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
            if (camera == null || !camera.isInitialized()) {
                return fallback();
            }

            Vec3 position = camera.position();
            Vector3fc up = camera.upVector();
            Vector3fc left = camera.leftVector();
            Vector3fc forward = camera.forwardVector();
            float farPlane = Math.max(DEFAULT_CAMERA_FAR, Minecraft.getInstance().options.getEffectiveRenderDistance() * 16.0f);
            return new CameraParameters(
                    DEFAULT_CAMERA_NEAR,
                    farPlane,
                    (float) Math.toRadians(camera.getFov()),
                    (float) position.x,
                    (float) position.y,
                    (float) position.z,
                    up.x(),
                    up.y(),
                    up.z(),
                    -left.x(),
                    -left.y(),
                    -left.z(),
                    forward.x(),
                    forward.y(),
                    forward.z()
            );
        }

        private static CameraParameters fallback() {
            return new CameraParameters(
                    DEFAULT_CAMERA_NEAR,
                    DEFAULT_CAMERA_FAR,
                    DEFAULT_CAMERA_FOV_Y,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    1.0f,
                    0.0f,
                    1.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    0.0f,
                    -1.0f
            );
        }
    }

    private static final class FsrMotionTargetBundle implements PostChain.TargetBundle {
        private final Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();

        private FsrMotionTargetBundle(ResourceHandle<RenderTarget> sceneHandle, ResourceHandle<RenderTarget> motionHandle) {
            targets.put(FSR_SCENE_TARGET_ID, sceneHandle);
            targets.put(FSR_MOTION_VECTOR_TARGET_ID, motionHandle);
        }

        @Override
        public void replace(Identifier id, ResourceHandle<RenderTarget> handle) {
            targets.put(id, handle);
        }

        @Override
        public ResourceHandle<RenderTarget> get(Identifier id) {
            return targets.getOrDefault(id, ResourceHandle.invalid());
        }
    }
}
