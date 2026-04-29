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

public final class RenderRuntime {
    private final ConfigManager configManager;
    private final RenderBackend backend;
    private final PassManager passManager;
    private final RenderPipelinePlanner planner;
    private final ScenePostProcessor scenePostProcessor;
    private final EdgeDebugAnalyzer edgeDebugAnalyzer;
    private final PerformanceMetricsRecorder performanceMetricsRecorder;
    private PipelinePlan currentPlan;

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
        return setMode(activeMode().nextImplemented());
    }

    public AntiAliasingMode setMode(AntiAliasingMode mode) {
        AntiAliasingMode clampedMode = AntiAliasingMode.clampImplemented(mode);
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

    public void applyScenePostProcessing(GameRenderer gameRenderer) {
        scenePostProcessor.apply(gameRenderer, configManager.snapshot());
    }

    public void recordRenderedFrame(long frameTimeNs, int displayedFps) {
        performanceMetricsRecorder.recordFrame(frameTimeNs, displayedFps);
    }

    public void shutdownMetrics() {
        performanceMetricsRecorder.close();
    }

    public void beginSceneRendering(GameRenderer gameRenderer) {
        if (backend.type() == RenderBackendType.OPENGL) {
            OpenGlSceneScaleController.instance().beginSceneRendering(gameRenderer, configManager.snapshot());
            OpenGlSceneMsaaController.instance().beginSceneRendering(gameRenderer, configManager.snapshot());
        }
    }

    public void endSceneRendering(GameRenderer gameRenderer) {
        if (backend.type() == RenderBackendType.OPENGL) {
            OpenGlSceneMsaaController.instance().endSceneRendering(gameRenderer, configManager.snapshot());
            OpenGlSceneScaleController.instance().endSceneRendering(gameRenderer, configManager.snapshot());
        }
    }

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

    private static ScenePostProcessor createScenePostProcessor(RenderBackendType backendType, EdgeDebugAnalyzer edgeDebugAnalyzer) {
        return switch (backendType) {
            case OPENGL -> new OpenGlScenePostProcessor(edgeDebugAnalyzer);
            case VULKAN -> NoOpScenePostProcessor.INSTANCE;
        };
    }
}
