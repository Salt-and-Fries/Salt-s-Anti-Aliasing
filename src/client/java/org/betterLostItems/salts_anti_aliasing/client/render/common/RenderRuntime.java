package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.compat.LoadedMods;
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
 * Coordinates the shared anti-aliasing state machine for the modern Minecraft renderer.
 *
 * <p>The runtime intentionally sits between two worlds:</p>
 *
 * <ul>
 *     <li>The version-stable mod model: config values, mode semantics, pass planning,
 *     metrics, and debug state.</li>
 *     <li>The modern 1.21.8-1.21.11 Minecraft renderer: {@link GameRenderer}, render
 *     targets, post chains, and OpenGL/GPU controllers.</li>
 * </ul>
 *
 * <p>Future legacy or mid-version jars should provide their own platform runtime/adapters
 * while keeping the config and pass-planning contracts compatible with this class.</p>
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
    private boolean loggedSodiumMsaaFallback;

    /**
     * Creates a render runtime with the collaborators or initial state supplied by the caller.
     * @param configManager config manager supplied by Minecraft or the caller
     * @param backend backend supplied by Minecraft or the caller
     * @param passManager pass manager supplied by Minecraft or the caller
     * @param planner planner supplied by Minecraft or the caller
     * @param scenePostProcessor scene post processor supplied by Minecraft or the caller
     * @param edgeDebugAnalyzer edge debug analyzer supplied by Minecraft or the caller
     * @param performanceMetricsRecorder performance metrics recorder supplied by Minecraft or the
     * caller
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
     * Builds the modern runtime from disk config and renderer capabilities.
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
     * @return active mode value produced or selected by this code path
     */
    public AntiAliasingMode activeMode() {
        return configManager.mode();
    }

    /**
     * Returns config snapshot for callers that need to coordinate UI, mixin, or render behavior.
     * @return config snapshot value produced or selected by this code path
     */
    public AntiAliasingConfig configSnapshot() {
        return configManager.snapshot();
    }

    /**
     * Coordinates cycle mode within the anti-aliasing render, configuration, or compatibility flow.
     * @return cycle mode value produced or selected by this code path
     */
    public AntiAliasingMode cycleMode() {
        return setMode(nextSupportedMode(activeMode()));
    }

    /**
     * Updates mode and refreshes dependent render state when the setting affects active passes or
     * targets.
     * @param mode requested anti-aliasing mode
     * @return set mode value produced or selected by this code path
     */
    public AntiAliasingMode setMode(AntiAliasingMode mode) {
        AntiAliasingMode clampedMode = resolveSupportedMode(AntiAliasingMode.clampImplemented(mode));
        configManager.edit(config -> config.mode = clampedMode);
        edgeDebugAnalyzer.reset(clampedMode);
        rebuildPipeline();
        return activeMode();
    }

    /**
     * Coordinates sharpen strength within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return sharpen strength value produced or selected by this code path
     */
    public float sharpenStrength() {
        return configManager.snapshot().sharpenStrength;
    }

    /**
     * Updates sharpen strength and refreshes dependent render state when the setting affects active
     * passes or targets.
     * @param sharpenStrength sharpen strength supplied by Minecraft or the caller
     * @return set sharpen strength value produced or selected by this code path
     */
    public float setSharpenStrength(float sharpenStrength) {
        configManager.edit(config -> config.sharpenStrength = sharpenStrength);
        return this.sharpenStrength();
    }

    /**
     * Coordinates msaa sample level within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return msaa sample level value produced or selected by this code path
     */
    public MsaaSampleLevel msaaSampleLevel() {
        return configManager.snapshot().msaaSampleLevel;
    }

    /**
     * Updates msaa sample level and refreshes dependent render state when the setting affects active
     * passes or targets.
     * @param sampleLevel MSAA sample-count preset selected by config or UI
     * @return set msaa sample level value produced or selected by this code path
     */
    public MsaaSampleLevel setMsaaSampleLevel(MsaaSampleLevel sampleLevel) {
        configManager.edit(config -> config.msaaSampleLevel = sampleLevel);
        return msaaSampleLevel();
    }

    /**
     * Coordinates ssaa scale level within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return ssaa scale level value produced or selected by this code path
     */
    public SsaaScaleLevel ssaaScaleLevel() {
        return configManager.snapshot().ssaaScaleLevel;
    }

    /**
     * Updates ssaa scale level and refreshes dependent render state when the setting affects active
     * passes or targets.
     * @param scaleLevel SSAA scale preset selected by config or UI
     * @return set ssaa scale level value produced or selected by this code path
     */
    public SsaaScaleLevel setSsaaScaleLevel(SsaaScaleLevel scaleLevel) {
        configManager.edit(config -> config.ssaaScaleLevel = scaleLevel);
        rebuildPipeline();
        return ssaaScaleLevel();
    }

    /**
     * Coordinates upscale quality preset within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @return upscale quality preset value produced or selected by this code path
     */
    public NisUpscaleQualityPreset upscaleQualityPreset() {
        return configManager.snapshot().nisUpscaleQualityPreset;
    }

    /**
     * Updates upscale quality preset and refreshes dependent render state when the setting affects
     * active passes or targets.
     * @param preset quality preset selected by config or UI
     * @return set upscale quality preset value produced or selected by this code path
     */
    public NisUpscaleQualityPreset setUpscaleQualityPreset(NisUpscaleQualityPreset preset) {
        configManager.edit(config -> config.nisUpscaleQualityPreset = preset);
        rebuildPipeline();
        return upscaleQualityPreset();
    }

    /**
     * Coordinates backend name within the anti-aliasing render, configuration, or compatibility flow.
     * @return backend name value produced or selected by this code path
     */
    public String backendName() {
        return backend.type().displayName();
    }

    /**
     * Coordinates backend type within the anti-aliasing render, configuration, or compatibility flow.
     * @return backend type value produced or selected by this code path
     */
    public RenderBackendType backendType() {
        return backend.type();
    }

    /**
     * Coordinates describe plan within the anti-aliasing render, configuration, or compatibility flow.
     * @return describe plan value produced or selected by this code path
     */
    public String describePlan() {
        return backendName() + " -> " + passManager.orderedPassIds();
    }

    /**
     * Coordinates debug views enabled within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return debug views enabled value produced or selected by this code path
     */
    public boolean debugViewsEnabled() {
        return configManager.snapshot().debugViewsEnabled;
    }

    /**
     * Coordinates toggle debug views within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return toggle debug views value produced or selected by this code path
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
     * Coordinates edge debug stats within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return edge debug stats value produced or selected by this code path
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
     * Coordinates record rendered frame within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param frameTimeNs frame duration in nanoseconds
     * @param displayedFps FPS value reported by Minecraft for the same frame
     */
    public void recordRenderedFrame(long frameTimeNs, int displayedFps) {
        performanceMetricsRecorder.recordFrame(frameTimeNs, displayedFps);
    }

    /**
     * Coordinates shutdown metrics within the anti-aliasing render, configuration, or compatibility
     * flow.
     */
    public void shutdownMetrics() {
        performanceMetricsRecorder.close();
    }

    /**
     * Gives the modern OpenGL controllers a chance to redirect scene rendering.
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
     * Coordinates create scene post processor within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param backendType backend type supplied by Minecraft or the caller
     * @param edgeDebugAnalyzer edge debug analyzer supplied by Minecraft or the caller
     * @return create scene post processor value produced or selected by this code path
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
     * Coordinates next supported mode within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param mode requested anti-aliasing mode
     * @return next supported mode value produced or selected by this code path
     */
    private AntiAliasingMode nextSupportedMode(AntiAliasingMode mode) {
        AntiAliasingMode nextMode = AntiAliasingMode.clampImplemented(mode);
        do {
            nextMode = nextMode.nextImplemented();
        } while (!isModeSupported(nextMode));

        return nextMode;
    }

    /**
     * Coordinates resolve supported mode within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param mode requested anti-aliasing mode
     * @return resolve supported mode value produced or selected by this code path
     */
    private AntiAliasingMode resolveSupportedMode(AntiAliasingMode mode) {
        if (isModeSupported(mode)) {
            return mode;
        }

        if (mode == AntiAliasingMode.MSAA && LoadedMods.sodiumLoaded()) {
            logSodiumMsaaFallback();
            return AntiAliasingMode.FXAA;
        }

        return AntiAliasingMode.OFF;
    }

    /**
     * Checks whether is mode supported without mutating configuration or render state.
     * @param mode requested anti-aliasing mode
     * @return is mode supported value produced or selected by this code path
     */
    private static boolean isModeSupported(AntiAliasingMode mode) {
        return mode != AntiAliasingMode.MSAA || !LoadedMods.sodiumLoaded();
    }

    /**
     * Coordinates log sodium msaa fallback within the anti-aliasing render, configuration, or
     * compatibility flow.
     */
    private void logSodiumMsaaFallback() {
        if (loggedSodiumMsaaFallback) {
            return;
        }

        loggedSodiumMsaaFallback = true;
        SaltsAntiAliasing.LOGGER.warn("Sodium is loaded, so MSAA is disabled to avoid Sodium chunk rendering disappearing");
    }

    /**
     * Coordinates ensure active mode supported within the anti-aliasing render, configuration, or
     * compatibility flow.
     */
    private void ensureActiveModeSupported() {
        AntiAliasingMode supportedMode = resolveSupportedMode(activeMode());
        if (supportedMode != activeMode()) {
            configManager.edit(config -> config.mode = supportedMode);
            edgeDebugAnalyzer.reset(supportedMode);
        }
    }
}
