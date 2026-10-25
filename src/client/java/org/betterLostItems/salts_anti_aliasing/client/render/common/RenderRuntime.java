package org.betterLostItems.salts_anti_aliasing.client.render.common;

import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.config.RenderTuning;
import java.util.function.Consumer;
import org.betterLostItems.salts_anti_aliasing.client.config.ConfigManager;
import org.betterLostItems.salts_anti_aliasing.client.config.DlssQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.config.FsrQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.config.MsaaSampleLevel;
import org.betterLostItems.salts_anti_aliasing.client.config.NisUpscaleQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.config.SsaaScaleLevel;
import org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugAnalyzer;
import org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugStats;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
import org.betterLostItems.salts_anti_aliasing.client.metrics.PerformanceMetricsRecorder;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackend;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackendType;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderCapability;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanRenderBackend;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneDlssController;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneFsrController;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneMsaaController;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanScenePostProcessor;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneScaleController;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssRuntimeStatus;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntimeStatus;

import java.util.EnumSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Coordinates the shared anti-aliasing state machine for the Fabric 26.3 Vulkan renderer.
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
    private boolean rebuildPipelineWhenBackendReady;

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
     * Builds the Fabric 26.3 runtime from disk config and the active Minecraft renderer.
     */
    public static RenderRuntime bootstrap() {
        ConfigManager configManager = ConfigManager.createDefault();
        configManager.load();
        PerformanceMetricsRecorder performanceMetricsRecorder = new PerformanceMetricsRecorder(
                configManager::recordMetricsEnabled,
                configManager::mode,
                configManager::snapshot
        );
        MetricsDiagnostics.recordEnvironment();
        MetricsDiagnostics.info(
                "configuration",
                "configuration_loaded",
                "configuration", MetricsDiagnostics.configSnapshot(configManager.snapshot())
        );
        DlssRuntime.instance().configure(configManager.snapshot());
        FsrRuntime.instance().configure(configManager.snapshot());

        RenderBackend backend = new VulkanRenderBackend();
        recordGpuContext(backend);
        EdgeDebugAnalyzer edgeDebugAnalyzer = new EdgeDebugAnalyzer();
        RenderRuntime runtime = new RenderRuntime(
                configManager,
                backend,
                new PassManager(),
                new RenderPipelinePlanner(),
                new VulkanScenePostProcessor(edgeDebugAnalyzer),
                edgeDebugAnalyzer,
                performanceMetricsRecorder
        );
        runtime.ensureActiveModeSupported();
        runtime.rebuildPipeline();
        return runtime;
    }

    /**
     * Returns the saved mode, even when Vulkan is inactive and rendering is blocked.
     */
    public AntiAliasingMode activeMode() {
        return configManager.mode();
    }

    /**
     * Returns a copy of the saved configuration.
     */
    public AntiAliasingConfig configSnapshot() {
        return configManager.snapshot();
    }

    /** Sliders preview live; the containing screen commits once on close. */
    public void editTuning(Consumer<RenderTuning> editor) { configManager.editLive(config -> editor.accept(config.tuning)); }
    public void saveSettings() { configManager.save(); }
    public void editSettings(Consumer<AntiAliasingConfig> editor) { configManager.edit(editor); rebuildPipeline(); }
    public void editSettingsLive(Consumer<AntiAliasingConfig> editor) { configManager.editLive(editor); }

    private volatile boolean retryRequested;

    public void retryRendering() { retryRequested=true; }

    public String allocationStatusText() {
        return String.format(java.util.Locale.ROOT,"AA scene targets: %.1f MiB (estimate)",
                org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanOwnedTargetMemory.totalBytes()/1048576.0);
    }
    public String frameGenerationStatusText() {
        FsrRuntime f=FsrRuntime.instance();
        return "Game frames: "+f.renderedPresentCount()+" / display presents: "+f.frameGenerationPresentCount();
    }

    public String dlssMemoryStatusText() {
        long bytes = DlssRuntime.instance().estimatedVramBytes();
        return bytes < 0 ? "DLSS SDK VRAM estimate: unavailable"
                : String.format(java.util.Locale.ROOT,"DLSS SDK VRAM estimate: %.1f MiB", bytes / 1048576.0);
    }

    public String dlssMotionCoverageText() {
        var c = org.betterLostItems.salts_anti_aliasing.client.render.vulkan.EntityMotionGeometry.coverage();
        int particles = org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanParticleMotionRenderer.lastRenderedQuads();
        return "Moving vertices: " + c.movingVertices() + " / particles: " + particles;
    }

    public String dlssMotionCoverageDetails() {
        var c = org.betterLostItems.salts_anti_aliasing.client.render.vulkan.EntityMotionGeometry.coverage();
        return "Captured object vertices: " + c.capturedVertices() + " / budget " + c.vertexBudget()
                + "\nStable meshes: " + c.stableMeshes() + "; newly visible: " + c.newMeshes()
                + "\nChanged topology: " + c.topologyChanges() + "; capped: " + c.cappedMeshes()
                + "; unidentified captured submissions: " + c.unidentifiedMeshes()
                + "\nParticle billboards captured: "
                + org.betterLostItems.salts_anti_aliasing.client.render.vulkan.ParticleMotionGeometry.capturedQuads()
                + "; new/unmatched: "
                + org.betterLostItems.salts_anti_aliasing.client.render.vulkan.ParticleMotionGeometry.unmatchedQuads()
                  + "; capped: " + org.betterLostItems.salts_anti_aliasing.client.render.vulkan.ParticleMotionGeometry.droppedQuads()
                + "\nCounts cover intercepted renderers; third-party renderers may need explicit support.";
    }

    public String renderStatusText() {
        if (activeMode() == AntiAliasingMode.DLSS_SUPER_RESOLUTION && !dlssRuntimeStatus().ready())
            return dlssRuntimeStatus().message();
        if(!canUseAntiAliasing())return "Select Vulkan in Video Settings to use anti aliasing";
        if(Minecraft.getInstance().level==null)return "Ready - enter a world to preview";
        return RenderingHealth.describe(activeMode());
    }

    public void resetMethodSettings() {
        configManager.edit(c->{
            var defaults=new AntiAliasingConfig();
            c.sharpenStrength=defaults.sharpenStrength;
            c.tuning.sharpener=defaults.tuning.sharpener;c.tuning.sharpenLimit=defaults.tuning.sharpenLimit;
            c.tuning.debugView=RenderTuning.DebugView.NONE;c.debugViewsEnabled=false;
            for(var setting:org.betterLostItems.salts_anti_aliasing.client.config.NumericTuningSetting.forMode(c.mode,true))
                setting.reset(c.tuning,defaults.tuning);
            switch(c.mode){
                case MSAA -> {c.msaaSampleLevel=defaults.msaaSampleLevel;c.msaaAlphaToCoverage=defaults.msaaAlphaToCoverage;}
                case SSAA -> {c.ssaaScaleLevel=defaults.ssaaScaleLevel;c.tuning.ssaaFilter=defaults.tuning.ssaaFilter;c.tuning.ssaaLinearLight=defaults.tuning.ssaaLinearLight;}
                case SMAA -> c.tuning.smaaQuality=defaults.tuning.smaaQuality;
                case NIS_UPSCALE,FSR1_UPSCALE -> {c.nisUpscaleQualityPreset=defaults.nisUpscaleQualityPreset;c.tuning.spatialAa=defaults.tuning.spatialAa;c.tuning.smaaQuality=defaults.tuning.smaaQuality;}
                case TAA -> {c.tuning.taaHistoryFilter=defaults.tuning.taaHistoryFilter;c.tuning.taaJitterPhases=defaults.tuning.taaJitterPhases;}
                case DLSS_SUPER_RESOLUTION -> {c.dlssQualityPreset=defaults.dlssQualityPreset;c.tuning.dlssModel=defaults.tuning.dlssModel;c.tuning.dlssColorInput=defaults.tuning.dlssColorInput;}
                case FSR2_SUPER_RESOLUTION,FSR3_SUPER_RESOLUTION,FSR3_SUPER_RESOLUTION_FRAME_GENERATION -> c.fsrQualityPreset=defaults.fsrQualityPreset;
                default -> {}
            }
            if(c.mode.usesHistoryBuffers()) {c.tuning.entityMotion=true;c.tuning.motionVertexBudget=defaults.tuning.motionVertexBudget;}
            if(c.mode==AntiAliasingMode.DLSS_SUPER_RESOLUTION || c.mode.usesFsrQualityControl() || c.mode.usesSpatialUpscaleQualityControl()) {
                c.tuning.upscalerHandAa=defaults.tuning.upscalerHandAa;
                c.tuning.upscalerHandAlphaToCoverage=defaults.tuning.upscalerHandAlphaToCoverage;
                c.tuning.mipBiasMode=defaults.tuning.mipBiasMode;c.tuning.mipBias=defaults.tuning.mipBias;c.tuning.mipBiasOffset=defaults.tuning.mipBiasOffset;
            }
            if(c.mode==AntiAliasingMode.NIS_UPSCALE || c.mode==AntiAliasingMode.FSR1_UPSCALE)
                for(var pre:java.util.List.of(AntiAliasingMode.FXAA,AntiAliasingMode.SMAA))
                    for(var setting:org.betterLostItems.salts_anti_aliasing.client.config.NumericTuningSetting.forMode(pre,true))
                        setting.reset(c.tuning,defaults.tuning);
        });
        rebuildPipeline();retryRendering();
    }

    public void resetAllSettings() {
        AntiAliasingMode previous=activeMode();
        configManager.resetControls();rebuildPipeline();
        reconfigureSurfaceForFrameGenerationChange(previous,activeMode());retryRendering();
    }

    /**
     * Reports whether Minecraft is currently running on Vulkan.
     */
    public boolean isVulkanActive() {
        return backend.isAvailable();
    }

    /**
     * Reports whether a non-off AA mode can be applied in this session.
     */
    public boolean canUseAntiAliasing() {
        return isVulkanActive();
    }

    /** Only jitter frames that have an active temporal consumer, including native setup success. */
    public boolean isTemporalRenderingActive() {
        if (!canUseAntiAliasing()) {
            return false;
        }
        return switch (activeMode()) {
            case TAA -> scenePostProcessor.isTemporalResolveAvailable();
            case DLSS_SUPER_RESOLUTION -> VulkanSceneDlssController.instance().overrideMainTarget() != null;
            case FSR2_SUPER_RESOLUTION, FSR3_SUPER_RESOLUTION, FSR3_SUPER_RESOLUTION_FRAME_GENERATION ->
                    VulkanSceneFsrController.instance().overrideMainTarget() != null;
            default -> false;
        };
    }

    /**
     * Reports whether a specific mode can be selected without being resolved to a fallback mode.
     */
    public boolean canSelectMode(AntiAliasingMode mode) {
        AntiAliasingMode requestedMode = AntiAliasingMode.clampImplemented(mode);
        if (requestedMode == AntiAliasingMode.OFF || requestedMode == AntiAliasingMode.DLSS_SUPER_RESOLUTION) {
            return true;
        }

        return canUseAntiAliasing()
                && (isModeSupported(requestedMode) || isNativeModeWaitingForVulkan(requestedMode));
    }

    /** DLSS can be selected for setup before its native renderer is available. */
    public boolean canConfigureMode(AntiAliasingMode mode) {
        return canUseAntiAliasing() && canSelectMode(mode)
                && (mode != AntiAliasingMode.DLSS_SUPER_RESOLUTION || dlssRuntimeStatus().ready());
    }

    /**
     * Describes why a mode cannot be selected in the current runtime.
     */
    public String modeUnavailableReason(AntiAliasingMode mode) {
        AntiAliasingMode requestedMode = AntiAliasingMode.clampImplemented(mode);
        if (canSelectMode(requestedMode)) {
            return "";
        }
        if (requestedMode != AntiAliasingMode.OFF && !canUseAntiAliasing()) {
            return "Anti-aliasing requires Minecraft's Vulkan graphics API.";
        }

        return switch (requestedMode) {
            case DLSS_SUPER_RESOLUTION -> dlssRuntimeStatus().message();
            case FSR2_SUPER_RESOLUTION,
                 FSR3_SUPER_RESOLUTION,
                 FSR3_SUPER_RESOLUTION_FRAME_GENERATION -> fsrRuntimeStatus().message();
            default -> "This mode is not supported by the current graphics backend.";
        };
    }

    /**
     * Advances to the next implemented anti-aliasing mode when Vulkan is active.
     */
    public AntiAliasingMode cycleMode() {
        if (!canUseAntiAliasing()) {
            return activeMode();
        }

        return setMode(nextSupportedMode(activeMode()));
    }

    /**
     * Applies a requested mode. DLSS remains selectable so its setup screen can be reached.
     */
    public AntiAliasingMode setMode(AntiAliasingMode mode) {
        AntiAliasingMode requestedMode = AntiAliasingMode.clampImplemented(mode);
        if (requestedMode != AntiAliasingMode.OFF && requestedMode != AntiAliasingMode.DLSS_SUPER_RESOLUTION && !canUseAntiAliasing()) {
            SaltsAntiAliasing.LOGGER.info(
                    "Ignoring Salt's Anti Aliasing mode {} because Minecraft is not running Vulkan",
                    requestedMode.displayName()
            );
            MetricsDiagnostics.warn(
                    "runtime",
                    "mode_change_rejected",
                    "requestedMode", requestedMode,
                    "activeMode", activeMode(),
                    "reason", "Vulkan backend is not active"
            );
            return activeMode();
        }

        AntiAliasingMode previousMode = activeMode();
        AntiAliasingMode clampedMode = resolveSupportedMode(requestedMode);
        MetricsDiagnostics.info(
                "runtime",
                "mode_change_requested",
                "previousMode", previousMode,
                "requestedMode", requestedMode,
                "resolvedMode", clampedMode,
                "requiredCapabilities", requiredCapabilities(requestedMode),
                "availableCapabilities", backend.capabilities()
        );
        configManager.edit(config -> {config.mode = clampedMode;config.tuning.debugView=RenderTuning.DebugView.NONE;config.debugViewsEnabled=false;});
        edgeDebugAnalyzer.reset(clampedMode);
        rebuildPipeline();
        reconfigureSurfaceForFrameGenerationChange(previousMode, clampedMode);
        return activeMode();
    }

    public float sharpenStrength() {
        return configManager.snapshot().sharpenStrength;
    }

    public float setSharpenStrength(float sharpenStrength) {
        boolean previouslyEnabled = this.sharpenStrength() > 0.0f;
        configManager.edit(config -> config.sharpenStrength = sharpenStrength);
        float sanitizedStrength = this.sharpenStrength();
        if (previouslyEnabled != (sanitizedStrength > 0.0f)) {
            rebuildPipeline();
        }
        return sanitizedStrength;
    }

    public MsaaSampleLevel msaaSampleLevel() {
        return configManager.snapshot().msaaSampleLevel;
    }

    public MsaaSampleLevel setMsaaSampleLevel(MsaaSampleLevel sampleLevel) {
        configManager.edit(config -> config.msaaSampleLevel = sampleLevel);
        return msaaSampleLevel();
    }

    public boolean msaaAlphaToCoverage() {
        return configManager.snapshot().msaaAlphaToCoverage;
    }

    public boolean setMsaaAlphaToCoverage(boolean enabled) {
        configManager.edit(config -> config.msaaAlphaToCoverage = enabled);
        return msaaAlphaToCoverage();
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

    public DlssQualityPreset dlssQualityPreset() {
        return configManager.snapshot().dlssQualityPreset;
    }

    public DlssQualityPreset setDlssQualityPreset(DlssQualityPreset preset) {
        configManager.edit(config -> config.dlssQualityPreset = preset);
        rebuildPipeline();
        return dlssQualityPreset();
    }

    public DlssRuntimeStatus dlssRuntimeStatus() {
        return DlssRuntime.instance().status();
    }

    public FsrQualityPreset fsrQualityPreset() {
        return configManager.snapshot().fsrQualityPreset;
    }

    public FsrQualityPreset setFsrQualityPreset(FsrQualityPreset preset) {
        configManager.edit(config -> config.fsrQualityPreset = preset);
        rebuildPipeline();
        return fsrQualityPreset();
    }

    public FsrRuntimeStatus fsrRuntimeStatus() {
        return FsrRuntime.instance().status();
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
        if (frameGenerationPostEffectsApplied) return;
        rebuildPipelineIfBackendReady();
        if (!canUseAntiAliasing()) {
            return;
        }

        AntiAliasingConfig config = effectiveConfigSnapshot();
        scenePostProcessor.applyFinalEffects(gameRenderer, config);
        VulkanSceneFsrController.instance().captureFrameGenerationHudlessColor();
    }

    private boolean frameGenerationPostEffectsApplied;

    /** Finish the FG world input before native-resolution hand color is drawn over it. */
    public void beforeHandRendering(GameRenderer renderer) {
        if (!effectiveConfigSnapshot().mode.usesFsrFrameGeneration()) return;
        applyScenePostProcessing(renderer);
        frameGenerationPostEffectsApplied = true;
    }

    public void maintainWithoutWorld(GameRenderer renderer) {
        if(Minecraft.getInstance().level!=null)return;
        org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanGpuProfiler.beginFrame();
        var inactive=effectiveConfigSnapshot();inactive.mode=AntiAliasingMode.OFF;
        VulkanSceneMsaaController.instance().beginSceneRendering(renderer,inactive);
        VulkanSceneScaleController.instance().beginSceneRendering(renderer,inactive);
        VulkanSceneDlssController.instance().beginSceneRendering(renderer,inactive);
        VulkanSceneFsrController.instance().beginSceneRendering(renderer,inactive);
        org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanSceneTemporalController.instance().resetForInactiveMode();
        org.betterLostItems.salts_anti_aliasing.client.render.vulkan.EntityMotionGeometry.beginFrame(inactive);
        scenePostProcessor.maintain(inactive);
    }

    public void recordRenderedFrame(long frameTimeNs, int displayedFps) {
        rebuildPipelineIfBackendReady();
        performanceMetricsRecorder.recordFrame(frameTimeNs, displayedFps);
    }

    public void shutdownMetrics() {
        configManager.save();
        MetricsDiagnostics.info(
                "lifecycle",
                "metrics_shutdown",
                "activeMode", activeMode(),
                "pipeline", describePlan()
        );
        performanceMetricsRecorder.close();
    }

    public void flushMetrics() {
        MetricsDiagnostics.infoOnce(
                "lifecycle.metrics-shutdown-requested",
                "lifecycle",
                "metrics_shutdown_requested",
                "activeMode", activeMode(),
                "pipeline", describePlan()
        );
        performanceMetricsRecorder.flushSnapshot();
    }

    /**
     * Releases native renderer integrations after Minecraft has destroyed its GPU surface.
     */
    public void shutdownNativeIntegrations() {
        MetricsDiagnostics.info(
                "lifecycle",
                "native_integrations_shutdown",
                "dlssStatus", DlssRuntime.instance().status(),
                "fsrStatus", FsrRuntime.instance().status()
        );
        DlssRuntime.instance().shutdown();
        FsrRuntime.instance().shutdown();
    }

    public void beginSceneRendering(GameRenderer gameRenderer) {
        frameGenerationPostEffectsApplied = false;
        rebuildPipelineIfBackendReady();
        AntiAliasingConfig config = effectiveConfigSnapshot();
        VulkanSceneMsaaController.instance().beginSceneRendering(gameRenderer, config);
        if (retryRequested) {
            retryRequested=false;
            scenePostProcessor.retry();
            VulkanSceneScaleController.instance().retry();
            VulkanSceneDlssController.instance().retry();
            VulkanSceneFsrController.instance().retry();
            VulkanSceneMsaaController.instance().retry();
            RenderingHealth.clear();
        }
        org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanGpuProfiler.beginFrame();
        scenePostProcessor.maintain(config);
        org.betterLostItems.salts_anti_aliasing.client.render.vulkan.EntityMotionGeometry.beginFrame(config);
        VulkanSceneDlssController.instance().beginSceneRendering(gameRenderer, config);
        VulkanSceneFsrController.instance().beginSceneRendering(gameRenderer, config);
        VulkanSceneScaleController.instance().beginSceneRendering(gameRenderer, config);
    }

    public void endSceneRendering(GameRenderer gameRenderer) {
        org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanTextureDetail.endScene();
        org.betterLostItems.salts_anti_aliasing.client.render.vulkan.EntityMotionGeometry.endWorldCapture();
        rebuildPipelineIfBackendReady();
        AntiAliasingConfig config = effectiveConfigSnapshot();
        VulkanSceneMsaaController.instance().endSceneRendering(gameRenderer, config);
        VulkanSceneDlssController.instance().endSceneRendering(gameRenderer, config);
        VulkanSceneFsrController.instance().endSceneRendering(gameRenderer, config);
        VulkanSceneScaleController.instance().endSceneRendering(gameRenderer, config);
        if (config.mode == AntiAliasingMode.TAA && canUseAntiAliasing()) {
            scenePostProcessor.applyTemporalResolve(gameRenderer, config);
        }
    }

    public void requestPipelineRebuildWhenBackendReady() {
        rebuildPipelineWhenBackendReady = true;
        MetricsDiagnostics.info(
                "pipeline",
                "pipeline_rebuild_requested",
                "backendAvailable", canUseAntiAliasing(),
                "activeMode", activeMode()
        );
        rebuildPipelineIfBackendReady();
    }

    /**
     * Recomputes the backend-neutral pass plan after a mode or quality setting changes.
     */
    public void rebuildPipeline() {
        AntiAliasingConfig effectiveConfig = effectiveConfigSnapshot();
        boolean previousFrameGenerationRequest = FsrRuntime.instance().isFrameGenerationSwapchainRequested();
        DlssRuntime.instance().configure(effectiveConfig);
        FsrRuntime.instance().configure(effectiveConfig);
        boolean frameGenerationSurfaceReconfigurationRequested = FsrRuntime.instance()
                .consumeFrameGenerationSurfaceReconfigurationRequested();
        if ((previousFrameGenerationRequest != effectiveConfig.mode.usesFsrFrameGeneration()
                || frameGenerationSurfaceReconfigurationRequested) && canUseAntiAliasing()) {
            Minecraft.getInstance().invalidateSurfaceConfiguration();
        }
        currentPlan = planner.plan(backend, effectiveConfig);
        passManager.replaceAll(currentPlan.passes());
        backend.declareTargets(currentPlan.targets());
        SaltsAntiAliasing.LOGGER.info(
                "Configured {} backend with passes {} and {} targets",
                backend.type().displayName(),
                passManager.orderedPassIds(),
                currentPlan.targets().size()
        );
        MetricsDiagnostics.info(
                "pipeline",
                "pipeline_configured",
                "backend", backend.type(),
                "backendAvailable", backend.isAvailable(),
                "capabilities", backend.capabilities(),
                "effectiveConfiguration", MetricsDiagnostics.configSnapshot(effectiveConfig),
                "passes", describePasses(currentPlan),
                "targets", describeTargets(currentPlan)
        );
    }

    /** Rendering uses a native scene while a selected DLSS method awaits setup or restart. */
    public AntiAliasingConfig effectiveConfigSnapshot() {
        AntiAliasingConfig config = configManager.snapshot();
        if (config.mode == AntiAliasingMode.DLSS_SUPER_RESOLUTION && !canConfigureMode(config.mode)) {
            config.mode = AntiAliasingMode.OFF;
            config.sharpenStrength = 0.0f;
            config.debugViewsEnabled = false;
            config.tuning.debugView = RenderTuning.DebugView.NONE;
        } else if (!canUseAntiAliasing()) {
            config.mode = AntiAliasingMode.OFF;
        } else {
            config.mode = resolveSupportedMode(config.mode);
        }

        return config;
    }

    private void rebuildPipelineIfBackendReady() {
        if (FsrRuntime.instance().consumeFrameGenerationSwapchainAvailabilityChanged()) {
            ensureActiveModeSupported();
            rebuildPipelineWhenBackendReady = true;
        }
        if (rebuildPipelineWhenBackendReady && canUseAntiAliasing()) {
            rebuildPipelineWhenBackendReady = false;
            rebuildPipeline();
        }
    }

    private AntiAliasingMode nextSupportedMode(AntiAliasingMode mode) {
        AntiAliasingMode nextMode = AntiAliasingMode.clampImplemented(mode);
        do {
            nextMode = nextMode.nextImplemented();
        } while (nextMode != AntiAliasingMode.DLSS_SUPER_RESOLUTION && !isModeSupported(nextMode));

        return nextMode;
    }

    private AntiAliasingMode resolveSupportedMode(AntiAliasingMode mode) {
        if (mode == AntiAliasingMode.DLSS_SUPER_RESOLUTION || isModeSupported(mode) || isNativeModeWaitingForVulkan(mode)) {
            return mode;
        }
        if (mode == AntiAliasingMode.FSR3_SUPER_RESOLUTION_FRAME_GENERATION
                && isModeSupported(AntiAliasingMode.FSR3_SUPER_RESOLUTION)) {
            return AntiAliasingMode.FSR3_SUPER_RESOLUTION;
        }
        return AntiAliasingMode.OFF;
    }

    private boolean isModeSupported(AntiAliasingMode mode) {
        return backend.supportsAll(requiredCapabilities(mode));
    }

    private boolean isNativeModeWaitingForVulkan(AntiAliasingMode mode) {
        return switch (mode) {
            case DLSS_SUPER_RESOLUTION -> DlssRuntime.instance().status() == DlssRuntimeStatus.VULKAN_DEVICE_MISSING;
            case FSR2_SUPER_RESOLUTION, FSR3_SUPER_RESOLUTION, FSR3_SUPER_RESOLUTION_FRAME_GENERATION ->
                    FsrRuntime.instance().status() == FsrRuntimeStatus.VULKAN_DEVICE_MISSING;
            default -> false;
        };
    }

    private static Set<RenderCapability> requiredCapabilities(AntiAliasingMode mode) {
        return switch (mode) {
            case OFF, NIS_SHARPEN -> Set.of();
            case FXAA, SMAA, SMAA_NIS_SHARPEN -> EnumSet.of(RenderCapability.POST_PROCESSING);
            case MSAA -> EnumSet.of(RenderCapability.MULTISAMPLE_AA);
            case SSAA -> EnumSet.of(RenderCapability.INTERNAL_RESOLUTION);
            case NIS_UPSCALE, FSR1_UPSCALE, FSR1_RCAS -> EnumSet.of(
                    RenderCapability.INTERNAL_RESOLUTION,
                    RenderCapability.SPATIAL_UPSCALING
            );
            case DLSS_SUPER_RESOLUTION -> EnumSet.of(
                    RenderCapability.INTERNAL_RESOLUTION,
                    RenderCapability.SPATIAL_UPSCALING,
                    RenderCapability.TEMPORAL_AA,
                    RenderCapability.VENDOR_UPSCALING
            );
            case FSR2_SUPER_RESOLUTION, FSR3_SUPER_RESOLUTION -> EnumSet.of(
                    RenderCapability.INTERNAL_RESOLUTION,
                    RenderCapability.SPATIAL_UPSCALING,
                    RenderCapability.TEMPORAL_AA,
                    RenderCapability.FSR_UPSCALING
            );
            case FSR3_SUPER_RESOLUTION_FRAME_GENERATION -> EnumSet.of(
                    RenderCapability.INTERNAL_RESOLUTION,
                    RenderCapability.SPATIAL_UPSCALING,
                    RenderCapability.TEMPORAL_AA,
                    RenderCapability.FSR_UPSCALING,
                    RenderCapability.FSR_FRAME_GENERATION
            );
            case TAA -> EnumSet.of(RenderCapability.POST_PROCESSING, RenderCapability.TEMPORAL_AA);
        };
    }

    private void ensureActiveModeSupported() {
        AntiAliasingMode previousMode = activeMode();
        AntiAliasingMode supportedMode = resolveSupportedMode(AntiAliasingMode.clampImplemented(activeMode()));
        if (supportedMode != activeMode()) {
            MetricsDiagnostics.warn(
                    "runtime",
                    "active_mode_fallback",
                    "requestedMode", previousMode,
                    "fallbackMode", supportedMode,
                    "availableCapabilities", backend.capabilities(),
                    "requiredCapabilities", requiredCapabilities(previousMode),
                    "dlssStatus", DlssRuntime.instance().status(),
                    "fsrStatus", FsrRuntime.instance().status()
            );
            configManager.edit(config -> config.mode = supportedMode);
            edgeDebugAnalyzer.reset(supportedMode);
            reconfigureSurfaceForFrameGenerationChange(previousMode, supportedMode);
        }
    }

    private static void reconfigureSurfaceForFrameGenerationChange(
            AntiAliasingMode previousMode,
            AntiAliasingMode newMode
    ) {
        if (previousMode.usesFsrFrameGeneration() != newMode.usesFsrFrameGeneration()) {
            MetricsDiagnostics.info(
                    "vulkan.surface",
                    "surface_reconfiguration_requested",
                    "previousMode", previousMode,
                    "newMode", newMode,
                    "reason", "FSR frame-generation ownership changed"
            );
            Minecraft.getInstance().invalidateSurfaceConfiguration();
        }
    }

    private static void recordGpuContext(RenderBackend backend) {
        try {
            GpuDevice device = RenderSystem.tryGetDevice();
            DeviceInfo deviceInfo = device == null ? null : device.getDeviceInfo();
            MetricsDiagnostics.infoOnce(
                    "environment.gpu",
                    "environment",
                    "gpu_context",
                    "backendDescription", RenderSystem.getBackendDescription(),
                    "backendType", backend.type(),
                    "backendAvailable", backend.isAvailable(),
                    "gpuDebuggingEnabled", device != null && device.isDebuggingEnabled(),
                    "deviceName", deviceInfo == null ? "unavailable" : deviceInfo.name(),
                    "vendorName", deviceInfo == null ? "unavailable" : deviceInfo.vendorName(),
                    "driverInfo", deviceInfo == null ? "unavailable" : deviceInfo.driverInfo(),
                    "deviceType", deviceInfo == null ? "unavailable" : deviceInfo.type(),
                    "deviceBackend", deviceInfo == null ? "unavailable" : deviceInfo.backendName(),
                    "timestampPeriod", deviceInfo == null ? 0.0f : deviceInfo.timestampPeriod(),
                    "deviceLimits", deviceInfo == null ? "unavailable" : deviceInfo.limits(),
                    "deviceFeatures", deviceInfo == null ? "unavailable" : deviceInfo.features(),
                    "deviceExtensions", deviceInfo == null ? List.of() : deviceInfo.underlyingExtensions(),
                    "hintsAndWorkarounds", deviceInfo == null ? "unavailable" : deviceInfo.hintsAndWorkarounds()
            );
        } catch (RuntimeException exception) {
            MetricsDiagnostics.error("environment", "gpu_context_failed", exception);
        }
    }

    private static List<Map<String, Object>> describePasses(PipelinePlan plan) {
        List<Map<String, Object>> passes = new ArrayList<>();
        plan.passes().forEach(pass -> {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("id", pass.id());
            details.put("requiredCapabilities", pass.requiredCapabilities());
            details.put("readTargets", pass.readTargets());
            details.put("writeTargets", pass.writeTargets());
            passes.add(details);
        });
        return passes;
    }

    private static List<Map<String, Object>> describeTargets(PipelinePlan plan) {
        List<Map<String, Object>> targets = new ArrayList<>();
        plan.targets().forEach(target -> {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("id", target.id());
            details.put("type", target.type());
            details.put("format", target.format());
            details.put("sizing", target.sizing());
            details.put("scale", target.scale());
            details.put("persistentAcrossFrames", target.persistentAcrossFrames());
            targets.add(details);
        });
        return targets;
    }
}
