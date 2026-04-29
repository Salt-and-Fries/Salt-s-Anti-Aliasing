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

    private OpenGlSceneMsaaController() {
    }

    public static OpenGlSceneMsaaController instance() {
        return INSTANCE;
    }

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

    public void syncColorIfNeeded(RenderTarget target) {
        if (target == mainTarget) {
            syncMainTargetIfNeeded(true, false);
        }
    }

    public void syncDepthIfNeeded(RenderTarget target) {
        if (target == mainTarget) {
            syncMainTargetIfNeeded(false, true);
        }
    }

    public void syncSourceDepthBeforeCopy(RenderTarget sourceTarget) {
        if (sourceTarget == mainTarget) {
            syncMainTargetIfNeeded(false, true);
        }
    }

    public void mirrorClearColorIfNeeded(GpuTexture colorTexture, int clearColor) {
        if (active && colorTexture == mainColorTexture) {
            clearMsaa(clearColor, true, 1.0d, false);
            mainColorDirty = false;
        }
    }

    public void mirrorClearDepthIfNeeded(GpuTexture depthTexture, double clearDepth) {
        if (active && depthTexture == mainDepthTexture) {
            clearMsaa(0, false, clearDepth, true);
            mainDepthDirty = false;
        }
    }

    public void mirrorClearColorAndDepthIfNeeded(GpuTexture colorTexture, int clearColor, GpuTexture depthTexture, double clearDepth) {
        if (active && colorTexture == mainColorTexture && depthTexture == mainDepthTexture) {
            clearMsaa(clearColor, true, clearDepth, true);
            mainColorDirty = false;
            mainDepthDirty = false;
        }
    }

    public void onRenderPassFinished() {
        if (mainPassInProgress) {
            mainPassInProgress = false;
        }
    }

    private int resolveRequestedSamples(MsaaSampleLevel requestedLevel) {
        MsaaSampleLevel supportedLevel = MsaaSampleLevel.bestSupported(requestedLevel, queryMaxSupportedSamples());
        return supportedLevel == null ? 0 : supportedLevel.samples();
    }

    private int queryMaxSupportedSamples() {
        if (cachedMaxSupportedSamples < 0) {
            cachedMaxSupportedSamples = Math.max(1, GL11C.glGetInteger(GL30C.GL_MAX_SAMPLES));
        }

        return cachedMaxSupportedSamples;
    }

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

    private void clearMsaa(int clearColor, boolean clearColorBuffer, double clearDepth, boolean clearDepthBuffer) {
        if (msaaFramebufferId == 0) {
            return;
        }

        int previousFramebuffer = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, msaaFramebufferId);
        GlStateManager._disableScissorTest();
        GlStateManager._depthMask(true);
        GlStateManager._colorMask(15);

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

    private void disableAfterFailure(String message, RuntimeException exception) {
        disabledAfterFailure = true;
        destroyResources();
        resetFrameState();
        SaltsAntiAliasing.LOGGER.error(message, exception);
    }

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
