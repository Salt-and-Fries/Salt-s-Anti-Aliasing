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
import net.minecraft.client.renderer.state.CameraRenderState;
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

/**
 * Maintains temporal jitter, camera motion estimates, and history textures for TAA.
 */
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

    /**
     * Creates a open gl scene temporal controller with the collaborators or initial state supplied by
     * the caller.
     */
    private OpenGlSceneTemporalController() {
    }

    /**
     * Coordinates instance within the anti-aliasing render, configuration, or compatibility flow.
     * @return instance value produced or selected by this code path
     */
    public static OpenGlSceneTemporalController instance() {
        return INSTANCE;
    }

    /**
     * Coordinates prepare frame jitter within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param taaActive taa active supplied by Minecraft or the caller
     * @param width width supplied by Minecraft or the caller
     * @param height height supplied by Minecraft or the caller
     */
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

    /**
     * Coordinates configure camera jitter within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param cameraRenderState camera render state supplied by Minecraft or the caller
     * @param taaActive taa active supplied by Minecraft or the caller
     * @return configure camera jitter value produced or selected by this code path
     */
    public CameraRenderState configureCameraJitter(CameraRenderState cameraRenderState, boolean taaActive) {
        return cameraRenderState;
    }

    /**
     * Coordinates jitter projection within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param projectionMatrix projection matrix supplied by Minecraft or the caller
     * @param taaActive taa active supplied by Minecraft or the caller
     * @return jitter projection value produced or selected by this code path
     */
    public Matrix4fc jitterProjection(Matrix4fc projectionMatrix, boolean taaActive) {
        if (!taaActive) {
            return projectionMatrix;
        }

        jitteredProjection.set(projectionMatrix);
        jitteredProjection.m20(jitteredProjection.m20() + currentJitterClipX);
        jitteredProjection.m21(jitteredProjection.m21() + currentJitterClipY);
        return jitteredProjection;
    }

    /**
     * Coordinates apply within the anti-aliasing render, configuration, or compatibility flow.
     * @param gameRenderer Minecraft renderer currently being intercepted or processed
     * @param resourcePool resource pool supplied by Minecraft or the caller
     */
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

    /**
     * Coordinates reset for inactive mode within the anti-aliasing render, configuration, or
     * compatibility flow.
     */
    public void resetForInactiveMode() {
        activeSequence = false;
        historyValid = false;
        lastLevel = null;
        bootstrapFramesRemaining = BOOTSTRAP_FRAME_COUNT;
        clearJitter();
        clearCameraMotion();
    }

    /**
     * Coordinates ensure history target within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param width width supplied by Minecraft or the caller
     * @param height height supplied by Minecraft or the caller
     */
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

    /**
     * Coordinates copy current frame to history within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param mainTarget main target supplied by Minecraft or the caller
     */
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

    /**
     * Coordinates destroy resources within the anti-aliasing render, configuration, or compatibility
     * flow.
     */
    private void destroyResources() {
        if (historyTarget != null) {
            historyTarget.destroyBuffers();
            historyTarget = null;
        }
    }

    /**
     * Coordinates base history weight within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return base history weight value produced or selected by this code path
     */
    public float baseHistoryWeight() {
        return TAA_BASE_HISTORY_WEIGHT;
    }

    /**
     * Coordinates luma rejection within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return luma rejection value produced or selected by this code path
     */
    public float lumaRejection() {
        return TAA_LUMA_REJECTION;
    }

    /**
     * Coordinates depth rejection within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return depth rejection value produced or selected by this code path
     */
    public float depthRejection() {
        return TAA_DEPTH_REJECTION;
    }

    /**
     * Coordinates neighborhood clamp within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return neighborhood clamp value produced or selected by this code path
     */
    public float neighborhoodClamp() {
        return TAA_NEIGHBORHOOD_CLAMP;
    }

    /**
     * Coordinates current jitter texel x within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @return current jitter texel x value produced or selected by this code path
     */
    public float currentJitterTexelX() {
        return currentJitterUvX;
    }

    /**
     * Coordinates current jitter texel y within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @return current jitter texel y value produced or selected by this code path
     */
    public float currentJitterTexelY() {
        return currentJitterUvY;
    }

    /**
     * Coordinates previous jitter texel x within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @return previous jitter texel x value produced or selected by this code path
     */
    public float previousJitterTexelX() {
        return previousJitterUvX;
    }

    /**
     * Coordinates previous jitter texel y within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @return previous jitter texel y value produced or selected by this code path
     */
    public float previousJitterTexelY() {
        return previousJitterUvY;
    }

    /**
     * Coordinates camera motion amount within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @return camera motion amount value produced or selected by this code path
     */
    public float cameraMotionAmount() {
        return cameraMotionAmount;
    }

    /**
     * Coordinates update camera motion within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param gameRenderer Minecraft renderer currently being intercepted or processed
     */
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

    /**
     * Coordinates clear jitter within the anti-aliasing render, configuration, or compatibility flow.
     */
    private void clearJitter() {
        jitterFrameIndex = 0;
        currentJitterUvX = 0.0f;
        currentJitterUvY = 0.0f;
        previousJitterUvX = 0.0f;
        previousJitterUvY = 0.0f;
        currentJitterClipX = 0.0f;
        currentJitterClipY = 0.0f;
    }

    /**
     * Coordinates clear camera motion within the anti-aliasing render, configuration, or compatibility
     * flow.
     */
    private void clearCameraMotion() {
        lastCameraPosition = null;
        lastCameraXRot = 0.0f;
        lastCameraYRot = 0.0f;
        cameraMotionAmount = 0.0f;
    }

    /**
     * Clamps the supplied value to the supported range before it can affect rendering or persisted
     * configuration.
     * @param value value being transformed or clamped
     * @return clamp01 value produced or selected by this code path
     */
    private static float clamp01(float value) {
        return Math.max(0.0f, Math.min(1.0f, value));
    }

    /**
     * Coordinates halton within the anti-aliasing render, configuration, or compatibility flow.
     * @param index index supplied by Minecraft or the caller
     * @param base base supplied by Minecraft or the caller
     * @return halton value produced or selected by this code path
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
     * Documents temporal target bundle behavior for Salt's Anti Aliasing. OpenGL backend code that
     * owns framebuffers, post chains, and GPU-side state.
     */
    private static final class TemporalTargetBundle implements PostChain.TargetBundle {
        private final Map<Identifier, ResourceHandle<RenderTarget>> targets = new HashMap<>();

        /**
         * Coordinates temporal target bundle within the anti-aliasing render, configuration, or
         * compatibility flow.
         * @param mainHandle main handle supplied by Minecraft or the caller
         * @param historyHandle history handle supplied by Minecraft or the caller
         */
        private TemporalTargetBundle(ResourceHandle<RenderTarget> mainHandle, ResourceHandle<RenderTarget> historyHandle) {
            targets.put(PostChain.MAIN_TARGET_ID, mainHandle);
            targets.put(HISTORY_TARGET_ID, historyHandle);
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
