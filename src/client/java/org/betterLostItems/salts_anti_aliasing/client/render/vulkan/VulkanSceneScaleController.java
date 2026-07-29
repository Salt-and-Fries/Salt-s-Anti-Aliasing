package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.NisUpscaleQualityPreset;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Owns GPU-backed scene rendering at non-native resolution for SSAA and spatial upscalers, then
 * resolves the scene back to the main target. This path uses Minecraft's render target and post
 * chain abstractions, so it can run on either 26.2 backend.
 */
public final class VulkanSceneScaleController {
    private static final VulkanSceneScaleController INSTANCE = new VulkanSceneScaleController();
    private static final String TARGET_LABEL = "Salt's Scaled Scene";
    private static final Identifier SCENE_TARGET_ID = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":scene_color");
    private static final Identifier NIS_UPSCALE_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":nis_upscale");
    private static final Identifier FSR1_QUALITY_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_upscale_quality");
    private static final Identifier FSR1_BALANCED_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_upscale_balanced");
    private static final Identifier FSR1_PERFORMANCE_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_upscale_performance");
    private static final Identifier FSR1_ULTRA_PERFORMANCE_EFFECT =
            Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_upscale_ultra_performance");
    private static final Identifier FSR1_RCAS_QUALITY_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_rcas_quality");
    private static final Identifier FSR1_RCAS_BALANCED_EFFECT = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_rcas_balanced");
    private static final Identifier FSR1_RCAS_PERFORMANCE_EFFECT =
            Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_rcas_performance");
    private static final Identifier FSR1_RCAS_ULTRA_PERFORMANCE_EFFECT =
            Identifier.parse(SaltsAntiAliasing.MOD_ID + ":fsr1_rcas_ultra_performance");
    private static final Set<Identifier> EXTERNAL_SCALE_TARGETS = Set.of(PostChain.MAIN_TARGET_ID, SCENE_TARGET_ID);

    private final CrossFrameResourcePool resourcePool = new CrossFrameResourcePool(3);
    private boolean disabledAfterFailure;
    private boolean active;
    private TextureTarget sceneTarget;
    private RenderTarget mainTarget;
    private AntiAliasingMode activeMode = AntiAliasingMode.OFF;

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

        if (disabledAfterFailure || !usesScaledSceneTarget(config.mode)) {
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
            int sceneWidth = Math.max(1, Math.round(mainTarget.width * config.sceneRenderScale()));
            int sceneHeight = Math.max(1, Math.round(mainTarget.height * config.sceneRenderScale()));
            ensureSceneTarget(sceneWidth, sceneHeight, mainTarget.useDepth);

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
        if (sceneTarget == null || sceneTarget.useDepth != useDepth) {
            destroyResources();
            sceneTarget = new TextureTarget(TARGET_LABEL, width, height, useDepth, GpuFormat.RGBA8_UNORM);
            return;
        }

        if (sceneTarget.width != width || sceneTarget.height != height) {
            sceneTarget.resize(width, height);
        }
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
        Identifier effectId = upscaleEffectFor(config);
        if (effectId == null) {
            resolveSceneColor(sceneTarget, mainTarget);
            return;
        }

        PostChain postChain = minecraft.getShaderManager().getPostChain(effectId, EXTERNAL_SCALE_TARGETS);
        if (postChain == null) {
            resolveSceneColor(sceneTarget, mainTarget);
            return;
        }

        VulkanDynamicUniforms.updateForMode(postChain, config);

        FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
        ResourceHandle<RenderTarget> mainHandle = frameGraphBuilder.importExternal("salts_upscale_main", mainTarget);
        ResourceHandle<RenderTarget> sceneHandle = frameGraphBuilder.importExternal("salts_upscale_scene", sceneTarget);
        SceneScaleTargetBundle targetBundle = new SceneScaleTargetBundle(mainHandle, sceneHandle);
        postChain.addToFrame(frameGraphBuilder, mainTarget.width, mainTarget.height, targetBundle);
        frameGraphBuilder.execute(resourcePool);
    }

    /**
     * Coordinates upscale effect for within the anti-aliasing render, configuration, or compatibility flow.
     * @param config configuration object being normalized, copied, or committed
     * @return upscale effect for produced by this helper
     */
    private static Identifier upscaleEffectFor(AntiAliasingConfig config) {
        return switch (config.mode) {
            case NIS_UPSCALE -> NIS_UPSCALE_EFFECT;
            case FSR1_UPSCALE -> fsr1EffectFor(config.nisUpscaleQualityPreset);
            case FSR1_RCAS -> fsr1RcasEffectFor(config.nisUpscaleQualityPreset);
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
     * Coordinates fsr1 rcas effect for within the anti-aliasing render, configuration, or compatibility flow.
     * @param preset quality preset selected by the user or loaded from config
     * @return fsr1 rcas effect for produced by this helper
     */
    private static Identifier fsr1RcasEffectFor(NisUpscaleQualityPreset preset) {
        return switch (NisUpscaleQualityPreset.clamp(preset)) {
            case QUALITY -> FSR1_RCAS_QUALITY_EFFECT;
            case BALANCED -> FSR1_RCAS_BALANCED_EFFECT;
            case PERFORMANCE -> FSR1_RCAS_PERFORMANCE_EFFECT;
            case ULTRA_PERFORMANCE -> FSR1_RCAS_ULTRA_PERFORMANCE_EFFECT;
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
            case FSR1_RCAS -> "Salt's FSR1 + RCAS Resolve";
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
        disabledAfterFailure = true;
        destroyResources();
        resourcePool.clear();
        clearFrameState();
        SaltsAntiAliasing.LOGGER.error(message, exception);
    }

    /**
     * Coordinates destroy resources within the anti-aliasing render, configuration, or compatibility flow.
     */
    private void destroyResources() {
        if (sceneTarget != null) {
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
                || mode == AntiAliasingMode.FSR1_UPSCALE
                || mode == AntiAliasingMode.FSR1_RCAS;
    }

    /**
     * Checks uses dedicated upscale shader without mutating runtime or configuration state.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @return whether this object requires the described render path
     */
    private static boolean usesDedicatedUpscaleShader(AntiAliasingMode mode) {
        return mode == AntiAliasingMode.NIS_UPSCALE
                || mode == AntiAliasingMode.FSR1_UPSCALE
                || mode == AntiAliasingMode.FSR1_RCAS;
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
