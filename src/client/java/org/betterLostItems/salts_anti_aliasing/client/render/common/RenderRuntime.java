package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.ConfigManager;
import org.betterLostItems.salts_anti_aliasing.client.config.MsaaSampleLevel;
import org.betterLostItems.salts_anti_aliasing.client.config.NisUpscaleQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.config.SsaaScaleLevel;
import org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugAnalyzer;
import org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugStats;
import org.betterLostItems.salts_anti_aliasing.client.metrics.PerformanceMetricsRecorder;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackend;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackendType;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlSceneMsaaController;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlScenePostProcessor;
import org.betterLostItems.salts_anti_aliasing.client.render.opengl.OpenGlSceneScaleController;
import net.minecraft.client.renderer.GameRenderer;

/**
 * Coordinates the shared anti-aliasing state machine for the Fabric 26.1.2 renderer.
 *
 * <p>The runtime intentionally sits between two worlds:</p>
 *
 * <ul>
 *     <li>The version-stable mod model: config values, mode semantics, pass planning,
 *     metrics, and debug state.</li>
 *     <li>The Fabric 26.1.2 Minecraft renderer: {@link GameRenderer}, render targets,
 *     post chains, and OpenGL/GPU controllers.</li>
 * </ul>
 *
 * <p>Other jar families should provide their own platform runtime/adapters while keeping
 * the config and pass-planning contracts compatible with this class.</p>
 */
public final class RenderRuntime {
    private final ConfigManager configManager;
    private final RenderBackend backend;
    private final PassManager passManager;
    private final RenderPipelinePlanner planner;
    private final ScenePostProcessor scenePostProcessor;
    private final EdgeDebugAnalyzer edgeDebugAnalyzer;
    private final PerformanceMetricsRecorder performanceMetricsRecorder;
    private PipelinePlan currentPlan;

    /**
     * Creates a render runtime instance with the collaborators or initial state supplied by the
     * caller.
     * @param configManager config manager value supplied by the caller or Minecraft callback
     * @param backend backend value supplied by the caller or Minecraft callback
     * @param passManager pass manager value supplied by the caller or Minecraft callback
     * @param planner planner value supplied by the caller or Minecraft callback
     * @param scenePostProcessor scene post processor value supplied by the caller or Minecraft
     * callback
     * @param edgeDebugAnalyzer edge debug analyzer value supplied by the caller or Minecraft
     * callback
     * @param performanceMetricsRecorder performance metrics recorder value supplied by the caller
     * or Minecraft callback
     */
    private RenderRuntime(
            ConfigManager configManager,
            RenderBackend backend,
            PassManager passManager,
            RenderPipelinePlanner planner,
            ScenePostProcessor scenePostProcessor,
            EdgeDebugAnalyzer edgeDebugAnalyzer,
            PerformanceMetricsRecorder performanceMetricsRecorder
    ) {
        this.configManager = configManager;
        this.backend = backend;
        this.passManager = passManager;
        this.planner = planner;
        this.scenePostProcessor = scenePostProcessor;
        this.edgeDebugAnalyzer = edgeDebugAnalyzer;
        this.performanceMetricsRecorder = performanceMetricsRecorder;
        this.currentPlan = new PipelinePlan(backend.type(), backend.declaredTargets(), passManager.passes());
    }

    /**
     * Builds the Fabric 26.1.2 runtime from disk config and renderer capabilities.
     */
    public static RenderRuntime bootstrap() {
        ConfigManager configManager = ConfigManager.createDefault();
        configManager.load();

        RenderBackend backend = new RenderBackendSelector().select(configManager.snapshot());
        EdgeDebugAnalyzer edgeDebugAnalyzer = new EdgeDebugAnalyzer();
        RenderRuntime runtime = new RenderRuntime(
                configManager,
                backend,
                new PassManager(),
                new RenderPipelinePlanner(),
                createScenePostProcessor(backend.type(), edgeDebugAnalyzer),
                edgeDebugAnalyzer,
                new PerformanceMetricsRecorder(
                        configManager::recordMetricsEnabled,
                        configManager::mode,
                        configManager::snapshot
                )
        );
        runtime.ensureActiveModeSupported();
        runtime.rebuildPipeline();
        return runtime;
    }

    /**
     * Returns active mode for callers that need to coordinate UI, mixin, or render behavior.
     * @return currently selected anti-aliasing mode
     */
    public AntiAliasingMode activeMode() {
        return configManager.mode();
    }

    /**
     * Returns config snapshot for callers that need to coordinate UI, mixin, or render behavior.
     * @return defensive copy of the current configuration
     */
    public AntiAliasingConfig configSnapshot() {
        return configManager.snapshot();
    }

    /**
     * Advances to the next implemented anti-aliasing mode, then rebuilds render state as needed.
     * @return cycle mode produced by this helper
     */
    public AntiAliasingMode cycleMode() {
        return setMode(nextSupportedMode(activeMode()));
    }

    /**
     * Applies a requested mode after clamping unknown choices to a safe fallback.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @return the normalized value after the update is applied
     */
    public AntiAliasingMode setMode(AntiAliasingMode mode) {
        AntiAliasingMode clampedMode = AntiAliasingMode.clampImplemented(mode);
        configManager.edit(config -> config.mode = clampedMode);
        edgeDebugAnalyzer.reset(clampedMode);
        rebuildPipeline();
        return activeMode();
    }

    /**
     * Handles sharpen strength as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return sharpening strength associated with the selected preset
     */
    public float sharpenStrength() {
        return configManager.snapshot().sharpenStrength;
    }

    /**
     * Updates sharpen strength and keeps dependent render state in sync when necessary.
     * @param sharpenStrength normalized sharpening amount requested by the user interface
     * @return the normalized value after the update is applied
     */
    public float setSharpenStrength(float sharpenStrength) {
        configManager.edit(config -> config.sharpenStrength = sharpenStrength);
        return this.sharpenStrength();
    }

    /**
     * Coordinates msaa sample level within the anti-aliasing render, configuration, or compatibility flow.
     * @return msaa sample level produced by this helper
     */
    public MsaaSampleLevel msaaSampleLevel() {
        return configManager.snapshot().msaaSampleLevel;
    }

    /**
     * Updates msaa sample level and keeps dependent render state in sync when necessary.
     * @param sampleLevel MSAA sample count preset selected by the user or loaded from config
     * @return the normalized value after the update is applied
     */
    public MsaaSampleLevel setMsaaSampleLevel(MsaaSampleLevel sampleLevel) {
        configManager.edit(config -> config.msaaSampleLevel = sampleLevel);
        return msaaSampleLevel();
    }

    /**
     * Handles ssaa scale level as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return ssaa scale level produced by this helper
     */
    public SsaaScaleLevel ssaaScaleLevel() {
        return configManager.snapshot().ssaaScaleLevel;
    }

    /**
     * Updates ssaa scale level and keeps dependent render state in sync when necessary.
     * @param scaleLevel SSAA render-scale preset selected by the user or loaded from config
     * @return the normalized value after the update is applied
     */
    public SsaaScaleLevel setSsaaScaleLevel(SsaaScaleLevel scaleLevel) {
        configManager.edit(config -> config.ssaaScaleLevel = scaleLevel);
        rebuildPipeline();
        return ssaaScaleLevel();
    }

    /**
     * Coordinates upscale quality preset within the anti-aliasing render, configuration, or compatibility flow.
     * @return upscale quality preset produced by this helper
     */
    public NisUpscaleQualityPreset upscaleQualityPreset() {
        return configManager.snapshot().nisUpscaleQualityPreset;
    }

    /**
     * Updates upscale quality preset and keeps dependent render state in sync when necessary.
     * @param preset quality preset selected by the user or loaded from config
     * @return the normalized value after the update is applied
     */
    public NisUpscaleQualityPreset setUpscaleQualityPreset(NisUpscaleQualityPreset preset) {
        configManager.edit(config -> config.nisUpscaleQualityPreset = preset);
        rebuildPipeline();
        return upscaleQualityPreset();
    }

    /**
     * Handles backend name as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return human-readable backend name
     */
    public String backendName() {
        return backend.type().displayName();
    }

    /**
     * Handles backend type as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return selected backend type
     */
    public RenderBackendType backendType() {
        return backend.type();
    }

    /**
     * Handles describe plan as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return describe plan produced by this helper
     */
    public String describePlan() {
        return backendName() + " -> " + passManager.orderedPassIds();
    }

    /**
     * Coordinates debug views enabled within the anti-aliasing render, configuration, or compatibility flow.
     * @return whether the operation or state is enabled
     */
    public boolean debugViewsEnabled() {
        return configManager.snapshot().debugViewsEnabled;
    }

    /**
     * Toggles debug views and performs any cleanup required when the feature is disabled.
     * @return the updated enabled state after the toggle is applied
     */
    public boolean toggleDebugViews() {
        configManager.edit(config -> config.debugViewsEnabled = !config.debugViewsEnabled);
        boolean enabled = configManager.snapshot().debugViewsEnabled;
        if (!enabled) {
            edgeDebugAnalyzer.reset(activeMode());
        }
        return enabled;
    }

    /**
     * Handles edge debug stats as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return edge debug stats produced by this helper
     */
    public EdgeDebugStats edgeDebugStats() {
        return edgeDebugAnalyzer.latestStats();
    }

    /**
     * Applies post-processing that should affect only the 3D scene, not menus or HUD text.
     */
    public void applyScenePostProcessing(GameRenderer gameRenderer) {
        scenePostProcessor.apply(gameRenderer, configManager.snapshot());
    }

    /**
     * Feeds frame timing into the metrics recorder after a rendered world frame completes.
     * @param frameTimeNs duration of the rendered frame in nanoseconds
     * @param displayedFps FPS value reported by Minecraft for the same frame sample
     */
    public void recordRenderedFrame(long frameTimeNs, int displayedFps) {
        performanceMetricsRecorder.recordFrame(frameTimeNs, displayedFps);
    }

    /**
     * Flushes and closes metrics resources during client shutdown paths.
     */
    public void shutdownMetrics() {
        performanceMetricsRecorder.close();
    }

    /**
     * Gives the OpenGL controllers a chance to redirect scene rendering.
     */
    public void beginSceneRendering(GameRenderer gameRenderer) {
        if (backend.type() == RenderBackendType.OPENGL) {
            OpenGlSceneScaleController.instance().beginSceneRendering(gameRenderer, configManager.snapshot());
            OpenGlSceneMsaaController.instance().beginSceneRendering(gameRenderer, configManager.snapshot());
        }
    }

    /**
     * Resolves any redirected scene rendering back into Minecraft's main target.
     */
    public void endSceneRendering(GameRenderer gameRenderer) {
        if (backend.type() == RenderBackendType.OPENGL) {
            OpenGlSceneMsaaController.instance().endSceneRendering(gameRenderer, configManager.snapshot());
            OpenGlSceneScaleController.instance().endSceneRendering(gameRenderer, configManager.snapshot());
        }
    }

    /**
     * Recomputes the backend-neutral pass plan after a mode or quality setting changes.
     */
    public void rebuildPipeline() {
        currentPlan = planner.plan(backend, configManager.snapshot());
        passManager.replaceAll(currentPlan.passes());
        backend.declareTargets(currentPlan.targets());
        SaltsAntiAliasing.LOGGER.info(
                "Configured {} backend with passes {} and {} targets",
                backend.type().displayName(),
                passManager.orderedPassIds(),
                currentPlan.targets().size()
        );
    }

    /**
     * Coordinates create scene post processor within the anti-aliasing render, configuration, or compatibility flow.
     * @param backendType backend type value supplied by the caller or Minecraft callback
     * @param edgeDebugAnalyzer edge debug analyzer value supplied by the caller or Minecraft
     * callback
     * @return a newly created instance configured for the current mod/runtime context
     */
    private static ScenePostProcessor createScenePostProcessor(
            RenderBackendType backendType,
            EdgeDebugAnalyzer edgeDebugAnalyzer
    ) {
        return switch (backendType) {
            case OPENGL -> new OpenGlScenePostProcessor(edgeDebugAnalyzer);
            case VULKAN -> NoOpScenePostProcessor.INSTANCE;
        };
    }

    /**
     * Coordinates next supported mode within the anti-aliasing render, configuration, or compatibility flow.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @return next supported mode produced by this helper
     */
    private AntiAliasingMode nextSupportedMode(AntiAliasingMode mode) {
        return AntiAliasingMode.clampImplemented(mode).nextImplemented();
    }

    /**
     * Coordinates ensure active mode supported within the anti-aliasing render, configuration, or compatibility flow.
     */
    private void ensureActiveModeSupported() {
        AntiAliasingMode supportedMode = AntiAliasingMode.clampImplemented(activeMode());
        if (supportedMode != activeMode()) {
            configManager.edit(config -> config.mode = supportedMode);
            edgeDebugAnalyzer.reset(supportedMode);
        }
    }
}
