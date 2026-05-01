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

    public AntiAliasingMode activeMode() {
        return configManager.mode();
    }

    public AntiAliasingConfig configSnapshot() {
        return configManager.snapshot();
    }

    public AntiAliasingMode cycleMode() {
        return setMode(nextSupportedMode(activeMode()));
    }

    public AntiAliasingMode setMode(AntiAliasingMode mode) {
        AntiAliasingMode clampedMode = resolveSupportedMode(AntiAliasingMode.clampImplemented(mode));
        configManager.edit(config -> config.mode = clampedMode);
        edgeDebugAnalyzer.reset(clampedMode);
        rebuildPipeline();
        return activeMode();
    }

    public float sharpenStrength() {
        return configManager.snapshot().sharpenStrength;
    }

    public float setSharpenStrength(float sharpenStrength) {
        configManager.edit(config -> config.sharpenStrength = sharpenStrength);
        return this.sharpenStrength();
    }

    public MsaaSampleLevel msaaSampleLevel() {
        return configManager.snapshot().msaaSampleLevel;
    }

    public MsaaSampleLevel setMsaaSampleLevel(MsaaSampleLevel sampleLevel) {
        configManager.edit(config -> config.msaaSampleLevel = sampleLevel);
        return msaaSampleLevel();
    }

    public SsaaScaleLevel ssaaScaleLevel() {
        return configManager.snapshot().ssaaScaleLevel;
    }

    public SsaaScaleLevel setSsaaScaleLevel(SsaaScaleLevel scaleLevel) {
        configManager.edit(config -> config.ssaaScaleLevel = scaleLevel);
        rebuildPipeline();
        return ssaaScaleLevel();
    }

    public NisUpscaleQualityPreset upscaleQualityPreset() {
        return configManager.snapshot().nisUpscaleQualityPreset;
    }

    public NisUpscaleQualityPreset setUpscaleQualityPreset(NisUpscaleQualityPreset preset) {
        configManager.edit(config -> config.nisUpscaleQualityPreset = preset);
        rebuildPipeline();
        return upscaleQualityPreset();
    }

    public String backendName() {
        return backend.type().displayName();
    }

    public RenderBackendType backendType() {
        return backend.type();
    }

    public String describePlan() {
        return backendName() + " -> " + passManager.orderedPassIds();
    }

    public boolean debugViewsEnabled() {
        return configManager.snapshot().debugViewsEnabled;
    }

    public boolean toggleDebugViews() {
        configManager.edit(config -> config.debugViewsEnabled = !config.debugViewsEnabled);
        boolean enabled = configManager.snapshot().debugViewsEnabled;
        if (!enabled) {
            edgeDebugAnalyzer.reset(activeMode());
        }
        return enabled;
    }

    public EdgeDebugStats edgeDebugStats() {
        return edgeDebugAnalyzer.latestStats();
    }

    /**
     * Applies post-processing that should affect only the 3D scene, not menus or HUD text.
     */
    public void applyScenePostProcessing(GameRenderer gameRenderer) {
        scenePostProcessor.apply(gameRenderer, configManager.snapshot());
    }

    public void recordRenderedFrame(long frameTimeNs, int displayedFps) {
        performanceMetricsRecorder.recordFrame(frameTimeNs, displayedFps);
    }

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

    private static ScenePostProcessor createScenePostProcessor(
            RenderBackendType backendType,
            EdgeDebugAnalyzer edgeDebugAnalyzer
    ) {
        return switch (backendType) {
            case OPENGL -> new OpenGlScenePostProcessor(edgeDebugAnalyzer);
            case VULKAN -> NoOpScenePostProcessor.INSTANCE;
        };
    }

    private AntiAliasingMode nextSupportedMode(AntiAliasingMode mode) {
        AntiAliasingMode nextMode = AntiAliasingMode.clampImplemented(mode);
        do {
            nextMode = nextMode.nextImplemented();
        } while (!isModeSupported(nextMode));

        return nextMode;
    }

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

    private static boolean isModeSupported(AntiAliasingMode mode) {
        return mode != AntiAliasingMode.MSAA || !LoadedMods.sodiumLoaded();
    }

    private void logSodiumMsaaFallback() {
        if (loggedSodiumMsaaFallback) {
            return;
        }

        loggedSodiumMsaaFallback = true;
        SaltsAntiAliasing.LOGGER.warn("Sodium is loaded, so MSAA is disabled to avoid Sodium chunk rendering disappearing");
    }

    private void ensureActiveModeSupported() {
        AntiAliasingMode supportedMode = resolveSupportedMode(activeMode());
        if (supportedMode != activeMode()) {
            configManager.edit(config -> config.mode = supportedMode);
            edgeDebugAnalyzer.reset(supportedMode);
        }
    }
}
