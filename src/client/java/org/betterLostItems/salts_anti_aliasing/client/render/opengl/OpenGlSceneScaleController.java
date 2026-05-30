package org.betterLostItems.salts_anti_aliasing.client.render.opengl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.lwjgl.opengl.GL30C;

/**
 * Manages scene rendering at scaled internal resolutions for Minecraft 1.21.1.
 */
public final class OpenGlSceneScaleController {
    private static final OpenGlSceneScaleController INSTANCE = new OpenGlSceneScaleController();
    private static final ResourceLocation NIS_SHARPEN_EFFECT = id("nis_sharpen");
    private static final ResourceLocation FSR1_UPSCALE_EFFECT = id("fsr1_upscale_quality");
    private static final ResourceLocation RCAS_SHARPEN_EFFECT = id("rcas_sharpen");

    private boolean disabledAfterFailure;
    private boolean active;
    private TextureTarget sceneTarget;
    private RenderTarget mainTarget;
    private AntiAliasingMode activeMode = AntiAliasingMode.OFF;

    private OpenGlSceneScaleController() {
    }

    public static OpenGlSceneScaleController instance() {
        return INSTANCE;
    }

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
            bindSceneTarget();
        } catch (RuntimeException exception) {
            disableAfterFailure("Disabling OpenGL scene scaling after a setup failure", exception);
        }
    }

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
            if (mainTarget != null && sceneTarget != null) {
                OpenGlFramebufferBlitter.blitColor(sceneTarget, mainTarget, GL30C.GL_LINEAR);
                if (mainTarget.useDepth && sceneTarget.useDepth) {
                    OpenGlFramebufferBlitter.blitDepth(sceneTarget, mainTarget);
                }
                processUpscaleFinishingPass(gameRenderer.getMinecraft(), config);
                mainTarget.bindWrite(true);
            }
        } catch (RuntimeException exception) {
            disableAfterFailure("Disabling OpenGL scene scaling after a resolve failure", exception);
        } finally {
            clearFrameState();
        }
    }

    public RenderTarget overrideMainTarget() {
        return active ? sceneTarget : null;
    }

    public boolean forceViewportOnBind(RenderTarget target, boolean setViewport) {
        return active && !setViewport && target != null;
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

        if (!resolvedTarget.useDepth || !resolvedSource.useDepth) {
            return true;
        }

        resolvedTarget.copyDepthFrom(resolvedSource);
        return true;
    }

    private void ensureSceneTarget(int width, int height, boolean useDepth) {
        if (sceneTarget == null || sceneTarget.useDepth != useDepth) {
            destroyResources();
            sceneTarget = new TextureTarget(width, height, useDepth, Minecraft.ON_OSX);
            return;
        }

        if (sceneTarget.width != width || sceneTarget.height != height) {
            sceneTarget.resize(width, height, Minecraft.ON_OSX);
        }
    }

    private void bindSceneTarget() {
        sceneTarget.bindWrite(true);
    }

    private void processUpscaleFinishingPass(Minecraft minecraft, AntiAliasingConfig config) {
        ResourceLocation effectId = finishingEffectFor(config.mode);
        if (effectId == null) {
            return;
        }

        try {
            PostChain postChain = OpenGlPostChainManager.get(minecraft, effectId);
            OpenGlDynamicUniforms.updateForMode(postChain, config);
            postChain.process(minecraft.getTimer().getGameTimeDeltaTicks());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to run scaled-scene finishing pass " + effectId, exception);
        }
    }

    private RenderTarget mappedTarget(RenderTarget target) {
        if (!active || target != mainTarget) {
            return null;
        }

        return sceneTarget;
    }

    private void disableAfterFailure(String message, RuntimeException exception) {
        disabledAfterFailure = true;
        destroyResources();
        clearFrameState();
        SaltsAntiAliasing.LOGGER.error(message, exception);
    }

    private void destroyResources() {
        if (sceneTarget != null) {
            sceneTarget.destroyBuffers();
            sceneTarget = null;
        }
    }

    private static boolean usesScaledSceneTarget(AntiAliasingMode mode) {
        return mode == AntiAliasingMode.SSAA
                || mode == AntiAliasingMode.NIS_UPSCALE
                || mode == AntiAliasingMode.FSR1_UPSCALE
                || mode == AntiAliasingMode.FSR1_RCAS;
    }

    private static ResourceLocation finishingEffectFor(AntiAliasingMode mode) {
        return switch (mode) {
            case NIS_UPSCALE -> NIS_SHARPEN_EFFECT;
            case FSR1_UPSCALE -> FSR1_UPSCALE_EFFECT;
            case FSR1_RCAS -> RCAS_SHARPEN_EFFECT;
            default -> null;
        };
    }

    private void clearFrameState() {
        active = false;
        mainTarget = null;
        activeMode = AntiAliasingMode.OFF;
    }

    private static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(SaltsAntiAliasing.MOD_ID, path);
    }
}
