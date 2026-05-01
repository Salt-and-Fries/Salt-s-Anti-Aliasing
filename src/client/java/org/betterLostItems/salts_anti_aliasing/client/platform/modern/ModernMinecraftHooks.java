package org.betterLostItems.salts_anti_aliasing.client.platform.modern;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlSceneMsaaController;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlSceneScaleController;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlSceneTemporalController;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Central bridge between Minecraft 1.21.8-1.21.11 hook points and the shared mod runtime.
 *
 * <p>Mixins should be as small and boring as possible. Their job is to land on a
 * Minecraft method that exists in this version family, collect the raw values exposed by
 * that method, and immediately delegate here. Keeping that delegation centralized gives
 * future version jars one obvious translation layer to replace while the core config,
 * pipeline planning, metrics, and mode semantics stay stable.</p>
 *
 * <p>This class is intentionally allowed to depend on Minecraft classes and on the modern
 * OpenGL/GPU controllers. Code below this layer should avoid knowing which mixin fired or
 * which Minecraft descriptor was used to reach the hook.</p>
 */
public final class ModernMinecraftHooks {
    private static final long EDGE_DEBUG_TOGGLE_DEBOUNCE_MS = 250L;
    private static long lastEdgeDebugToggleMs;

    private ModernMinecraftHooks() {
    }

    /**
     * Prepares the per-frame temporal jitter before the world projection matrix is built.
     */
    public static void prepareTemporalJitter(GameRenderer gameRenderer) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        boolean taaActive = runtime != null && runtime.activeMode().usesHistoryBuffers();
        RenderTarget mainTarget = gameRenderer.getMinecraft().getMainRenderTarget();
        OpenGlSceneTemporalController.instance().prepareFrameJitter(taaActive, mainTarget.width, mainTarget.height);
    }

    /**
     * Applies the current temporal jitter to whichever projection-matrix argument exists
     * in the active Minecraft minor version.
     */
    public static Matrix4f jitterProjection(Matrix4f projectionMatrix) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        boolean taaActive = runtime != null && runtime.activeMode().usesHistoryBuffers();
        return new Matrix4f(OpenGlSceneTemporalController.instance().jitterProjection(projectionMatrix, taaActive));
    }

    /**
     * Starts scene-only rendering work before Minecraft renders the 3D world.
     */
    public static void beginSceneRendering(GameRenderer gameRenderer) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null) {
            runtime.beginSceneRendering(gameRenderer);
        }
    }

    /**
     * Resolves any temporary scene targets after Minecraft finishes the 3D world.
     */
    public static void endSceneRendering(GameRenderer gameRenderer) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null) {
            runtime.endSceneRendering(gameRenderer);
        }
    }

    /**
     * Applies native-resolution post effects after scene rendering but before HUD/menu drawing.
     */
    public static void applyScenePostProcessing(GameRenderer gameRenderer) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null) {
            runtime.applyScenePostProcessing(gameRenderer);
        }
    }

    /**
     * Records a frame sample once Minecraft has finished a visible world frame.
     */
    public static void recordRenderedFrame(GameRenderer gameRenderer, boolean renderLevel) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null && renderLevel) {
            Minecraft minecraft = gameRenderer.getMinecraft();
            runtime.recordRenderedFrame(minecraft.getFrameTimeNs(), minecraft.getFps());
        }
    }

    /**
     * Lets the scaled-scene controller temporarily replace Minecraft's main render target.
     */
    public static RenderTarget overrideMainRenderTarget() {
        return OpenGlSceneScaleController.instance().overrideMainTarget();
    }

    /**
     * Flushes metrics during the normal Minecraft shutdown hooks.
     */
    public static void shutdownMetrics() {
        if (SaltsAntiAliasingClient.runtimeOrNull() != null) {
            SaltsAntiAliasingClient.runtime().shutdownMetrics();
        }
    }

    /**
     * Redirects color texture reads while SSAA/upscale or MSAA controllers own the scene.
     */
    public static GpuTexture redirectColorTexture(RenderTarget target) {
        GpuTexture redirectedTexture = OpenGlSceneScaleController.instance().overrideColorTexture(target);
        if (redirectedTexture != null) {
            return redirectedTexture;
        }

        OpenGlSceneMsaaController.instance().syncColorIfNeeded(target);
        return null;
    }

    /**
     * Redirects color texture view reads while SSAA/upscale or MSAA controllers own the scene.
     */
    public static GpuTextureView redirectColorTextureView(RenderTarget target) {
        GpuTextureView redirectedTextureView = OpenGlSceneScaleController.instance().overrideColorTextureView(target);
        if (redirectedTextureView != null) {
            return redirectedTextureView;
        }

        OpenGlSceneMsaaController.instance().syncColorIfNeeded(target);
        return null;
    }

    /**
     * Redirects depth texture reads while SSAA/upscale or MSAA controllers own the scene.
     */
    public static GpuTexture redirectDepthTexture(RenderTarget target) {
        GpuTexture redirectedTexture = OpenGlSceneScaleController.instance().overrideDepthTexture(target);
        if (redirectedTexture != null) {
            return redirectedTexture;
        }

        OpenGlSceneMsaaController.instance().syncDepthIfNeeded(target);
        return null;
    }

    /**
     * Redirects depth texture view reads while SSAA/upscale or MSAA controllers own the scene.
     */
    public static GpuTextureView redirectDepthTextureView(RenderTarget target) {
        GpuTextureView redirectedTextureView = OpenGlSceneScaleController.instance().overrideDepthTextureView(target);
        if (redirectedTextureView != null) {
            return redirectedTextureView;
        }

        OpenGlSceneMsaaController.instance().syncDepthIfNeeded(target);
        return null;
    }

    /**
     * Keeps depth-copy behavior coherent when Minecraft copies from or to a redirected scene target.
     */
    public static boolean redirectCopyDepth(RenderTarget target, RenderTarget sourceTarget) {
        if (OpenGlSceneScaleController.instance().redirectCopyDepth(target, sourceTarget)) {
            return true;
        }

        OpenGlSceneMsaaController.instance().syncSourceDepthBeforeCopy(sourceTarget);
        return false;
    }

    /**
     * Lets the MSAA controller replace the main scene framebuffer for a render pass.
     */
    public static Integer overrideFramebuffer(GpuTextureView colorView, GpuTexture depthTexture, int originalFramebufferId) {
        if (colorView instanceof com.mojang.blaze3d.opengl.GlTextureView glTextureView) {
            return OpenGlSceneMsaaController.instance().overrideFramebuffer(glTextureView, depthTexture, originalFramebufferId);
        }

        return null;
    }

    /**
     * Lets the MSAA controller replace the main scene framebuffer when an older
     * modern encoder builds the framebuffer from the color texture instead of
     * from a texture view.
     */
    public static Integer overrideFramebuffer(GpuTexture colorTexture, GpuTexture depthTexture, int originalFramebufferId) {
        return OpenGlSceneMsaaController.instance().overrideFramebuffer(colorTexture, depthTexture, originalFramebufferId);
    }

    public static void mirrorColorClearToMsaa(GpuTexture colorTexture, int clearColor) {
        OpenGlSceneMsaaController.instance().mirrorClearColorIfNeeded(colorTexture, clearColor);
    }

    public static void mirrorDepthClearToMsaa(GpuTexture depthTexture, double clearDepth) {
        OpenGlSceneMsaaController.instance().mirrorClearDepthIfNeeded(depthTexture, clearDepth);
    }

    public static void mirrorColorDepthClearToMsaa(GpuTexture colorTexture, int clearColor, GpuTexture depthTexture, double clearDepth) {
        OpenGlSceneMsaaController.instance().mirrorClearColorAndDepthIfNeeded(colorTexture, clearColor, depthTexture, clearDepth);
    }

    public static void resolveMsaaAfterRenderPass() {
        OpenGlSceneMsaaController.instance().onRenderPassFinished();
    }

    /**
     * Handles the debug-view toggle from both keyboard descriptors used inside the modern range.
     */
    public static void toggleEdgeDebug(Minecraft minecraft, int key, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (key != GLFW.GLFW_KEY_K) {
            return;
        }

        long now = Util.getMillis();
        if (now - lastEdgeDebugToggleMs < EDGE_DEBUG_TOGGLE_DEBOUNCE_MS) {
            callbackInfo.setReturnValue(true);
            return;
        }

        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        lastEdgeDebugToggleMs = now;
        boolean enabled = runtime.toggleDebugViews();
        minecraft.gui.setOverlayMessage(
                Component.literal("Salt's Anti Aliasing Edge View: " + (enabled ? "ON" : "OFF")),
                false
        );
        callbackInfo.setReturnValue(true);
    }
}
