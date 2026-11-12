package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.RenderTuning;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderingHealth;
import org.betterLostItems.salts_anti_aliasing.client.config.NisUpscaleQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Owns GPU-backed scene rendering at non-native resolution for SSAA and spatial upscalers, then
 * resolves the scene back to the main target. This path uses Minecraft's render target and post
 * chain abstractions, so it can run on either 26.3 backend.
 */
public final class VulkanSceneScaleController {
    private static final VulkanSceneScaleController INSTANCE = new VulkanSceneScaleController();
    private static final String TARGET_LABEL = "Salt's Scaled Scene";
    private static final Identifier SCENE_TARGET_ID = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":scene_color");
    private static final Identifier NIS_UPSCALE_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":nis_upscale");
    private static final Identifier SSAA_RESOLVE_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":ssaa_resolve");
    private static final Identifier FSR1_QUALITY_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_upscale_quality");
    private static final Identifier FSR1_BALANCED_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_upscale_balanced");
    private static final Identifier FSR1_PERFORMANCE_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_upscale_performance");
    private static final Identifier FSR1_ULTRA_PERFORMANCE_EFFECT =
            Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_upscale_ultra_performance");
    private static final Set<Identifier> EXTERNAL_SCALE_TARGETS = Set.of(PostChain.MAIN_TARGET_ID, SCENE_TARGET_ID);

    private final CrossFrameResourcePool resourcePool = new CrossFrameResourcePool(3);
    private boolean disabledAfterFailure;
    private String lastConfiguration;
    public void retry() { disabledAfterFailure=false; failedSceneSetupSignature=null; }
    private boolean active;
    private TextureTarget sceneTarget;
    private RenderTarget mainTarget;
    private AntiAliasingMode activeMode = AntiAliasingMode.OFF;
    private String lastRejectedSceneSize;
    private String failedSceneSetupSignature;

    /**
     * Creates a scene scale controller instance with the collaborators or initial state
     * supplied by the caller.
     */
    private VulkanSceneScaleController() {
    }

    /**
     * Handles instance as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return singleton controller instance
     */
    public long allocatedTargetBytes() { return VulkanOwnedTargetMemory.bytes(sceneTarget); }

    public static VulkanSceneScaleController instance() {
        return INSTANCE;
    }

    /**
     * Gives active scene controllers a chance to redirect Minecraft's world rendering into mode-
     * specific targets.
     * @param gameRenderer Minecraft game renderer whose scene target or post-processing phase is
     * being coordinated
     * @param config configuration object being normalized, copied, or committed
     */
    public void beginSceneRendering(GameRenderer gameRenderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        clearFrameState();

        String configuration = config.mode+":"+config.sceneRenderScale()+":"+gameRenderer.mainRenderTarget().width+"x"+gameRenderer.mainRenderTarget().height;
        if (!configuration.equals(lastConfiguration)) { retry(); lastConfiguration=configuration; }
        if (!usesScaledSceneTarget(config.mode)) {
            failedSceneSetupSignature = null;
            destroyResources(); resourcePool.clear();
            return;
        }
        if (disabledAfterFailure) return;
        this.activeMode=config.mode;

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            return;
        }

        RenderTarget mainTarget = gameRenderer.mainRenderTarget();
        if (!mainTarget.hasDepth() || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        try {
            int sceneWidth = Math.max(1, Math.round(mainTarget.width * config.sceneRenderScale()));
            int sceneHeight = Math.max(1, Math.round(mainTarget.height * config.sceneRenderScale()));
            int maxTextureSize = RenderSystem.getDevice().getDeviceInfo().limits().maxTextureSize();
            MetricsDiagnostics.infoOnce(
                    "vulkan.scene-scale.request." + config.mode + "." + mainTarget.width + "x" + mainTarget.height
                            + "." + sceneWidth + "x" + sceneHeight,
                    "vulkan.scene_scale",
                    "target_dimensions_calculated",
                    "mode", config.mode,
                    "sceneRenderScale", config.sceneRenderScale(),
                    "outputWidth", mainTarget.width,
                    "outputHeight", mainTarget.height,
                    "sceneWidth", sceneWidth,
                    "sceneHeight", sceneHeight,
                    "maximumTextureSize", maxTextureSize,
                    "useDepth", mainTarget.hasDepth()
            );
            if (sceneWidth > maxTextureSize || sceneHeight > maxTextureSize) {
                warnUnsupportedSceneSize(sceneWidth, sceneHeight, maxTextureSize);
                RenderingHealth.failure(config.mode,"Requested size exceeds GPU limit; using native resolution");
                return;
            }

            String setupSignature = config.mode
                    + ":" + sceneWidth + "x" + sceneHeight
                    + ":" + mainTarget.hasDepth();
            if (setupSignature.equals(failedSceneSetupSignature)) {
                return;
            }

            lastRejectedSceneSize = null;
            try {
                ensureSceneTarget(sceneWidth, sceneHeight, mainTarget.hasDepth());
            } catch (RuntimeException exception) {
                rejectSceneSetupUntilConfigurationChanges(setupSignature, sceneWidth, sceneHeight, exception);
                return;
            }

            failedSceneSetupSignature = null;
            this.mainTarget = mainTarget;
            this.activeMode = config.mode;
            active = true;
        } catch (RuntimeException exception) {
            disableAfterFailure("Disabling scene scaling after a setup failure", exception);
        }
    }

    /**
     * Resolves any redirected scene output back into the target that the rest of Minecraft expects
     * to read.
     * @param gameRenderer Minecraft game renderer whose scene target or post-processing phase is
     * being coordinated
     * @param config configuration object being normalized, copied, or committed
     */
    public void endSceneRendering(GameRenderer gameRenderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        if (!active) {
            clearFrameState();
            return;
        }

        RenderTarget mainTarget = this.mainTarget;
        TextureTarget sceneTarget = this.sceneTarget;

        try {
            active = false;
            if (mainTarget != null
                    && mainTarget.getColorTextureView() != null
                    && sceneTarget != null
                    && sceneTarget.getColorTextureView() != null) {
                if (usesDedicatedUpscaleShader(config.mode)) {
                    processDedicatedUpscale(Minecraft.getInstance(), sceneTarget, mainTarget, config);
                } else {
                    resolveSceneColor(sceneTarget, mainTarget);
                }
            }
        } catch (RuntimeException exception) {
            disableAfterFailure("Disabling scene scaling after a resolve failure", exception);
        } finally {
            resourcePool.endFrame();
            clearFrameState();
        }
    }

    /**
     * Coordinates override color texture within the anti-aliasing render, configuration, or compatibility flow.
     * @param target target value supplied by the caller or Minecraft callback
     * @return override color texture produced by this helper
     */
    public GpuTexture overrideColorTexture(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getColorTexture();
    }

    /**
     * Coordinates override main target within the anti-aliasing render, configuration, or compatibility flow.
     * @return override main target produced by this helper
     */
    public RenderTarget overrideMainTarget() {
        return active ? sceneTarget : null;
    }

    /**
     * Coordinates override color texture view within the anti-aliasing render, configuration, or compatibility flow.
     * @param target target value supplied by the caller or Minecraft callback
     * @return override color texture view produced by this helper
     */
    public GpuTextureView overrideColorTextureView(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getColorTextureView();
    }

    /**
     * Coordinates override depth texture within the anti-aliasing render, configuration, or compatibility flow.
     * @param target target value supplied by the caller or Minecraft callback
     * @return override depth texture produced by this helper
     */
    public GpuTexture overrideDepthTexture(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getDepthTexture();
    }

    /**
     * Coordinates override depth texture view within the anti-aliasing render, configuration, or compatibility flow.
     * @param target target value supplied by the caller or Minecraft callback
     * @return override depth texture view produced by this helper
     */
    public GpuTextureView overrideDepthTextureView(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getDepthTextureView();
    }

    /**
     * Coordinates redirect copy depth within the anti-aliasing render, configuration, or compatibility flow.
     * @param target target value supplied by the caller or Minecraft callback
     * @param sourceTarget source target value supplied by the caller or Minecraft callback
     * @return whether the operation or state is enabled
     */
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

        if (!canCopyDepth(resolvedTarget, resolvedSource)) {
            return true;
        }

        resolvedTarget.copyDepthFrom(resolvedSource);
        return true;
    }

    /**
     * Coordinates ensure scene target within the anti-aliasing render, configuration, or compatibility flow.
     * @param width width value supplied by the caller or Minecraft callback
     * @param height height value supplied by the caller or Minecraft callback
     * @param useDepth use depth value supplied by the caller or Minecraft callback
     */
    private void ensureSceneTarget(int width, int height, boolean useDepth) {
        if (sceneTarget == null || sceneTarget.hasDepth() != useDepth) {
            destroyResources();
            sceneTarget = new TextureTarget(TARGET_LABEL, width, height, GpuFormat.RGBA8_UNORM, useDepth ? GpuFormat.D32_FLOAT : null);
            MetricsDiagnostics.info(
                    "vulkan.scene_scale",
                    "target_created",
                    "label", TARGET_LABEL,
                    "activeMode", activeMode,
                    "width", width,
                    "height", height,
                    "useDepth", useDepth,
                    "format", GpuFormat.RGBA8_UNORM
            );
            return;
        }

        if (sceneTarget.width != width || sceneTarget.height != height) {
            int previousWidth = sceneTarget.width;
            int previousHeight = sceneTarget.height;
            sceneTarget.resize(width, height);
            MetricsDiagnostics.info(
                    "vulkan.scene_scale",
                    "target_resized",
                    "label", TARGET_LABEL,
                    "activeMode", activeMode,
                    "previousWidth", previousWidth,
                    "previousHeight", previousHeight,
                    "width", width,
                    "height", height,
                    "useDepth", useDepth,
                    "format", GpuFormat.RGBA8_UNORM
            );
        }
    }

    /**
     * Warns once for a rejected size while leaving scene scaling able to recover when the player
     * lowers the SSAA setting or resizes the window.
     * @param width requested scaled scene width
     * @param height requested scaled scene height
     * @param maxTextureSize maximum two-dimensional texture size supported by the active GPU
     */
    private void warnUnsupportedSceneSize(int width, int height, int maxTextureSize) {
        String rejectedSceneSize = width + "x" + height + ":" + maxTextureSize;
        if (rejectedSceneSize.equals(lastRejectedSceneSize)) {
            return;
        }

        lastRejectedSceneSize = rejectedSceneSize;
        SaltsAntiAliasing.LOGGER.warn(
                "Skipping scaled scene target {}x{} because the active GPU supports at most {}x{}; "
                        + "lower the SSAA scale or output resolution",
                width,
                height,
                maxTextureSize,
                maxTextureSize
        );
        MetricsDiagnostics.warn(
                "vulkan.scene_scale",
                "target_size_unsupported",
                "requestedWidth", width,
                "requestedHeight", height,
                "maximumTextureSize", maxTextureSize,
                "activeMode", activeMode,
                "fallback", "native resolution"
        );
    }

    /**
     * Treats an extreme scaled-target allocation failure as configuration-specific. The same
     * impossible target is not retried every frame, while lowering SSAA or resizing the window
     * immediately produces a new signature and can recover without restarting Minecraft.
     * @param setupSignature mode, dimensions, and depth shape of the failed target request
     * @param width requested scaled scene width
     * @param height requested scaled scene height
     * @param exception allocation or resize failure raised by the graphics backend
     */
    private void rejectSceneSetupUntilConfigurationChanges(
            String setupSignature,
            int width,
            int height,
            RuntimeException exception
    ) {
        failedSceneSetupSignature = setupSignature;
        RenderingHealth.failure(activeMode,"Target allocation failed; lower the scale or output size");
        destroyResources();
        resourcePool.clear();
        clearFrameState();
        SaltsAntiAliasing.LOGGER.error(
                "Could not allocate scaled scene target {}x{}; rendering at native resolution until the "
                        + "SSAA scale or window size changes",
                width,
                height,
                exception
        );
        MetricsDiagnostics.error(
                "vulkan.scene_scale",
                "target_allocation_failed",
                exception,
                "setupSignature", setupSignature,
                "width", width,
                "height", height,
                "activeMode", activeMode,
                "fallback", "native resolution until configuration changes"
        );
    }

    /**
     * Resolves scene color into a safe fallback or final render value.
     * @param sceneTarget scene target value supplied by the caller or Minecraft callback
     * @param mainTarget main target value supplied by the caller or Minecraft callback
     */
    private void resolveSceneColor(TextureTarget sceneTarget, RenderTarget mainTarget) {
        VulkanColorBlitter.blitColor(sceneTarget, mainTarget);
    }

    /**
     * Coordinates process dedicated upscale within the anti-aliasing render, configuration, or compatibility flow.
     * @param minecraft minecraft value supplied by the caller or Minecraft callback
     * @param sceneTarget scene target value supplied by the caller or Minecraft callback
     * @param mainTarget main target value supplied by the caller or Minecraft callback
     * @param config configuration object being normalized, copied, or committed
     */
    private void processDedicatedUpscale(
            Minecraft minecraft,
            TextureTarget sceneTarget,
            RenderTarget mainTarget,
            AntiAliasingConfig config
    ) {
        if (config.mode != AntiAliasingMode.SSAA && config.tuning.spatialAa != RenderTuning.SpatialAa.OFF) {
            String preEffect = config.tuning.spatialAa == RenderTuning.SpatialAa.SMAA ? "smaa" : "fxaa";
            PostChain pre = minecraft.getShaderManager().getPostChain(
                    Identifier.parse(SaltsAntiAliasing.MOD_ID+":"+preEffect), Set.of(PostChain.MAIN_TARGET_ID));
            if (pre == null) throw new IllegalStateException("Spatial pre-AA shader unavailable");
            VulkanDynamicUniforms.updateTuning(pre,config);
            try(var gpuTiming=VulkanGpuProfiler.scope(config.mode+".pre_"+preEffect)) { pre.process(sceneTarget,resourcePool); }
        }
        Identifier effectId = upscaleEffectFor(config);
        if (effectId == null) {
            resolveSceneColor(sceneTarget, mainTarget);
            return;
        }

        PostChain postChain = minecraft.getShaderManager().getPostChain(effectId, EXTERNAL_SCALE_TARGETS);
        if (postChain == null) {
            MetricsDiagnostics.warnOnce(
                    "scene-scale-post-chain-missing-" + effectId,
                    "vulkan.scene_scale",
                    "upscale_post_chain_unavailable",
                    "effect", effectId,
                    "mode", config.mode,
                    "fallback", "linear color resolve"
            );
            resolveSceneColor(sceneTarget, mainTarget);
            RenderingHealth.failure(config.mode,"Upscale shader unavailable; using linear resolve");
            return;
        }

        MetricsDiagnostics.infoOnce(
                "scene-scale-post-chain-active-" + effectId,
                "vulkan.scene_scale",
                "upscale_post_chain_activated",
                "effect", effectId,
                "mode", config.mode,
                "inputWidth", sceneTarget.width,
                "inputHeight", sceneTarget.height,
                "outputWidth", mainTarget.width,
                "outputHeight", mainTarget.height
        );

        // Upscaling itself never chooses a sharpening amount; the universal final pass owns it.
        VulkanDynamicUniforms.updateTuning(postChain, config);

        FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
        ResourceHandle<RenderTarget> mainHandle = frameGraphBuilder.importExternal("salts_upscale_main", mainTarget);
        ResourceHandle<RenderTarget> sceneHandle = frameGraphBuilder.importExternal("salts_upscale_scene", sceneTarget);
        SceneScaleTargetBundle targetBundle = new SceneScaleTargetBundle(mainHandle, sceneHandle);
        postChain.addToFrame(frameGraphBuilder, mainTarget.width, mainTarget.height, targetBundle);
            try(var gpuTiming=VulkanGpuProfiler.scope(config.mode+".resolve")) { frameGraphBuilder.execute(resourcePool); }
        RenderingHealth.success(config.mode,sceneTarget.width,sceneTarget.height);
    }

    /**
     * Coordinates upscale effect for within the anti-aliasing render, configuration, or compatibility flow.
     * @param config configuration object being normalized, copied, or committed
     * @return upscale effect for produced by this helper
     */
    private static Identifier upscaleEffectFor(AntiAliasingConfig config) {
        if (config.mode == AntiAliasingMode.SSAA) {
            return SSAA_RESOLVE_EFFECT;
        }
        return switch (config.mode) {
            case NIS_UPSCALE -> NIS_UPSCALE_EFFECT;
            case FSR1_UPSCALE -> fsr1EffectFor(config.nisUpscaleQualityPreset);
            default -> null;
        };
    }

    /**
     * Handles fsr1 effect for as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param preset quality preset selected by the user or loaded from config
     * @return fsr1 effect for produced by this helper
     */
    private static Identifier fsr1EffectFor(NisUpscaleQualityPreset preset) {
        return switch (NisUpscaleQualityPreset.clamp(preset)) {
            case QUALITY -> FSR1_QUALITY_EFFECT;
            case BALANCED -> FSR1_BALANCED_EFFECT;
            case PERFORMANCE -> FSR1_PERFORMANCE_EFFECT;
            case ULTRA_PERFORMANCE -> FSR1_ULTRA_PERFORMANCE_EFFECT;
        };
    }

    /**
     * Resolves pass label into a safe fallback or final render value.
     * @return resolve pass label produced by this helper
     */
    private String resolvePassLabel() {
        return switch (activeMode) {
            case SSAA -> "Salt's SSAA Resolve";
            case FSR1_UPSCALE -> "Salt's FSR1 Upscale Resolve";
            case NIS_UPSCALE -> "Salt's NIS Upscale Resolve";
            default -> "Salt's Scene Resolve";
        };
    }

    /**
     * Handles mapped target as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param target target value supplied by the caller or Minecraft callback
     * @return mapped target produced by this helper
     */
    private RenderTarget mappedTarget(RenderTarget target) {
        if (!active || target != mainTarget) {
            return null;
        }

        return sceneTarget;
    }

    /**
     * Coordinates disable after failure within the anti-aliasing render, configuration, or compatibility flow.
     * @param message message value supplied by the caller or Minecraft callback
     * @param exception exception value supplied by the caller or Minecraft callback
     */
    private void disableAfterFailure(String message, RuntimeException exception) {
        AntiAliasingMode failedMode = activeMode;
        disabledAfterFailure = true;
        RenderingHealth.failure(failedMode, exception.getMessage());
        destroyResources();
        resourcePool.clear();
        clearFrameState();
        SaltsAntiAliasing.LOGGER.error(message, exception);
        MetricsDiagnostics.error(
                "vulkan.scene_scale",
                "controller_disabled_after_failure",
                exception,
                "message", message,
                "activeMode", failedMode
        );
    }

    /**
     * Coordinates destroy resources within the anti-aliasing render, configuration, or compatibility flow.
     */
    private void destroyResources() {
        if (sceneTarget != null) {
            MetricsDiagnostics.info(
                    "vulkan.scene_scale",
                    "target_destroyed",
                    "label", TARGET_LABEL,
                    "width", sceneTarget.width,
                    "height", sceneTarget.height,
                    "useDepth", sceneTarget.hasDepth(),
                    "activeMode", activeMode
            );
            sceneTarget.destroyBuffers();
            sceneTarget = null;
        }
    }

    /**
     * Checks uses scaled scene target without mutating runtime or configuration state.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @return whether this object requires the described render path
     */
    private static boolean usesScaledSceneTarget(AntiAliasingMode mode) {
        return mode == AntiAliasingMode.SSAA
                || mode == AntiAliasingMode.NIS_UPSCALE
                || mode == AntiAliasingMode.FSR1_UPSCALE;
    }

    /**
     * Checks uses dedicated upscale shader without mutating runtime or configuration state.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @return whether this object requires the described render path
     */
    private static boolean usesDedicatedUpscaleShader(AntiAliasingMode mode) {
        return mode == AntiAliasingMode.SSAA || mode == AntiAliasingMode.NIS_UPSCALE
                || mode == AntiAliasingMode.FSR1_UPSCALE;
    }

    /**
     * Handles can copy depth as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param target target value supplied by the caller or Minecraft callback
     * @param source source value supplied by the caller or Minecraft callback
     * @return whether the requested operation is currently allowed
     */
    private static boolean canCopyDepth(RenderTarget target, RenderTarget source) {
        GpuTexture targetDepth = target.getDepthTexture();
        GpuTexture sourceDepth = source.getDepthTexture();
        if (targetDepth == null || sourceDepth == null) {
            return false;
        }

        return targetDepth.getFormat() == sourceDepth.getFormat()
                && targetDepth.getWidth(0) == sourceDepth.getWidth(0)
                && targetDepth.getHeight(0) == sourceDepth.getHeight(0);
    }

    /**
     * Coordinates clear frame state within the anti-aliasing render, configuration, or compatibility flow.
     */
    private void clearFrameState() {
        active = false;
        mainTarget = null;
        activeMode = AntiAliasingMode.OFF;
    }

    /**
     * Implements scene scale target bundle behavior for Salt's Anti Aliasing. This code owns
     * render-target redirection, post-processing, and Minecraft framebuffer coordination.
     */
    private static final class SceneScaleTargetBundle implements PostChain.TargetBundle {
        private final Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();

        /**
         * Coordinates scene scale target bundle within the anti-aliasing render, configuration, or compatibility flow.
         * @param mainHandle main handle value supplied by the caller or Minecraft callback
         * @param sceneHandle scene handle value supplied by the caller or Minecraft callback
         */
        private SceneScaleTargetBundle(ResourceHandle<RenderTarget> mainHandle, ResourceHandle<RenderTarget> sceneHandle) {
            targets.put(PostChain.MAIN_TARGET_ID, mainHandle);
            targets.put(SCENE_TARGET_ID, sceneHandle);
        }

        /**
         * Handles replace as part of the anti-aliasing render, configuration, or compatibility
         * flow.
         * @param id id value supplied by the caller or Minecraft callback
         * @param handle handle value supplied by the caller or Minecraft callback
         */
        @Override
        public void replace(Identifier id, ResourceHandle<RenderTarget> handle) {
            targets.put(id, handle);
        }

        /**
         * Returns get for callers that need to coordinate UI, mixin, or render behavior.
         * @param id id value supplied by the caller or Minecraft callback
         * @return the requested Minecraft or renderer object
         */
        @Override
        public ResourceHandle<RenderTarget> get(Identifier id) {
            return targets.getOrDefault(id, ResourceHandle.invalid());
        }
    }
}
