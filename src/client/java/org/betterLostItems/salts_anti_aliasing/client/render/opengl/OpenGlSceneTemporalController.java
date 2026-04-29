package org.betterLostItems.salts_anti_aliasing.client.render.opengl;

import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

public final class OpenGlSceneTemporalController {
    private static final OpenGlSceneTemporalController INSTANCE = new OpenGlSceneTemporalController();
    private static final String HISTORY_TARGET_LABEL = "Salt's TAA History";
    private static final Identifier HISTORY_TARGET_ID = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":taa_history");
    private static final Identifier TAA_EFFECT_ID = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":taa");
    private static final Set<Identifier> EXTERNAL_TARGETS = Set.of(PostChain.MAIN_TARGET_ID, HISTORY_TARGET_ID);
    private static final int BOOTSTRAP_FRAME_COUNT = 8;
    private static final float TAA_BASE_HISTORY_WEIGHT = 0.92f;
    private static final float TAA_LUMA_REJECTION = 1.35f;
    private static final float TAA_DEPTH_REJECTION = 7.5f;
    private static final float TAA_NEIGHBORHOOD_CLAMP = 0.22f;

    private TextureTarget historyTarget;
    private boolean historyValid;
    private boolean activeSequence;
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
        if (!taaActive) {
            clearJitter();
            return;
        }

        if (width <= 0 || height <= 0) {
            clearJitter();
            return;
        }

        previousJitterUvX = currentJitterUvX;
        previousJitterUvY = currentJitterUvY;
        currentJitterUvX = halton(jitterFrameIndex + 1, 2) - 0.5f;
        currentJitterUvY = halton(jitterFrameIndex + 1, 3) - 0.5f;
        jitterFrameIndex = (jitterFrameIndex + 1) % 8;
        currentJitterClipX = (currentJitterUvX * 2.0f) / width;
        currentJitterClipY = (-currentJitterUvY * 2.0f) / height;
    }

    public CameraRenderState configureCameraJitter(CameraRenderState cameraRenderState, boolean taaActive) {
        if (!taaActive) {
            return cameraRenderState;
        }

        cameraRenderState.projectionMatrix.m20(cameraRenderState.projectionMatrix.m20() + currentJitterClipX);
        cameraRenderState.projectionMatrix.m21(cameraRenderState.projectionMatrix.m21() + currentJitterClipY);
        return cameraRenderState;
    }

    public Matrix4fc jitterProjection(Matrix4fc projectionMatrix, boolean taaActive) {
        if (!taaActive) {
            return projectionMatrix;
        }

        jitteredProjection.set(projectionMatrix);
        jitteredProjection.m20(jitteredProjection.m20() + currentJitterClipX);
        jitteredProjection.m21(jitteredProjection.m21() + currentJitterClipY);
        return jitteredProjection;
    }

    public void apply(GameRenderer gameRenderer, CrossFrameResourcePool resourcePool) {
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

        PostChain postChain = minecraft.getShaderManager().getPostChain(TAA_EFFECT_ID, EXTERNAL_TARGETS);
        if (postChain == null) {
            return;
        }

        OpenGlDynamicUniforms.updateTaa(postChain, this);

        FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
        ResourceHandle<RenderTarget> mainHandle = frameGraphBuilder.importExternal("salts_taa_main", mainTarget);
        ResourceHandle<RenderTarget> historyHandle = frameGraphBuilder.importExternal("salts_taa_history", historyTarget);

        PostChain.TargetBundle targetBundle = new TemporalTargetBundle(mainHandle, historyHandle);
        postChain.addToFrame(frameGraphBuilder, mainTarget.width, mainTarget.height, targetBundle);
        frameGraphBuilder.execute(resourcePool);

        copyCurrentFrameToHistory(mainTarget);
        activeSequence = true;
        lastLevel = level;
    }

    public void resetForInactiveMode() {
        activeSequence = false;
        historyValid = false;
        lastLevel = null;
        bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
        clearJitter();
        clearCameraMotion();
    }

    private void ensureHistoryTarget(int width, int height) {
        if (historyTarget == null) {
            destroyResources();
            historyTarget = new TextureTarget(HISTORY_TARGET_LABEL, width, height, false);
            historyValid = false;
            bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
            return;
        }

        if (historyTarget.width != width || historyTarget.height != height) {
            historyTarget.resize(width, height);
            historyValid = false;
            bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
        }
    }

    private void copyCurrentFrameToHistory(RenderTarget mainTarget) {
        if (historyTarget == null
                || mainTarget.getColorTextureView() == null
                || historyTarget.getColorTextureView() == null) {
            historyValid = false;
            return;
        }

        try (var renderPass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> "Salt's TAA History Copy",
                historyTarget.getColorTextureView(),
                OptionalInt.empty()
        )) {
            renderPass.setPipeline(RenderPipelines.TRACY_BLIT);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.bindTexture(
                    "InSampler",
                    mainTarget.getColorTextureView(),
                    RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST)
            );
            renderPass.draw(0, 3);
        }

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

        Vec3 cameraPosition = camera.position();
        float cameraXRot = camera.xRot();
        float cameraYRot = camera.yRot();
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

    private static float halton(int index, int base) {
        float result = 0.0f;
        float fraction = 1.0f / base;
        int value = index;
        while (value > 0) {
            result += fraction * (value % base);
            value /= base;
            fraction /= base;
        }
        return result;
    }

    private static final class TemporalTargetBundle implements PostChain.TargetBundle {
        private final Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();

        private TemporalTargetBundle(ResourceHandle<RenderTarget> mainHandle, ResourceHandle<RenderTarget> historyHandle) {
            targets.put(PostChain.MAIN_TARGET_ID, mainHandle);
            targets.put(HISTORY_TARGET_ID, historyHandle);
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
