package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.api.GpuFormat;
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
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.RenderTuning;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
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
    private static final Set<Identifier> EXTERNAL_TARGETS = Set.of(PostChain.MAIN_TARGET_ID, HISTORY_TARGET_ID, Identifier.parse(SaltsAntiAliasing.MOD_ID+":taa_motion"));
    private static final int BOOTSTRAP_FRAME_COUNT = 1;
    private static final double CAMERA_CUT_DISTANCE_SQUARED = 16.0;
    private static final float CAMERA_CUT_ROTATION_DEGREES = 45.0f;
    private static final float CAMERA_FOV_RESET_RADIANS = (float) Math.toRadians(1.0);

    private RenderTuning tuning = new RenderTuning();
    private boolean resolveWithoutHistory;
    private TextureTarget historyTarget;
    private TextureTarget motionTarget;
    private static final Identifier MOTION_TARGET_ID=Identifier.parse(SaltsAntiAliasing.MOD_ID+":taa_motion");

    private boolean configurationResetPending;
    private int lastTextureBias;
    public void configureTuning(RenderTuning tuning) {
        int bias=VulkanTextureDetail.activeQuarterSteps();
        if(this.tuning.entityMotion!=tuning.entityMotion || this.tuning.taaHistoryFilter!=tuning.taaHistoryFilter
                || this.tuning.debugView!=tuning.debugView || lastTextureBias!=bias
                || this.tuning.motionVertexBudget!=tuning.motionVertexBudget
                || this.tuning.dlssParticleAlphaThreshold!=tuning.dlssParticleAlphaThreshold)configurationResetPending=true;
        lastTextureBias=bias;
        this.tuning=tuning.copy();
    }
    public RenderTuning tuning() { return tuning; }
    public RenderTarget motionTarget() { return motionTarget; }
    private boolean historyValid;
    private boolean capturedFrameValid;
    public boolean hasValidFrameMatrices() { return capturedFrameValid; }
    private boolean activeSequence;
    private AntiAliasingMode lastTemporalMode;
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
    private final TemporalCameraMatrices cameraMatrices = new TemporalCameraMatrices();
    private boolean hasViewProjection;
    private boolean resetHistoryThisFrame = true;
    private String historyResetReason = "initialization";
    private boolean historyResetRecorded;
    private long frameIndex;
    private ClientLevel capturedLevel;
    private Object capturedPerspective;
    private Object capturedCameraEntity;
    private long previousCaptureNanos;
    private Vec3 capturedCameraPosition;
    private Vec3 previousCameraPosition;
    private float capturedCameraXRot;
    private float capturedCameraYRot;
    private float currentCameraNear = 0.05f;
    private float currentCameraFar = 64.0f;
    private float currentCameraFovY = (float) Math.toRadians(70.0);
    private final Matrix4f currentProjection = new Matrix4f();
    private final Matrix4f currentViewRotation = new Matrix4f();
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
    public long allocatedTargetBytes() { return VulkanOwnedTargetMemory.bytes(historyTarget,motionTarget); }

    public static VulkanSceneTemporalController instance() {
        return INSTANCE;
    }

    /**
     * Coordinates prepare frame jitter within the anti-aliasing render, configuration, or compatibility flow.
     * @param temporalActive whether a temporal renderer consumes the frame
     * @param width width value supplied by the caller or Minecraft callback
     * @param height height value supplied by the caller or Minecraft callback
     * @param requestedPhaseCount temporal jitter sequence length, or zero for native TAA
     * @param temporalMode active consumer, used to invalidate history when changing temporal modes
     */
    public void prepareFrameJitter(
            boolean temporalActive,
            int width,
            int height,
            int requestedPhaseCount,
            AntiAliasingMode temporalMode
    ) {
        if (!temporalActive) {
            resetForInactiveMode();
            return;
        }

        if (lastTemporalMode != temporalMode) {
            resetForInactiveMode();
            lastTemporalMode = temporalMode;
        }

        if (width <= 0 || height <= 0) {
            clearJitter();
            return;
        }

        int nextPhaseCount = requestedPhaseCount > 0 ? requestedPhaseCount : tuning.taaJitterPhases;
        boolean sequenceChanged = jitterWidth != width
                || jitterHeight != height
                || jitterPhaseCount != nextPhaseCount;
        resetHistoryThisFrame = sequenceChanged || configurationResetPending;
        if(configurationResetPending)historyResetReason="input_tuning_changed";
        configurationResetPending=false;
        if (sequenceChanged) {
            markHistoryReset("jitter_sequence_changed");
            MetricsDiagnostics.info("temporal", "jitter_sequence_configured", MetricsDiagnostics.fields(
                    "width", width,
                    "height", height,
                    "phaseCount", nextPhaseCount
            ));
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
            capturedFrameValid = false;
            return projectionMatrix;
        }

        capturedFrameValid = cameraRenderState != null
                && TemporalMatrixValidity.usable(projectionMatrix)
                && TemporalMatrixValidity.usable(cameraRenderState.viewRotationMatrix);
        if (!capturedFrameValid) {
            markHistoryReset("invalid_camera_matrix");
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
        if (!mainTarget.hasDepth() || mainTarget.width <= 0 || mainTarget.height <= 0) {
            return;
        }

        ensureHistoryTarget(mainTarget.width, mainTarget.height);
        updateCameraMotion(gameRenderer);

        if (lastLevel != null && lastLevel != level) {
            bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
            historyValid = false;
            markHistoryReset("client_level_changed");
            clearCameraMotion();
        }

        resolveWithoutHistory = !activeSequence || !historyValid || lastLevel != level
                || bootstrapFramesRemaining > 0 || resetHistoryThisFrame;
        if (!activeSequence
                || !historyValid
                || lastLevel != level
                || bootstrapFramesRemaining > 0
                || resetHistoryThisFrame) {
            if (!historyResetRecorded) {
                MetricsDiagnostics.info("temporal", "history_reset", MetricsDiagnostics.fields(
                        "reason", historyResetReason,
                        "activeSequence", activeSequence,
                        "historyValid", historyValid,
                        "levelChanged", lastLevel != null && lastLevel != level,
                        "bootstrapFramesRemaining", bootstrapFramesRemaining,
                        "resetHistoryThisFrame", resetHistoryThisFrame,
                        "frameIndex", frameIndex,
                        "width", mainTarget.width,
                        "height", mainTarget.height,
                        "jitterPhaseCount", jitterPhaseCount,
                        "cameraMotionAmount", cameraMotionAmount
                ));
                historyResetRecorded = true;
            }
            lastLevel = level;
            copyCurrentFrameToHistory(mainTarget);
            historyValid = true;
            activeSequence = true;
            if (bootstrapFramesRemaining > 0) {
                bootstrapFramesRemaining--;
            }
        }

        PostChain postChain = minecraft.getShaderManager().getPostChain(TAA_EFFECT_ID, EXTERNAL_TARGETS);
        if (postChain == null) {
            MetricsDiagnostics.warnOnce(
                    "taa-post-chain-missing",
                    "temporal",
                    "post_chain_unavailable",
                    MetricsDiagnostics.fields("effect", TAA_EFFECT_ID.toString())
            );
            throw new IllegalStateException("TAA resolve shader is unavailable");
        }

        VulkanMotionVectorRenderer.fsr().render(mainTarget,motionTarget,this);
        VulkanDynamicUniforms.updateTaa(postChain, this);

        FrameGraphBuilder frameGraphBuilder = new FrameGraphBuilder();
        ResourceHandle<RenderTarget> mainHandle = frameGraphBuilder.importExternal("salts_taa_main", mainTarget);
        ResourceHandle<RenderTarget> historyHandle = frameGraphBuilder.importExternal("salts_taa_history", historyTarget);

        ResourceHandle<RenderTarget> motionHandle=frameGraphBuilder.importExternal("salts_taa_motion",motionTarget);
        PostChain.TargetBundle targetBundle = new TemporalTargetBundle(mainHandle, historyHandle,motionHandle);
        postChain.addToFrame(frameGraphBuilder, mainTarget.width, mainTarget.height, targetBundle);
            try(var gpuTiming=VulkanGpuProfiler.scope("TAA.resolve")) { frameGraphBuilder.execute(resourcePool); }

        historyTarget.copyDepthFrom(mainTarget);
        historyValid=true;
        activeSequence = true;
        lastLevel = level;
        historyResetRecorded = false;
        historyResetReason = "none";
        resetHistoryThisFrame = false;
        org.betterLostItems.salts_anti_aliasing.client.render.common.RenderingHealth.success(AntiAliasingMode.TAA, mainTarget.width, mainTarget.height);
    }

    /**
     * Coordinates reset for inactive mode within the anti-aliasing render, configuration, or compatibility flow.
     */
    public void resetForInactiveMode() {
        boolean wasActive = activeSequence || historyValid || hasViewProjection;
        destroyResources();
        activeSequence = false;
        lastTemporalMode = null;
        historyValid = false;
        lastLevel = null;
        bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
        clearJitter();
        clearCameraMotion();
        clearViewProjection();
        historyResetReason = "inactive_mode";
        historyResetRecorded = false;
        if (wasActive) {
            MetricsDiagnostics.info("temporal", "sequence_deactivated", MetricsDiagnostics.fields(
                    "historyTargetAllocated", historyTarget != null
            ));
        }
    }

    /**
     * Coordinates ensure history target within the anti-aliasing render, configuration, or compatibility flow.
     * @param width width value supplied by the caller or Minecraft callback
     * @param height height value supplied by the caller or Minecraft callback
     */
    private void ensureHistoryTarget(int width, int height) {
        if(motionTarget==null)motionTarget=new TextureTarget("Salt's TAA Motion",width,height,GpuFormat.RG16_FLOAT,null);
        else if(motionTarget.width!=width || motionTarget.height!=height)motionTarget.resize(width,height);
        if (historyTarget == null || !historyTarget.hasDepth()) {
            if(historyTarget!=null)historyTarget.destroyBuffers();
            historyTarget = new TextureTarget(HISTORY_TARGET_LABEL, width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
            historyValid = false;
            bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
            markHistoryReset("history_target_created");
            MetricsDiagnostics.info("temporal", "history_target_created", MetricsDiagnostics.fields(
                    "width", width,
                    "height", height,
                    "format", GpuFormat.RGBA8_UNORM.toString(),
                    "depthEnabled", true
            ));
            return;
        }

        if (historyTarget.width != width || historyTarget.height != height) {
            int previousWidth = historyTarget.width;
            int previousHeight = historyTarget.height;
            historyTarget.resize(width, height);
            historyValid = false;
            bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
            markHistoryReset("history_target_resized");
            MetricsDiagnostics.info("temporal", "history_target_resized", MetricsDiagnostics.fields(
                    "previousWidth", previousWidth,
                    "previousHeight", previousHeight,
                    "width", width,
                    "height", height
            ));
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
            resetHistoryThisFrame = true;
            historyResetReason = "history_copy_target_unavailable";
            MetricsDiagnostics.warnOnce(
                    "taa-history-copy-target-unavailable",
                    "temporal",
                    "history_copy_skipped",
                    MetricsDiagnostics.fields(
                            "historyTargetAllocated", historyTarget != null,
                            "mainColorViewAvailable", mainTarget.getColorTextureView() != null,
                            "historyColorViewAvailable", historyTarget != null
                                    && historyTarget.getColorTextureView() != null
                    )
            );
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
        if(motionTarget!=null){motionTarget.destroyBuffers();motionTarget=null;}
        if (historyTarget != null) {
            MetricsDiagnostics.info("temporal", "history_target_destroyed", MetricsDiagnostics.fields(
                    "width", historyTarget.width,
                    "height", historyTarget.height,
                    "historyValid", historyValid
            ));
            historyTarget.destroyBuffers();
            historyTarget = null;
        }
    }

    /**
     * Coordinates base history weight within the anti-aliasing render, configuration, or compatibility flow.
     * @return base history weight produced by this helper
     */
    public float baseHistoryWeight() {
        return resolveWithoutHistory ? 0.0f : tuning.taaHistoryWeight;
    }

    /**
     * Handles luma rejection as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return luma rejection produced by this helper
     */
    public float lumaRejection() {
        return tuning.taaLumaRejection;
    }

    /**
     * Handles depth rejection as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return depth rejection produced by this helper
     */
    public float depthRejection() {
        return tuning.taaDepthRejection;
    }

    /**
     * Returns the standard-deviation multiplier used to clip reprojected history.
     */
    public float varianceGamma() {
        return tuning.taaVarianceGamma;
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
        return matrixArray(cameraMatrices.current());
    }

    public float[] previousViewProjectionArray() {
        return matrixArray(hasViewProjection ? cameraMatrices.previous() : cameraMatrices.current());
    }

    public float[] currentClipToWorldArray() {
        Matrix4f inverted = new Matrix4f(cameraMatrices.current());
        if (Math.abs(inverted.determinant()) <= 1.0e-6f) {
            inverted.identity();
        } else {
            inverted.invert();
        }
        return matrixArray(inverted);
    }

    public float[] currentJitteredClipToWorldArray() {
        Matrix4f inverted = new Matrix4f(cameraMatrices.jittered());
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

    public float[] currentProjectionArray() {
        return matrixArray(currentProjection);
    }

    /** Camera constants in the same rebased world coordinates as the temporal matrices. */
    public float[] dlssCameraParameters() {
        Matrix4f inverseView = new Matrix4f(currentViewRotation).invert();
        return new float[] {
                currentCameraNear, currentCameraFar, currentCameraFovY,
                Math.abs(currentProjection.m11() / currentProjection.m00()),
                0.0f, 0.0f, 0.0f,
                inverseView.m10(), inverseView.m11(), inverseView.m12(),
                inverseView.m00(), inverseView.m01(), inverseView.m02(),
                -inverseView.m20(), -inverseView.m21(), -inverseView.m22()
        };
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
            markHistoryReset("camera_state_unavailable");
            return;
        }

        ClientLevel level = Minecraft.getInstance().level;
        Vec3 position = cameraRenderState.pos;
        var minecraft = Minecraft.getInstance();
        Object perspective = minecraft.options.getCameraType();
        Object cameraEntity = minecraft.getCameraEntity();
        long now = System.nanoTime();
        if (hasViewProjection && (perspective != capturedPerspective || cameraEntity != capturedCameraEntity))
            markHistoryReset("camera_identity_changed");
        if (previousCaptureNanos != 0 && now - previousCaptureNanos > 1_000_000_000L)
            markHistoryReset("rendering_resumed");
        capturedPerspective = perspective;
        capturedCameraEntity = cameraEntity;
        previousCaptureNanos = now;
        if (capturedLevel != null && capturedLevel != level) {
            markHistoryReset("captured_level_changed");
        }
        if (capturedCameraPosition != null
                && position != null
                && position.distanceToSqr(capturedCameraPosition) > CAMERA_CUT_DISTANCE_SQUARED) {
            markHistoryReset("camera_position_cut");
        }
        if (capturedCameraPosition != null) {
            float rotationDelta = Math.abs(cameraRenderState.xRot - capturedCameraXRot)
                    + Math.abs(Mth.wrapDegrees(cameraRenderState.yRot - capturedCameraYRot));
            if (rotationDelta > CAMERA_CUT_ROTATION_DEGREES) {
                markHistoryReset("camera_rotation_cut");
            }
        }

        if (hasViewProjection) {
            previousCameraPosition = capturedCameraPosition;
        }

        cameraMatrices.update(projectionMatrix, jitteredProjectionMatrix, cameraRenderState.viewRotationMatrix,
                position == null ? 0.0 : position.x,
                position == null ? 0.0 : position.y,
                position == null ? 0.0 : position.z);
        currentProjection.set(projectionMatrix);
        currentViewRotation.set(cameraRenderState.viewRotationMatrix);

        float nextCameraFar = Math.max(currentCameraNear, cameraRenderState.depthFar);
        float nextCameraFovY = currentCameraFovY;
        float projectionScaleY = cameraRenderState.projectionMatrix.m11();
        if (Math.abs(projectionScaleY) > 1.0e-6f) {
            nextCameraFovY = 2.0f * (float) Math.atan(1.0f / Math.abs(projectionScaleY));
        }
        if (hasViewProjection
                && (Math.abs(nextCameraFovY - currentCameraFovY) > CAMERA_FOV_RESET_RADIANS
                || Math.abs(nextCameraFar - currentCameraFar) > 1.0f)) {
            markHistoryReset("camera_projection_changed");
        }

        capturedLevel = level;
        capturedCameraPosition = position;
        capturedCameraXRot = cameraRenderState.xRot;
        capturedCameraYRot = cameraRenderState.yRot;
        currentCameraFar = nextCameraFar;
        currentCameraFovY = nextCameraFovY;
        if (!hasViewProjection) {
            previousCameraPosition = position;
            markHistoryReset("view_projection_initialized");
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
        capturedFrameValid = false;
        hasViewProjection = false;
        markHistoryReset("view_projection_cleared");
        frameIndex = 0L;
        capturedLevel = null;
        capturedPerspective = capturedCameraEntity = null;
        previousCaptureNanos = 0;
        capturedCameraPosition = null;
        previousCameraPosition = null;
        capturedCameraXRot = 0.0f;
        capturedCameraYRot = 0.0f;
        cameraMatrices.reset();
    }

    private void markHistoryReset(String reason) {
        resetHistoryThisFrame = true;
        if (reason != null && !reason.isBlank()) {
            historyResetReason = reason;
        }
        historyResetRecorded = false;
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
        private TemporalTargetBundle(ResourceHandle<RenderTarget> mainHandle, ResourceHandle<RenderTarget> historyHandle,
                                     ResourceHandle<RenderTarget> motionHandle) {
            targets.put(PostChain.MAIN_TARGET_ID, mainHandle);
            targets.put(HISTORY_TARGET_ID, historyHandle);
            targets.put(MOTION_TARGET_ID,motionHandle);
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
