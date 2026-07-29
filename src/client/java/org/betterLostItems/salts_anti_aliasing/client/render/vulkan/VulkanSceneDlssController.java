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
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssEvaluateParameters;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssOptimalSettings;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssRuntime;
import org.betterLostItems.salts_anti_aliasing.mixin.client.CommandEncoderAccessor;
import org.joml.Vector4f;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkCommandBuffer;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Owns the DLSS Super Resolution scene target, motion-vector target, and native Streamline
 * evaluate call.
 */
public final class VulkanSceneDlssController {
    private static final VulkanSceneDlssController INSTANCE = new VulkanSceneDlssController();
    private static final String SCENE_TARGET_LABEL = "Salt's DLSS Scene";
    private static final String MOTION_VECTOR_TARGET_LABEL = "Salt's DLSS Motion Vectors";
    private static final Identifier DLSS_SCENE_TARGET_ID = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":dlss_scene");
    private static final Identifier DLSS_MOTION_VECTOR_TARGET_ID =
            Identifier.parse(SaltsAntiAliasing.MOD_ID + ":dlss_motion_vectors");
    private static final Identifier DLSS_MOTION_EFFECT =
            Identifier.parse(SaltsAntiAliasing.MOD_ID + ":dlss_motion_vectors");
    private static final Set<Identifier> DLSS_MOTION_TARGETS = Set.of(DLSS_SCENE_TARGET_ID, DLSS_MOTION_VECTOR_TARGET_ID);
    private static final Vector4f ZERO = new Vector4f(0.0f, 0.0f, 0.0f, 0.0f);

    private final CrossFrameResourcePool resourcePool = new CrossFrameResourcePool(3);
    private boolean disabledAfterFailure;
    private boolean destroyResourcesAfterFrame;
    private boolean active;
    private TextureTarget sceneTarget;
    private TextureTarget motionVectorTarget;
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
        if (!mainTarget.useDepth || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        try {
            optimalSettings = DlssRuntime.instance().queryOptimalSettings(
                    config.dlssQualityPreset,
                    mainTarget.width,
                    mainTarget.height
            );
            ensureTargets(optimalSettings.renderWidth(), optimalSettings.renderHeight());
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
            int result = evaluateDlss();
            if (result != 0) {
                SaltsAntiAliasing.LOGGER.warn("DLSS evaluate failed with result {}; falling back to linear scene resolve", result);
                resolveSceneColor(sceneTarget, mainTarget);
            }
        } catch (RuntimeException exception) {
            disableAfterFailure("Disabling DLSS after an evaluate failure", exception);
        } finally {
            resourcePool.endFrame();
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
        long outputColorImage = image(mainTarget.getColorTexture());
        long outputColorView = view(mainTarget.getColorTextureView());
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
            return -2;
        }

        VulkanSceneTemporalController temporal = VulkanSceneTemporalController.instance();
        CommandEncoder commandEncoder = RenderSystem.getDevice().createCommandEncoder();
        CommandEncoderBackend backend = ((CommandEncoderAccessor) commandEncoder).saltsAntiAliasing$backend();
        if (!(backend instanceof VulkanCommandEncoder vulkanCommandEncoder)) {
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
                temporal.previousViewProjectionArray()
        );

        int result = DlssRuntime.instance().evaluate(parameters);
        int endResult = VK12.vkEndCommandBuffer(commandBuffer);
        if (endResult != VK12.VK_SUCCESS) {
            return endResult;
        }
        vulkanCommandEncoder.execute(commandBuffer);
        vulkanCommandEncoder.submit();
        return result;
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

    private void ensureTargets(int width, int height) {
        if (sceneTarget == null) {
            sceneTarget = new TextureTarget(SCENE_TARGET_LABEL, width, height, true, GpuFormat.RGBA8_UNORM);
        } else if (sceneTarget.width != width || sceneTarget.height != height) {
            sceneTarget.resize(width, height);
        }

        if (motionVectorTarget == null) {
            motionVectorTarget = new TextureTarget(MOTION_VECTOR_TARGET_LABEL, width, height, false, GpuFormat.RG16_FLOAT);
        } else if (motionVectorTarget.width != width || motionVectorTarget.height != height) {
            motionVectorTarget.resize(width, height);
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
    }

    private void destroyResourcesIfPending() {
        if (!destroyResourcesAfterFrame) {
            return;
        }

        destroyResourcesAfterFrame = false;
        destroyResources();
    }

    private void destroyResources() {
        if (sceneTarget != null) {
            sceneTarget.destroyBuffers();
            sceneTarget = null;
        }
        if (motionVectorTarget != null) {
            motionVectorTarget.destroyBuffers();
            motionVectorTarget = null;
        }
        resourcePool.clear();
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

    private static final class DlssMotionTargetBundle implements PostChain.TargetBundle {
        private final Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();

        private DlssMotionTargetBundle(ResourceHandle<RenderTarget> sceneHandle, ResourceHandle<RenderTarget> motionHandle) {
            targets.put(DLSS_SCENE_TARGET_ID, sceneHandle);
            targets.put(DLSS_MOTION_VECTOR_TARGET_ID, motionHandle);
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
