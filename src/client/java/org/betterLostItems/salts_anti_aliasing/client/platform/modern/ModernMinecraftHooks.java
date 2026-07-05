package org.betterLostItems.salts_anti_aliasing.client.platform.modern;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneDlssController;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneMsaaController;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneScaleController;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneTemporalController;
import org.joml.Matrix4fc;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Central bridge between Fabric 26.2 Minecraft hook points and the shared mod runtime.
 *
 * <p>Mixins should be as small and boring as possible. Their job is to land on a
 * Minecraft method that exists in this version family, collect the raw values exposed by
 * that method, and immediately delegate here. Keeping that delegation centralized gives
 * future version jars one obvious translation layer to replace while the core config,
 * pipeline planning, metrics, and mode semantics stay stable.</p>
 *
 * <p>This class is intentionally allowed to depend on Minecraft classes and on the modern
 * Vulkan/GPU controllers. Code below this layer should avoid knowing which mixin fired or
 * which Minecraft descriptor was used to reach the hook.</p>
 */
public final class ModernMinecraftHooks {
    private static final long EDGE_DEBUG_TOGGLE_DEBOUNCE_MS = 250L;
    private static long lastEdgeDebugToggleMs;

    /**
     * Creates a modern minecraft hooks instance with the collaborators or initial state supplied by
     * the caller.
     */
    private ModernMinecraftHooks() {
    }

    /**
     * Prepares the per-frame temporal jitter before the world projection and camera state are consumed.
     */
    public static void prepareTemporalJitter(GameRenderer gameRenderer) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        boolean taaActive = runtime != null && runtime.activeMode().usesHistoryBuffers();
        RenderTarget mainTarget = gameRenderer.mainRenderTarget();
        VulkanSceneTemporalController.instance().prepareFrameJitter(taaActive, mainTarget.width, mainTarget.height);
    }

    /**
     * Applies the current temporal jitter to the 26.2 camera render state.
     */
    public static CameraRenderState configureCameraJitter(CameraRenderState cameraRenderState) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        boolean taaActive = runtime != null && runtime.activeMode().usesHistoryBuffers();
        return VulkanSceneTemporalController.instance().configureCameraJitter(cameraRenderState, taaActive);
    }

    /**
     * Applies the current temporal jitter to the 26.2 projection-matrix argument.
     */
    public static Matrix4fc jitterProjection(Matrix4fc projectionMatrix) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        boolean taaActive = runtime != null && runtime.activeMode().usesHistoryBuffers();
        return VulkanSceneTemporalController.instance().jitterProjection(projectionMatrix, taaActive);
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
            Minecraft minecraft = Minecraft.getInstance();
            runtime.recordRenderedFrame(minecraft.getFrameTimeNs(), minecraft.getFps());
        }
    }

    /**
     * Lets the scaled-scene controller temporarily replace Minecraft's main render target.
     */
    public static RenderTarget overrideMainRenderTarget() {
        RenderTarget msaaTarget = VulkanSceneMsaaController.instance().overrideMainTarget();
        if (msaaTarget != null) {
            return msaaTarget;
        }

        RenderTarget dlssTarget = VulkanSceneDlssController.instance().overrideMainTarget();
        if (dlssTarget != null) {
            return dlssTarget;
        }

        return VulkanSceneScaleController.instance().overrideMainTarget();
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
     * Redirects color texture reads while a Vulkan scene controller owns the main target.
     */
    public static GpuTexture redirectColorTexture(RenderTarget target) {
        GpuTexture msaaTexture = VulkanSceneMsaaController.instance().overrideColorTexture(target);
        if (msaaTexture != null) {
            return msaaTexture;
        }

        GpuTexture dlssTexture = VulkanSceneDlssController.instance().overrideColorTexture(target);
        if (dlssTexture != null) {
            return dlssTexture;
        }

        return VulkanSceneScaleController.instance().overrideColorTexture(target);
    }

    /**
     * Redirects color texture view reads while a Vulkan scene controller owns the main target.
     */
    public static GpuTextureView redirectColorTextureView(RenderTarget target) {
        GpuTextureView msaaTextureView = VulkanSceneMsaaController.instance().overrideColorTextureView(target);
        if (msaaTextureView != null) {
            return msaaTextureView;
        }

        GpuTextureView dlssTextureView = VulkanSceneDlssController.instance().overrideColorTextureView(target);
        if (dlssTextureView != null) {
            return dlssTextureView;
        }

        return VulkanSceneScaleController.instance().overrideColorTextureView(target);
    }

    /**
     * Redirects depth texture reads while a Vulkan scene controller owns the main target.
     */
    public static GpuTexture redirectDepthTexture(RenderTarget target) {
        GpuTexture msaaTexture = VulkanSceneMsaaController.instance().overrideDepthTexture(target);
        if (msaaTexture != null) {
            return msaaTexture;
        }

        GpuTexture dlssTexture = VulkanSceneDlssController.instance().overrideDepthTexture(target);
        if (dlssTexture != null) {
            return dlssTexture;
        }

        return VulkanSceneScaleController.instance().overrideDepthTexture(target);
    }

    /**
     * Redirects depth texture view reads while a Vulkan scene controller owns the main target.
     */
    public static GpuTextureView redirectDepthTextureView(RenderTarget target) {
        GpuTextureView msaaTextureView = VulkanSceneMsaaController.instance().overrideDepthTextureView(target);
        if (msaaTextureView != null) {
            return msaaTextureView;
        }

        GpuTextureView dlssTextureView = VulkanSceneDlssController.instance().overrideDepthTextureView(target);
        if (dlssTextureView != null) {
            return dlssTextureView;
        }

        return VulkanSceneScaleController.instance().overrideDepthTextureView(target);
    }

    /**
     * Keeps depth-copy behavior coherent when Minecraft copies from or to a redirected scene target.
     */
    public static boolean redirectCopyDepth(RenderTarget target, RenderTarget sourceTarget) {
        return VulkanSceneMsaaController.instance().redirectCopyDepth(target, sourceTarget)
                || VulkanSceneDlssController.instance().redirectCopyDepth(target, sourceTarget)
                || VulkanSceneScaleController.instance().redirectCopyDepth(target, sourceTarget);
    }

    /**
     * Handles the debug-view toggle from Minecraft's debug-key path.
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
        minecraft.gui.hud.setOverlayMessage(
                Component.literal("Salt's Anti Aliasing Edge View: " + (enabled ? "ON" : "OFF")),
                false
        );
        callbackInfo.setReturnValue(true);
    }
}
