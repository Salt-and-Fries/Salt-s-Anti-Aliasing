package org.betterLostItems.salts_anti_aliasing.client.render.opengl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.opengl.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.opengl.GlTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.ARGB;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.MsaaSampleLevel;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL30C;

/**
 * Manages OpenGL multisample scene targets and resolves MSAA output back into Minecraft's expected
 * target.
 */
public final class OpenGlSceneMsaaController {
    private static final OpenGlSceneMsaaController INSTANCE = new OpenGlSceneMsaaController();

    private boolean disabledAfterFailure;
    private boolean active;
    private int msaaFramebufferId;
    private int msaaColorRenderbufferId;
    private int msaaDepthRenderbufferId;
    private int allocatedWidth = -1;
    private int allocatedHeight = -1;
    private int allocatedSamples = -1;
    private int cachedMaxSupportedSamples = -1;
    private int lastLoggedRequestedSamples = -1;
    private int lastLoggedResolvedSamples = -1;
    private RenderTarget mainTarget;
    private GpuTexture mainColorTexture;
    private GpuTextureView mainColorView;
    private GpuTexture mainDepthTexture;
    private int resolvedMainFramebufferId = -1;
    private boolean mainColorDirty;
    private boolean mainDepthDirty;
    private boolean mainPassInProgress;

    /**
     * Creates a open gl scene msaa controller with the collaborators or initial state supplied by the
     * caller.
     */
    private OpenGlSceneMsaaController() {
    }

    /**
     * Coordinates instance within the anti-aliasing render, configuration, or compatibility flow.
     * @return instance value produced or selected by this code path
     */
    public static OpenGlSceneMsaaController instance() {
        return INSTANCE;
    }

    /**
     * Begins the scene rendering phase and prepares render state that later callbacks will resolve.
     * @param gameRenderer Minecraft renderer currently being intercepted or processed
     * @param config configuration being read, normalized, or applied
     */
    public void beginSceneRendering(GameRenderer gameRenderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        resetFrameState();

        if (disabledAfterFailure || config.mode != AntiAliasingMode.MSAA) {
            return;
        }

        Minecraft minecraft = gameRenderer.getMinecraft();
        if (minecraft.level == null || Minecraft.useShaderTransparency()) {
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (!mainTarget.useDepth || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        GpuTextureView colorView = mainTarget.getColorTextureView();
        GpuTexture depthTexture = mainTarget.getDepthTexture();
        if (colorView == null || depthTexture == null) {
            return;
        }

        try {
            int requestedSamples = MsaaSampleLevel.clamp(config.msaaSampleLevel).samples();
            int resolvedSamples = resolveRequestedSamples(config.msaaSampleLevel);
            if (resolvedSamples < 2) {
                return;
            }

            ensureResources(mainTarget.width, mainTarget.height, resolvedSamples);
            logFallbackIfNeeded(requestedSamples, resolvedSamples);

            this.mainTarget = mainTarget;
            mainColorTexture = mainTarget.getColorTexture();
            mainColorView = colorView;
            mainDepthTexture = depthTexture;
            active = true;
        } catch (RuntimeException exception) {
            disableAfterFailure("Disabling OpenGL MSAA scene rendering after a setup failure", exception);
        }
    }

    /**
     * Coordinates override framebuffer within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param colorView color view supplied by Minecraft or the caller
     * @param depthTexture depth texture supplied by Minecraft or the caller
     * @param originalFramebufferId original framebuffer id supplied by Minecraft or the caller
     * @return override framebuffer value produced or selected by this code path
     */
    public Integer overrideFramebuffer(GlTextureView colorView, GpuTexture depthTexture, int originalFramebufferId) {
        if (!active || colorView != mainColorView || depthTexture != mainDepthTexture) {
            return null;
        }

        resolvedMainFramebufferId = originalFramebufferId;
        mainColorDirty = true;
        mainDepthDirty = true;
        mainPassInProgress = true;
        return msaaFramebufferId;
    }

    /**
     * Coordinates override framebuffer within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param colorTexture color texture supplied by Minecraft or the caller
     * @param depthTexture depth texture supplied by Minecraft or the caller
     * @param originalFramebufferId original framebuffer id supplied by Minecraft or the caller
     * @return override framebuffer value produced or selected by this code path
     */
    public Integer overrideFramebuffer(GpuTexture colorTexture, GpuTexture depthTexture, int originalFramebufferId) {
        if (!active || colorTexture != mainColorTexture || depthTexture != mainDepthTexture) {
            return null;
        }

        resolvedMainFramebufferId = originalFramebufferId;
        mainColorDirty = true;
        mainDepthDirty = true;
        mainPassInProgress = true;
        return msaaFramebufferId;
    }

    /**
     * Ends the scene rendering phase and restores renderer state expected by vanilla Minecraft.
     * @param gameRenderer Minecraft renderer currently being intercepted or processed
     * @param config configuration being read, normalized, or applied
     */
    public void endSceneRendering(GameRenderer gameRenderer, AntiAliasingConfig config) {
        RenderSystem.assertOnRenderThread();
        if (!active) {
            resetFrameState();
            return;
        }

        try {
            syncMainTargetIfNeeded(true, false);
        } catch (RuntimeException exception) {
            disableAfterFailure("Disabling OpenGL MSAA scene rendering after a resolve failure", exception);
        } finally {
            resetFrameState();
        }
    }

    /**
     * Coordinates sync color if needed within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param target target supplied by Minecraft or the caller
     */
    public void syncColorIfNeeded(RenderTarget target) {
        if (target == mainTarget) {
            syncMainTargetIfNeeded(true, false);
        }
    }

    /**
     * Coordinates sync depth if needed within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param target target supplied by Minecraft or the caller
     */
    public void syncDepthIfNeeded(RenderTarget target) {
        if (target == mainTarget) {
            syncMainTargetIfNeeded(false, true);
        }
    }

    /**
     * Coordinates sync source depth before copy within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param sourceTarget source target supplied by Minecraft or the caller
     */
    public void syncSourceDepthBeforeCopy(RenderTarget sourceTarget) {
        if (sourceTarget == mainTarget) {
            syncMainTargetIfNeeded(false, true);
        }
    }

    /**
     * Coordinates mirror clear color if needed within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param colorTexture color texture supplied by Minecraft or the caller
     * @param clearColor clear color supplied by Minecraft or the caller
     */
    public void mirrorClearColorIfNeeded(GpuTexture colorTexture, int clearColor) {
        if (active && colorTexture == mainColorTexture) {
            clearMsaa(clearColor, true, 1.0d, false);
            mainColorDirty = false;
        }
    }

    /**
     * Coordinates mirror clear depth if needed within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param depthTexture depth texture supplied by Minecraft or the caller
     * @param clearDepth clear depth supplied by Minecraft or the caller
     */
    public void mirrorClearDepthIfNeeded(GpuTexture depthTexture, double clearDepth) {
        if (active && depthTexture == mainDepthTexture) {
            clearMsaa(0, false, clearDepth, true);
            mainDepthDirty = false;
        }
    }

    /**
     * Coordinates mirror clear color and depth if needed within the anti-aliasing render,
     * configuration, or compatibility flow.
     * @param colorTexture color texture supplied by Minecraft or the caller
     * @param clearColor clear color supplied by Minecraft or the caller
     * @param depthTexture depth texture supplied by Minecraft or the caller
     * @param clearDepth clear depth supplied by Minecraft or the caller
     */
    public void mirrorClearColorAndDepthIfNeeded(GpuTexture colorTexture, int clearColor, GpuTexture depthTexture, double clearDepth) {
        if (active && colorTexture == mainColorTexture && depthTexture == mainDepthTexture) {
            clearMsaa(clearColor, true, clearDepth, true);
            mainColorDirty = false;
            mainDepthDirty = false;
        }
    }

    /**
     * Coordinates on render pass finished within the anti-aliasing render, configuration, or
     * compatibility flow.
     */
    public void onRenderPassFinished() {
        if (mainPassInProgress) {
            mainPassInProgress = false;
        }
    }

    /**
     * Coordinates resolve requested samples within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param requestedLevel requested level supplied by Minecraft or the caller
     * @return resolve requested samples value produced or selected by this code path
     */
    private int resolveRequestedSamples(MsaaSampleLevel requestedLevel) {
        MsaaSampleLevel supportedLevel = MsaaSampleLevel.bestSupported(requestedLevel, queryMaxSupportedSamples());
        return supportedLevel == null ? 0 : supportedLevel.samples();
    }

    /**
     * Coordinates query max supported samples within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @return query max supported samples value produced or selected by this code path
     */
    private int queryMaxSupportedSamples() {
        if (cachedMaxSupportedSamples < 0) {
            cachedMaxSupportedSamples = Math.max(1, GL11C.glGetInteger(GL30C.GL_MAX_SAMPLES));
        }

        return cachedMaxSupportedSamples;
    }

    /**
     * Coordinates ensure resources within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param width width supplied by Minecraft or the caller
     * @param height height supplied by Minecraft or the caller
     * @param samples samples supplied by Minecraft or the caller
     */
    private void ensureResources(int width, int height, int samples) {
        if (msaaFramebufferId != 0 && allocatedWidth == width && allocatedHeight == height && allocatedSamples == samples) {
            return;
        }

        destroyResources();

        int previousFramebuffer = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        int previousRenderbuffer = GL11C.glGetInteger(GL30C.GL_RENDERBUFFER_BINDING);

        msaaFramebufferId = GL30C.glGenFramebuffers();
        msaaColorRenderbufferId = GL30C.glGenRenderbuffers();
        msaaDepthRenderbufferId = GL30C.glGenRenderbuffers();

        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, msaaFramebufferId);

        GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, msaaColorRenderbufferId);
        GL30C.glRenderbufferStorageMultisample(GL30C.GL_RENDERBUFFER, samples, GL30C.GL_RGBA8, width, height);
        GL30C.glFramebufferRenderbuffer(
                GL30C.GL_FRAMEBUFFER,
                GL30C.GL_COLOR_ATTACHMENT0,
                GL30C.GL_RENDERBUFFER,
                msaaColorRenderbufferId
        );

        GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, msaaDepthRenderbufferId);
        GL30C.glRenderbufferStorageMultisample(
                GL30C.GL_RENDERBUFFER,
                samples,
                GL30C.GL_DEPTH_COMPONENT32F,
                width,
                height
        );
        GL30C.glFramebufferRenderbuffer(
                GL30C.GL_FRAMEBUFFER,
                GL30C.GL_DEPTH_ATTACHMENT,
                GL30C.GL_RENDERBUFFER,
                msaaDepthRenderbufferId
        );

        int status = GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER);
        if (status != GL30C.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("MSAA framebuffer is incomplete: 0x" + Integer.toHexString(status));
        }

        GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, previousRenderbuffer);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, previousFramebuffer);

        allocatedWidth = width;
        allocatedHeight = height;
        allocatedSamples = samples;
    }

    /**
     * Coordinates sync main target if needed within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param includeColor include color supplied by Minecraft or the caller
     * @param includeDepth include depth supplied by Minecraft or the caller
     */
    private void syncMainTargetIfNeeded(boolean includeColor, boolean includeDepth) {
        if (!active || mainTarget == null || msaaFramebufferId == 0 || resolvedMainFramebufferId == -1) {
            return;
        }

        boolean shouldResolveColor = includeColor && mainColorDirty;
        boolean shouldResolveDepth = includeDepth && mainDepthDirty;
        if (!shouldResolveColor && !shouldResolveDepth) {
            return;
        }

        int previousReadFramebuffer = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int previousDrawFramebuffer = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);

        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, msaaFramebufferId);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, resolvedMainFramebufferId);

        if (shouldResolveColor) {
            GL30C.glBlitFramebuffer(
                    0,
                    0,
                    mainTarget.width,
                    mainTarget.height,
                    0,
                    0,
                    mainTarget.width,
                    mainTarget.height,
                    GL30C.GL_COLOR_BUFFER_BIT,
                    GL30C.GL_NEAREST
            );
            mainColorDirty = false;
        }

        if (shouldResolveDepth) {
            GL30C.glBlitFramebuffer(
                    0,
                    0,
                    mainTarget.width,
                    mainTarget.height,
                    0,
                    0,
                    mainTarget.width,
                    mainTarget.height,
                    GL30C.GL_DEPTH_BUFFER_BIT,
                    GL30C.GL_NEAREST
            );
            mainDepthDirty = false;
        }

        GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, previousReadFramebuffer);
        GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, previousDrawFramebuffer);
    }

    /**
     * Coordinates clear msaa within the anti-aliasing render, configuration, or compatibility flow.
     * @param clearColor clear color supplied by Minecraft or the caller
     * @param clearColorBuffer clear color buffer supplied by Minecraft or the caller
     * @param clearDepth clear depth supplied by Minecraft or the caller
     * @param clearDepthBuffer clear depth buffer supplied by Minecraft or the caller
     */
    private void clearMsaa(int clearColor, boolean clearColorBuffer, double clearDepth, boolean clearDepthBuffer) {
        if (msaaFramebufferId == 0) {
            return;
        }

        int previousFramebuffer = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, msaaFramebufferId);
        GlStateManager._disableScissorTest();
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(true, true, true, true);

        int clearMask = 0;
        if (clearColorBuffer) {
            GL11C.glClearColor(
                    ARGB.redFloat(clearColor),
                    ARGB.greenFloat(clearColor),
                    ARGB.blueFloat(clearColor),
                    ARGB.alphaFloat(clearColor)
            );
            clearMask |= GL30C.GL_COLOR_BUFFER_BIT;
        }

        if (clearDepthBuffer) {
            GL11C.glClearDepth(clearDepth);
            clearMask |= GL30C.GL_DEPTH_BUFFER_BIT;
        }

        if (clearMask != 0) {
            GL11C.glClear(clearMask);
        }

        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, previousFramebuffer);
    }

    /**
     * Coordinates destroy resources within the anti-aliasing render, configuration, or compatibility
     * flow.
     */
    private void destroyResources() {
        if (msaaColorRenderbufferId != 0) {
            GL30C.glDeleteRenderbuffers(msaaColorRenderbufferId);
            msaaColorRenderbufferId = 0;
        }

        if (msaaDepthRenderbufferId != 0) {
            GL30C.glDeleteRenderbuffers(msaaDepthRenderbufferId);
            msaaDepthRenderbufferId = 0;
        }

        if (msaaFramebufferId != 0) {
            GL30C.glDeleteFramebuffers(msaaFramebufferId);
            msaaFramebufferId = 0;
        }

        allocatedWidth = -1;
        allocatedHeight = -1;
        allocatedSamples = -1;
    }

    /**
     * Coordinates log fallback if needed within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param requestedSamples requested samples supplied by Minecraft or the caller
     * @param resolvedSamples resolved samples supplied by Minecraft or the caller
     */
    private void logFallbackIfNeeded(int requestedSamples, int resolvedSamples) {
        if (requestedSamples == resolvedSamples) {
            return;
        }

        if (requestedSamples == lastLoggedRequestedSamples && resolvedSamples == lastLoggedResolvedSamples) {
            return;
        }

        lastLoggedRequestedSamples = requestedSamples;
        lastLoggedResolvedSamples = resolvedSamples;
        SaltsAntiAliasing.LOGGER.info(
                "OpenGL MSAA requested {}x but using {}x based on current GPU limits",
                requestedSamples,
                resolvedSamples
        );
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
        resetFrameState();
        SaltsAntiAliasing.LOGGER.error(message, exception);
    }

    /**
     * Coordinates reset frame state within the anti-aliasing render, configuration, or compatibility
     * flow.
     */
    private void resetFrameState() {
        active = false;
        mainTarget = null;
        mainColorTexture = null;
        mainColorView = null;
        mainDepthTexture = null;
        resolvedMainFramebufferId = -1;
        mainColorDirty = false;
        mainDepthDirty = false;
        mainPassInProgress = false;
    }
}
