package org.betterLostItems.salts_anti_aliasing.client.render.opengl;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.NisUpscaleQualityPreset;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Manages OpenGL scene rendering at scaled internal resolutions for SSAA and spatial upscaling modes.
 */
public final class OpenGlSceneScaleController {
    private static final OpenGlSceneScaleController INSTANCE = new OpenGlSceneScaleController();
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
     * Creates a open gl scene scale controller with the collaborators or initial state supplied by the
     * caller.
     */
    private OpenGlSceneScaleController() {
    }

    /**
     * Coordinates instance within the anti-aliasing render, configuration, or compatibility flow.
     * @return instance value produced or selected by this code path
     */
    public static OpenGlSceneScaleController instance() {
        return INSTANCE;
    }

    /**
     * Begins the scene rendering phase and prepares render state that later callbacks will resolve.
     * @param gameRenderer Minecraft renderer currently being intercepted or processed
     * @param config configuration being read, normalized, or applied
     */
    public void beginSceneRendering(GameRenderer gameRenderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        clearFrameState();

        if (disabledAfterFailure || !usesScaledSceneTarget(config.mode)) {
            return;
        }

        Minecraft minecraft = gameRenderer.getMinecraft();
        if (minecraft.level == null) {
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
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
            disableAfterFailure("Disabling OpenGL scene scaling after a setup failure", exception);
        }
    }

    /**
     * Ends the scene rendering phase and restores renderer state expected by vanilla Minecraft.
     * @param gameRenderer Minecraft renderer currently being intercepted or processed
     * @param config configuration being read, normalized, or applied
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
                    processDedicatedUpscale(gameRenderer.getMinecraft(), sceneTarget, mainTarget, config);
                } else {
                    resolveSceneColor(sceneTarget, mainTarget);
                }
            }
        } catch (RuntimeException exception) {
            disableAfterFailure("Disabling OpenGL scene scaling after a resolve failure", exception);
        } finally {
            resourcePool.endFrame();
            clearFrameState();
        }
    }

    /**
     * Coordinates override color texture within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param target target supplied by Minecraft or the caller
     * @return override color texture value produced or selected by this code path
     */
    public GpuTexture overrideColorTexture(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getColorTexture();
    }

    /**
     * Coordinates override main target within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @return override main target value produced or selected by this code path
     */
    public RenderTarget overrideMainTarget() {
        return active ? sceneTarget : null;
    }

    /**
     * Coordinates override color texture view within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param target target supplied by Minecraft or the caller
     * @return override color texture view value produced or selected by this code path
     */
    public GpuTextureView overrideColorTextureView(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getColorTextureView();
    }

    /**
     * Coordinates override depth texture within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param target target supplied by Minecraft or the caller
     * @return override depth texture value produced or selected by this code path
     */
    public GpuTexture overrideDepthTexture(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getDepthTexture();
    }

    /**
     * Coordinates override depth texture view within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param target target supplied by Minecraft or the caller
     * @return override depth texture view value produced or selected by this code path
     */
    public GpuTextureView overrideDepthTextureView(RenderTarget target) {
        RenderTarget redirectedTarget = mappedTarget(target);
        return redirectedTarget == null ? null : redirectedTarget.getDepthTextureView();
    }

    /**
     * Coordinates redirect copy depth within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param target target supplied by Minecraft or the caller
     * @param sourceTarget source target supplied by Minecraft or the caller
     * @return redirect copy depth value produced or selected by this code path
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
     * Coordinates ensure scene target within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param width width supplied by Minecraft or the caller
     * @param height height supplied by Minecraft or the caller
     * @param useDepth use depth supplied by Minecraft or the caller
     */
    private void ensureSceneTarget(int width, int height, boolean useDepth) {
        if (sceneTarget == null || sceneTarget.useDepth != useDepth) {
            destroyResources();
            sceneTarget = new TextureTarget(TARGET_LABEL, width, height, useDepth);
            return;
        }

        if (sceneTarget.width != width || sceneTarget.height != height) {
            sceneTarget.resize(width, height);
        }
    }

    /**
     * Coordinates resolve scene color within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param sceneTarget scene target supplied by Minecraft or the caller
     * @param mainTarget main target supplied by Minecraft or the caller
     */
    private void resolveSceneColor(TextureTarget sceneTarget, RenderTarget mainTarget) {
        try (var renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                this::resolvePassLabel,
                mainTarget.getColorTextureView(),
                OptionalInt.empty()
        )) {
            renderPass.setPipeline(RenderPipelines.TRACY_BLIT);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture(
                    "InSampler",
                    sceneTarget.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR)
            );
            renderPass.draw(0, 3);
        }
    }

    /**
     * Coordinates process dedicated upscale within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param minecraft minecraft supplied by Minecraft or the caller
     * @param sceneTarget scene target supplied by Minecraft or the caller
     * @param mainTarget main target supplied by Minecraft or the caller
     * @param config configuration being read, normalized, or applied
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

        OpenGlDynamicUniforms.updateForMode(postChain, config);

        FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
        ResourceHandle<RenderTarget> mainHandle = frameGraphBuilder.importExternal("salts_upscale_main", mainTarget);
        ResourceHandle<RenderTarget> sceneHandle = frameGraphBuilder.importExternal("salts_upscale_scene", sceneTarget);
        SceneScaleTargetBundle targetBundle = new SceneScaleTargetBundle(mainHandle, sceneHandle);
        postChain.addToFrame(frameGraphBuilder, mainTarget.width, mainTarget.height, targetBundle);
        frameGraphBuilder.execute(resourcePool);
    }

    /**
     * Coordinates upscale effect for within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param config configuration being read, normalized, or applied
     * @return upscale effect for value produced or selected by this code path
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
     * Coordinates fsr1 effect for within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param preset quality preset selected by config or UI
     * @return fsr1 effect for value produced or selected by this code path
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
     * Coordinates fsr1 rcas effect for within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param preset quality preset selected by config or UI
     * @return fsr1 rcas effect for value produced or selected by this code path
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
     * Coordinates resolve pass label within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return resolve pass label value produced or selected by this code path
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
     * Coordinates mapped target within the anti-aliasing render, configuration, or compatibility flow.
     * @param target target supplied by Minecraft or the caller
     * @return mapped target value produced or selected by this code path
     */
    private RenderTarget mappedTarget(RenderTarget target) {
        if (!active || target != mainTarget) {
            return null;
        }

        return sceneTarget;
    }

    /**
     * Coordinates disable after failure within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param message message supplied by Minecraft or the caller
     * @param exception exception supplied by Minecraft or the caller
     */
    private void disableAfterFailure(String message, RuntimeException exception) {
        disabledAfterFailure = true;
        destroyResources();
        resourcePool.clear();
        clearFrameState();
        SaltsAntiAliasing.LOGGER.error(message, exception);
    }

    /**
     * Coordinates destroy resources within the anti-aliasing render, configuration, or compatibility
     * flow.
     */
    private void destroyResources() {
        if (sceneTarget != null) {
            sceneTarget.destroyBuffers();
            sceneTarget = null;
        }
    }

    /**
     * Checks whether uses scaled scene target without mutating configuration or render state.
     * @param mode requested anti-aliasing mode
     * @return uses scaled scene target value produced or selected by this code path
     */
    private static boolean usesScaledSceneTarget(AntiAliasingMode mode) {
        return mode == AntiAliasingMode.SSAA
                || mode == AntiAliasingMode.NIS_UPSCALE
                || mode == AntiAliasingMode.FSR1_UPSCALE
                || mode == AntiAliasingMode.FSR1_RCAS;
    }

    /**
     * Checks whether uses dedicated upscale shader without mutating configuration or render state.
     * @param mode requested anti-aliasing mode
     * @return uses dedicated upscale shader value produced or selected by this code path
     */
    private static boolean usesDedicatedUpscaleShader(AntiAliasingMode mode) {
        return mode == AntiAliasingMode.NIS_UPSCALE
                || mode == AntiAliasingMode.FSR1_UPSCALE
                || mode == AntiAliasingMode.FSR1_RCAS;
    }

    /**
     * Coordinates can copy depth within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param target target supplied by Minecraft or the caller
     * @param source source supplied by Minecraft or the caller
     * @return can copy depth value produced or selected by this code path
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
     * Coordinates clear frame state within the anti-aliasing render, configuration, or compatibility
     * flow.
     */
    private void clearFrameState() {
        active = false;
        mainTarget = null;
        activeMode = AntiAliasingMode.OFF;
    }

    /**
     * Documents scene scale target bundle behavior for Salt's Anti Aliasing. OpenGL backend code that
     * owns framebuffers, post chains, and GPU-side state.
     */
    private static final class SceneScaleTargetBundle implements PostChain.TargetBundle {
        private final Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();

        /**
         * Coordinates scene scale target bundle within the anti-aliasing render, configuration, or
         * compatibility flow.
         * @param mainHandle main handle supplied by Minecraft or the caller
         * @param sceneHandle scene handle supplied by Minecraft or the caller
         */
        private SceneScaleTargetBundle(ResourceHandle<RenderTarget> mainHandle, ResourceHandle<RenderTarget> sceneHandle) {
            targets.put(PostChain.MAIN_TARGET_ID, mainHandle);
            targets.put(SCENE_TARGET_ID, sceneHandle);
        }

        /**
         * Coordinates replace within the anti-aliasing render, configuration, or compatibility flow.
         * @param id id supplied by Minecraft or the caller
         * @param handle handle supplied by Minecraft or the caller
         */
        @Override
        public void replace(Identifier id, ResourceHandle<RenderTarget> handle) {
            targets.put(id, handle);
        }

        /**
         * Coordinates get within the anti-aliasing render, configuration, or compatibility flow.
         * @param id id supplied by Minecraft or the caller
         * @return get value produced or selected by this code path
         */
        @Override
        public ResourceHandle<RenderTarget> get(Identifier id) {
            return targets.getOrDefault(id, ResourceHandle.invalid());
        }
    }
}
