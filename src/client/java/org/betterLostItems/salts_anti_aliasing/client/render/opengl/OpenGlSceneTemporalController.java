package org.betterLostItems.salts_anti_aliasing.client.render.opengl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL30C;

/**
 * Maintains temporal jitter, camera motion estimates, and history textures for TAA.
 */
public final class OpenGlSceneTemporalController {
    private static final OpenGlSceneTemporalController INSTANCE = new OpenGlSceneTemporalController();
    private static final ResourceLocation TAA_EFFECT_ID = ResourceLocation.fromNamespaceAndPath(SaltsAntiAliasing.MOD_ID, "taa");
    private static final int BOOTSTRAP_FRAME_COUNT = 8;
    private static final float TAA_BASE_HISTORY_WEIGHT = 0.92f;
    private static final float TAA_LUMA_REJECTION = 1.35f;
    private static final float TAA_DEPTH_REJECTION = 7.5f;
    private static final float TAA_NEIGHBORHOOD_CLAMP = 0.22f;
    private static final float[] JITTER_SEQUENCE_X = {
            0.0f, -0.25f, 0.25f, -0.375f, 0.125f, -0.125f, 0.375f, -0.4375f
    };
    private static final float[] JITTER_SEQUENCE_Y = {
            -0.16666667f, 0.16666667f, -0.3888889f, -0.05555556f,
            0.2777778f, -0.2777778f, 0.05555556f, 0.3888889f
    };

    private TextureTarget historyTarget;
    private boolean historyValid;
    private boolean activeSequence;
    private boolean inactiveStateClean = true;
    private ClientLevel lastLevel;
    private int jitterFrameIndex;
    private int bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
    private float currentJitterUvX;
    private float currentJitterUvY;
    private float previousJitterUvX;
    private float previousJitterUvY;
    private float currentJitterClipX;
    private float currentJitterClipY;
    private final Matrix4f jitteredProjection = new Matrix4f();
    private Vec3 lastCameraPosition;
    private float lastCameraXRot;
    private float lastCameraYRot;
    private float cameraMotionAmount;

    private OpenGlSceneTemporalController() {
    }

    public static OpenGlSceneTemporalController instance() {
        return INSTANCE;
    }

    public void prepareFrameJitter(boolean taaActive, int width, int height) {
        if (!taaActive || width <= 0 || height <= 0) {
            if (!inactiveStateClean) {
                clearJitter();
            }
            return;
        }

        inactiveStateClean = false;
        previousJitterUvX = currentJitterUvX;
        previousJitterUvY = currentJitterUvY;
        currentJitterUvX = JITTER_SEQUENCE_X[jitterFrameIndex];
        currentJitterUvY = JITTER_SEQUENCE_Y[jitterFrameIndex];
        jitterFrameIndex = (jitterFrameIndex + 1) & 7;
        currentJitterClipX = (currentJitterUvX * 2.0f) / width;
        currentJitterClipY = (-currentJitterUvY * 2.0f) / height;
    }

    public Matrix4f jitterProjection(Matrix4f projectionMatrix, boolean taaActive) {
        if (!taaActive) {
            return projectionMatrix;
        }

        jitteredProjection.set(projectionMatrix);
        jitteredProjection.m20(jitteredProjection.m20() + currentJitterClipX);
        jitteredProjection.m21(jitteredProjection.m21() + currentJitterClipY);
        return jitteredProjection;
    }

    public void apply(GameRenderer gameRenderer) {
        RenderSystem.assertOnRenderThread();

        Minecraft minecraft = gameRenderer.getMinecraft();
        ClientLevel level = minecraft.level;
        if (level == null) {
            resetForInactiveMode();
            return;
        }

        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        if (!mainTarget.useDepth || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        ensureHistoryTarget(mainTarget.width, mainTarget.height);
        updateCameraMotion(gameRenderer);

        if (lastLevel != null && lastLevel != level) {
            bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
            historyValid = false;
            clearCameraMotion();
        }

        if (!activeSequence || !historyValid || lastLevel != level || bootstrapFramesRemaining > 0) {
            lastLevel = level;
            copyCurrentFrameToHistory(mainTarget);
            historyValid = true;
            activeSequence = true;
            if (bootstrapFramesRemaining > 0) {
                bootstrapFramesRemaining--;
            }
            return;
        }

        try {
            PostChain postChain = OpenGlPostChainManager.get(minecraft, TAA_EFFECT_ID);
            RenderTarget chainHistory = postChain.getTempTarget("taa_history");
            if (chainHistory == null) {
                return;
            }

            OpenGlFramebufferBlitter.blitColor(historyTarget, chainHistory, GL30C.GL_NEAREST);
            OpenGlDynamicUniforms.updateTaa(postChain, this);
            postChain.process(minecraft.getTimer().getGameTimeDeltaTicks());
            copyCurrentFrameToHistory(mainTarget);
            activeSequence = true;
            lastLevel = level;
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to process TAA post chain", exception);
        }
    }

    public void resetForInactiveMode() {
        if (inactiveStateClean) {
            return;
        }

        activeSequence = false;
        historyValid = false;
        lastLevel = null;
        bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
        clearJitter();
        clearCameraMotion();
        inactiveStateClean = true;
    }

    private void ensureHistoryTarget(int width, int height) {
        if (historyTarget == null) {
            destroyResources();
            historyTarget = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            historyValid = false;
            bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
            return;
        }

        if (historyTarget.width != width || historyTarget.height != height) {
            historyTarget.resize(width, height, Minecraft.ON_OSX);
            historyValid = false;
            bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
        }
    }

    private void copyCurrentFrameToHistory(RenderTarget mainTarget) {
        if (historyTarget == null || mainTarget.getColorTextureId() == -1 || historyTarget.getColorTextureId() == -1) {
            historyValid = false;
            return;
        }

        OpenGlFramebufferBlitter.blitColor(mainTarget, historyTarget, GL30C.GL_NEAREST);
        historyValid = true;
    }

    private void destroyResources() {
        if (historyTarget != null) {
            historyTarget.destroyBuffers();
            historyTarget = null;
        }
    }

    public float baseHistoryWeight() {
        return TAA_BASE_HISTORY_WEIGHT;
    }

    public float lumaRejection() {
        return TAA_LUMA_REJECTION;
    }

    public float depthRejection() {
        return TAA_DEPTH_REJECTION;
    }

    public float neighborhoodClamp() {
        return TAA_NEIGHBORHOOD_CLAMP;
    }

    public float currentJitterTexelX() {
        return currentJitterUvX;
    }

    public float currentJitterTexelY() {
        return currentJitterUvY;
    }

    public float previousJitterTexelX() {
        return previousJitterUvX;
    }

    public float previousJitterTexelY() {
        return previousJitterUvY;
    }

    public float cameraMotionAmount() {
        return cameraMotionAmount;
    }

    private void updateCameraMotion(GameRenderer gameRenderer) {
        Camera camera = gameRenderer.getMainCamera();
        if (camera == null || !camera.isInitialized()) {
            clearCameraMotion();
            return;
        }

        Vec3 cameraPosition = camera.getPosition();
        float cameraXRot = camera.getXRot();
        float cameraYRot = camera.getYRot();
        if (lastCameraPosition == null) {
            cameraMotionAmount = 0.0f;
        } else {
            float positionDelta = (float) cameraPosition.distanceTo(lastCameraPosition);
            float rotationDelta = Math.abs(cameraXRot - lastCameraXRot)
                    + Math.abs(Mth.wrapDegrees(cameraYRot - lastCameraYRot));
            float positionMotion = clamp01((positionDelta - 0.02f) / 0.30f);
            float rotationMotion = clamp01((rotationDelta - 0.6f) / 8.0f);
            cameraMotionAmount = Math.max(positionMotion * 0.85f, rotationMotion);
        }

        lastCameraPosition = cameraPosition;
        lastCameraXRot = cameraXRot;
        lastCameraYRot = cameraYRot;
    }

    private void clearJitter() {
        jitterFrameIndex = 0;
        currentJitterUvX = 0.0f;
        currentJitterUvY = 0.0f;
        previousJitterUvX = 0.0f;
        previousJitterUvY = 0.0f;
        currentJitterClipX = 0.0f;
        currentJitterClipY = 0.0f;
    }

    private void clearCameraMotion() {
        lastCameraPosition = null;
        lastCameraXRot = 0.0f;
        lastCameraYRot = 0.0f;
        cameraMotionAmount = 0.0f;
    }

    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

}
