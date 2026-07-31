package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.framegraph.FrameGraphBuilder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.resource.CrossFrameResourcePool;
import com.mojang.blaze3d.resource.ResourceHandle;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Maintains temporal jitter and history textures used by temporal anti-aliasing across consecutive
 * rendered frames.
 */
public final class VulkanSceneTemporalController {
    private static final VulkanSceneTemporalController INSTANCE = new VulkanSceneTemporalController();
    private static final String HISTORY_TARGET_LABEL = "Salt's TAA History";
    private static final Identifier HISTORY_TARGET_ID = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":taa_history");
    private static final Identifier TAA_EFFECT_ID = Identifier.parse(SaltsAntiAliasing.MOD_ID + ":taa");
    private static final Set<Identifier> EXTERNAL_TARGETS = Set.of(PostChain.MAIN_TARGET_ID, HISTORY_TARGET_ID);
    private static final int BOOTSTRAP_FRAME_COUNT = 1;
    private static final int TAA_JITTER_PHASE_COUNT = 16;
    private static final float TAA_BASE_HISTORY_WEIGHT = 0.90f;
    private static final float TAA_LUMA_REJECTION = 1.0f;
    private static final float TAA_DEPTH_REJECTION = 1.0f;
    private static final float TAA_VARIANCE_GAMMA = 1.25f;
    private static final double CAMERA_CUT_DISTANCE_SQUARED = 16.0;
    private static final float CAMERA_CUT_ROTATION_DEGREES = 45.0f;
    private static final float CAMERA_FOV_RESET_RADIANS = (float) Math.toRadians(1.0);

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
    private int jitterWidth;
    private int jitterHeight;
    private int jitterPhaseCount;
    private final Matrix4f jitteredProjection = new Matrix4f();
    private final Matrix4f currentViewProjection = new Matrix4f();
    private final Matrix4f previousViewProjection = new Matrix4f();
    private final Matrix4f currentJitteredViewProjection = new Matrix4f();
    private boolean hasViewProjection;
    private boolean resetHistoryThisFrame = true;
    private long frameIndex;
    private ClientLevel capturedLevel;
    private Vec3 capturedCameraPosition;
    private Vec3 previousCameraPosition;
    private float capturedCameraXRot;
    private float capturedCameraYRot;
    private float currentCameraNear = 0.05f;
    private float currentCameraFar = 64.0f;
    private float currentCameraFovY = (float) Math.toRadians(70.0);
    private Vec3 lastCameraPosition;
    private float lastCameraXRot;
    private float lastCameraYRot;
    private float cameraMotionAmount;

    /**
     * Creates a Vulkan scene temporal controller instance with the collaborators or initial state
     * supplied by the caller.
     */
    private VulkanSceneTemporalController() {
    }

    /**
     * Handles instance as part of the anti-aliasing render, configuration, or compatibility flow.
     * @return singleton controller instance
     */
    public static VulkanSceneTemporalController instance() {
        return INSTANCE;
    }

    /**
     * Coordinates prepare frame jitter within the anti-aliasing render, configuration, or compatibility flow.
     * @param temporalActive whether a temporal renderer consumes the frame
     * @param width width value supplied by the caller or Minecraft callback
     * @param height height value supplied by the caller or Minecraft callback
     * @param requestedPhaseCount temporal jitter sequence length, or zero for native TAA
     */
    public void prepareFrameJitter(
            boolean temporalActive,
            int width,
            int height,
            int requestedPhaseCount
    ) {
        if (!temporalActive) {
            clearJitter();
            return;
        }

        if (width <= 0 || height <= 0) {
            clearJitter();
            return;
        }

        int nextPhaseCount = requestedPhaseCount > 0 ? requestedPhaseCount : TAA_JITTER_PHASE_COUNT;
        boolean sequenceChanged = jitterWidth != width
                || jitterHeight != height
                || jitterPhaseCount != nextPhaseCount;
        resetHistoryThisFrame = sequenceChanged;
        if (sequenceChanged) {
            jitterFrameIndex = 0;
            previousJitterUvX = 0.0f;
            previousJitterUvY = 0.0f;
            jitterWidth = width;
            jitterHeight = height;
            jitterPhaseCount = nextPhaseCount;
        } else {
            previousJitterUvX = currentJitterUvX;
            previousJitterUvY = currentJitterUvY;
        }

        currentJitterUvX = halton(jitterFrameIndex + 1, 2) - 0.5f;
        currentJitterUvY = halton(jitterFrameIndex + 1, 3) - 0.5f;
        jitterFrameIndex = (jitterFrameIndex + 1) % jitterPhaseCount;
        currentJitterClipX = (currentJitterUvX * 2.0f) / width;
        currentJitterClipY = (-currentJitterUvY * 2.0f) / height;
    }

    /**
     * Captures vanilla's final unjittered world projection and returns a clip-space translated copy
     * for temporal rendering. The input matrix belongs to Minecraft and is never mutated.
     */
    public Matrix4f configureProjection(
            Matrix4f projectionMatrix,
            CameraRenderState cameraRenderState,
            boolean temporalActive
    ) {
        if (!temporalActive || projectionMatrix == null) {
            return projectionMatrix;
        }

        jitteredProjection.translation(currentJitterClipX, currentJitterClipY, 0.0f).mul(projectionMatrix);
        captureFrameState(projectionMatrix, jitteredProjection, cameraRenderState);
        return jitteredProjection;
    }

    /**
     * Handles apply as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param gameRenderer Minecraft game renderer whose scene target or post-processing phase is
     * being coordinated
     * @param resourcePool resource pool value supplied by the caller or Minecraft callback
     */
    public void apply(GameRenderer gameRenderer, CrossFrameResourcePool resourcePool) {
        RenderSystem.assertOnRenderThread();

        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            resetForInactiveMode();
            return;
        }

        RenderTarget mainTarget = gameRenderer.mainRenderTarget();
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

        if (!activeSequence
                || !historyValid
                || lastLevel != level
                || bootstrapFramesRemaining > 0
                || resetHistoryThisFrame) {
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

        VulkanDynamicUniforms.updateTaa(postChain, this);

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

    /**
     * Coordinates reset for inactive mode within the anti-aliasing render, configuration, or compatibility flow.
     */
    public void resetForInactiveMode() {
        activeSequence = false;
        historyValid = false;
        lastLevel = null;
        bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
        clearJitter();
        clearCameraMotion();
        clearViewProjection();
    }

    /**
     * Coordinates ensure history target within the anti-aliasing render, configuration, or compatibility flow.
     * @param width width value supplied by the caller or Minecraft callback
     * @param height height value supplied by the caller or Minecraft callback
     */
    private void ensureHistoryTarget(int width, int height) {
        if (historyTarget == null || !historyTarget.useDepth) {
            destroyResources();
            historyTarget = new TextureTarget(HISTORY_TARGET_LABEL, width, height, true, GpuFormat.RGBA8_UNORM);
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

    /**
     * Coordinates copy current frame to history within the anti-aliasing render, configuration, or compatibility flow.
     * @param mainTarget main target value supplied by the caller or Minecraft callback
     */
    private void copyCurrentFrameToHistory(RenderTarget mainTarget) {
        if (historyTarget == null
                || mainTarget.getColorTextureView() == null
                || historyTarget.getColorTextureView() == null) {
            historyValid = false;
            return;
        }

        VulkanColorBlitter.blitColor(mainTarget, historyTarget);
        if (mainTarget.getDepthTexture() != null && historyTarget.getDepthTexture() != null) {
            historyTarget.copyDepthFrom(mainTarget);
        }

        historyValid = true;
    }

    /**
     * Coordinates destroy resources within the anti-aliasing render, configuration, or compatibility flow.
     */
    private void destroyResources() {
        if (historyTarget != null) {
            historyTarget.destroyBuffers();
            historyTarget = null;
        }
    }

    /**
     * Coordinates base history weight within the anti-aliasing render, configuration, or compatibility flow.
     * @return base history weight produced by this helper
     */
    public float baseHistoryWeight() {
        return TAA_BASE_HISTORY_WEIGHT;
    }

    /**
     * Handles luma rejection as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return luma rejection produced by this helper
     */
    public float lumaRejection() {
        return TAA_LUMA_REJECTION;
    }

    /**
     * Handles depth rejection as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return depth rejection produced by this helper
     */
    public float depthRejection() {
        return TAA_DEPTH_REJECTION;
    }

    /**
     * Returns the standard-deviation multiplier used to clip reprojected history.
     */
    public float varianceGamma() {
        return TAA_VARIANCE_GAMMA;
    }

    /**
     * Coordinates current jitter texel x within the anti-aliasing render, configuration, or compatibility flow.
     * @return current jitter texel x produced by this helper
     */
    public float currentJitterTexelX() {
        return currentJitterUvX;
    }

    /**
     * Coordinates current jitter texel y within the anti-aliasing render, configuration, or compatibility flow.
     * @return current jitter texel y produced by this helper
     */
    public float currentJitterTexelY() {
        return currentJitterUvY;
    }

    /**
     * Coordinates previous jitter texel x within the anti-aliasing render, configuration, or compatibility flow.
     * @return previous jitter texel x produced by this helper
     */
    public float previousJitterTexelX() {
        return previousJitterUvX;
    }

    /**
     * Coordinates previous jitter texel y within the anti-aliasing render, configuration, or compatibility flow.
     * @return previous jitter texel y produced by this helper
     */
    public float previousJitterTexelY() {
        return previousJitterUvY;
    }

    /**
     * Coordinates camera motion amount within the anti-aliasing render, configuration, or compatibility flow.
     * @return camera motion amount produced by this helper
     */
    public float cameraMotionAmount() {
        return cameraMotionAmount;
    }

    /**
     * Reports whether temporal consumers should discard native history for this frame.
     */
    public boolean resetHistoryThisFrame() {
        return resetHistoryThisFrame || !hasViewProjection;
    }

    public long frameIndex() {
        return frameIndex;
    }

    public float[] currentViewProjectionArray() {
        return matrixArray(currentViewProjection);
    }

    public float[] previousViewProjectionArray() {
        return matrixArray(hasViewProjection ? previousViewProjection : currentViewProjection);
    }

    public float[] currentClipToWorldArray() {
        Matrix4f inverted = new Matrix4f(currentViewProjection);
        if (Math.abs(inverted.determinant()) <= 1.0e-6f) {
            inverted.identity();
        } else {
            inverted.invert();
        }
        return matrixArray(inverted);
    }

    public float[] currentJitteredClipToWorldArray() {
        Matrix4f inverted = new Matrix4f(currentJitteredViewProjection);
        if (Math.abs(inverted.determinant()) <= 1.0e-6f) {
            inverted.identity();
        } else {
            inverted.invert();
        }
        return matrixArray(inverted);
    }

    public float cameraNearPlane() {
        return currentCameraNear;
    }

    public float cameraFarPlane() {
        return currentCameraFar;
    }

    public float cameraFovY() {
        return currentCameraFovY;
    }

    /**
     * Returns the world-space translation that moves a direction reconstructed around the current
     * camera to the same direction around the previous camera. Clear-depth sky pixels use this to
     * retain rotational motion without inventing parallax from camera translation.
     */
    public float[] cameraTranslationToPreviousArray() {
        if (capturedCameraPosition == null || previousCameraPosition == null) {
            return new float[] {0.0f, 0.0f, 0.0f};
        }

        return new float[] {
                (float) (previousCameraPosition.x - capturedCameraPosition.x),
                (float) (previousCameraPosition.y - capturedCameraPosition.y),
                (float) (previousCameraPosition.z - capturedCameraPosition.z)
        };
    }

    /**
     * Captures the final unjittered and jittered camera transforms used by the world pass.
     */
    private void captureFrameState(
            Matrix4f projectionMatrix,
            Matrix4f jitteredProjectionMatrix,
            CameraRenderState cameraRenderState
    ) {
        if (cameraRenderState == null || cameraRenderState.viewRotationMatrix == null) {
            resetHistoryThisFrame = true;
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        Vec3 position = cameraRenderState.pos;
        if (capturedLevel != null && capturedLevel != level) {
            resetHistoryThisFrame = true;
        }
        if (capturedCameraPosition != null
                && position != null
                && position.distanceToSqr(capturedCameraPosition) > CAMERA_CUT_DISTANCE_SQUARED) {
            resetHistoryThisFrame = true;
        }
        if (capturedCameraPosition != null) {
            float rotationDelta = Math.abs(cameraRenderState.xRot - capturedCameraXRot)
                    + Math.abs(Mth.wrapDegrees(cameraRenderState.yRot - capturedCameraYRot));
            if (rotationDelta > CAMERA_CUT_ROTATION_DEGREES) {
                resetHistoryThisFrame = true;
            }
        }

        if (hasViewProjection) {
            previousViewProjection.set(currentViewProjection);
            previousCameraPosition = capturedCameraPosition;
        }

        Matrix4f view = new Matrix4f(cameraRenderState.viewRotationMatrix);
        if (cameraRenderState.pos != null) {
            view.translate(
                    (float) -cameraRenderState.pos.x,
                    (float) -cameraRenderState.pos.y,
                    (float) -cameraRenderState.pos.z
            );
        }

        currentViewProjection.set(projectionMatrix).mul(view);
        currentJitteredViewProjection.set(jitteredProjectionMatrix).mul(view);

        float nextCameraFar = Math.max(currentCameraNear, cameraRenderState.depthFar);
        float nextCameraFovY = currentCameraFovY;
        float projectionScaleY = cameraRenderState.projectionMatrix.m11();
        if (Math.abs(projectionScaleY) > 1.0e-6f) {
            nextCameraFovY = 2.0f * (float) Math.atan(1.0f / Math.abs(projectionScaleY));
        }
        if (hasViewProjection
                && (Math.abs(nextCameraFovY - currentCameraFovY) > CAMERA_FOV_RESET_RADIANS
                || Math.abs(nextCameraFar - currentCameraFar) > 1.0f)) {
            resetHistoryThisFrame = true;
        }

        capturedLevel = level;
        capturedCameraPosition = position;
        capturedCameraXRot = cameraRenderState.xRot;
        capturedCameraYRot = cameraRenderState.yRot;
        currentCameraFar = nextCameraFar;
        currentCameraFovY = nextCameraFovY;
        if (!hasViewProjection) {
            previousViewProjection.set(currentViewProjection);
            previousCameraPosition = position;
            resetHistoryThisFrame = true;
            hasViewProjection = true;
        }
        frameIndex++;
    }

    /**
     * Coordinates update camera motion within the anti-aliasing render, configuration, or compatibility flow.
     * @param gameRenderer Minecraft game renderer whose scene target or post-processing phase is
     * being coordinated
     */
    private void updateCameraMotion(GameRenderer gameRenderer) {
        Camera camera = gameRenderer.mainCamera();
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

    /**
     * Handles clear jitter as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     */
    private void clearJitter() {
        jitterFrameIndex = 0;
        currentJitterUvX = 0.0f;
        currentJitterUvY = 0.0f;
        previousJitterUvX = 0.0f;
        previousJitterUvY = 0.0f;
        currentJitterClipX = 0.0f;
        currentJitterClipY = 0.0f;
        jitterWidth = 0;
        jitterHeight = 0;
        jitterPhaseCount = 0;
        resetHistoryThisFrame = true;
    }

    /**
     * Coordinates clear camera motion within the anti-aliasing render, configuration, or compatibility flow.
     */
    private void clearCameraMotion() {
        lastCameraPosition = null;
        lastCameraXRot = 0.0f;
        lastCameraYRot = 0.0f;
        cameraMotionAmount = 0.0f;
    }

    private void clearViewProjection() {
        hasViewProjection = false;
        resetHistoryThisFrame = true;
        frameIndex = 0L;
        capturedLevel = null;
        capturedCameraPosition = null;
        previousCameraPosition = null;
        capturedCameraXRot = 0.0f;
        capturedCameraYRot = 0.0f;
        currentViewProjection.identity();
        previousViewProjection.identity();
        currentJitteredViewProjection.identity();
    }

    private static float[] matrixArray(Matrix4f matrix) {
        float[] values = new float[16];
        matrix.get(values);
        return values;
    }

    /**
     * Clamps the supplied value to the inclusive 0..1 range used by shader uniforms and blend weights.
     * @param value value supplied by the caller or Minecraft callback
     * @return value clamped to the inclusive 0..1 range
     */
    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /**
     * Handles halton as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param index index value supplied by the caller or Minecraft callback
     * @param base base value supplied by the caller or Minecraft callback
     * @return halton produced by this helper
     */
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

    /**
     * Implements temporal target bundle behavior for Salt's Anti Aliasing. This code owns
     * render-target redirection, post-processing, and Minecraft framebuffer coordination.
     */
    private static final class TemporalTargetBundle implements PostChain.TargetBundle {
        private final Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();

        /**
         * Coordinates temporal target bundle within the anti-aliasing render, configuration, or compatibility flow.
         * @param mainHandle main handle value supplied by the caller or Minecraft callback
         * @param historyHandle history handle value supplied by the caller or Minecraft callback
         */
        private TemporalTargetBundle(ResourceHandle<RenderTarget> mainHandle, ResourceHandle<RenderTarget> historyHandle) {
            targets.put(PostChain.MAIN_TARGET_ID, mainHandle);
            targets.put(HISTORY_TARGET_ID, historyHandle);
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
