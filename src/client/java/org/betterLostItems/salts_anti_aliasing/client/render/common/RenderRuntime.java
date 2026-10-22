package org.betterLostItems.salts_anti_aliasing.client.render.common;

import com.mojang.renderpearl.api.device.DeviceInfo;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
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
        if (requestedMode == AntiAliasingMode.OFF) {
            return true;
        }

        return canUseAntiAliasing()
                && (isModeSupported(requestedMode) || isNativeModeWaitingForVulkan(requestedMode));
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
     * Applies a requested mode. Non-off modes are blocked when Minecraft is not running Vulkan.
     */
    public AntiAliasingMode setMode(AntiAliasingMode mode) {
        AntiAliasingMode requestedMode = AntiAliasingMode.clampImplemented(mode);
        if (requestedMode != AntiAliasingMode.OFF && !canUseAntiAliasing()) {
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
        configManager.edit(config -> config.mode = clampedMode);
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
        rebuildPipelineIfBackendReady();
        if (!canUseAntiAliasing()) {
            return;
        }

        AntiAliasingConfig config = effectiveConfigSnapshot();
        scenePostProcessor.applyFinalEffects(gameRenderer, config);
        VulkanSceneFsrController.instance().captureFrameGenerationHudlessColor();
    }

    public void recordRenderedFrame(long frameTimeNs, int displayedFps) {
        rebuildPipelineIfBackendReady();
        performanceMetricsRecorder.recordFrame(frameTimeNs, displayedFps);
    }

    public void shutdownMetrics() {
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
        rebuildPipelineIfBackendReady();
        AntiAliasingConfig config = effectiveConfigSnapshot();
        VulkanSceneMsaaController.instance().beginSceneRendering(gameRenderer, config);
        if (config.mode == AntiAliasingMode.OFF) {
            return;
        }
        VulkanSceneDlssController.instance().beginSceneRendering(gameRenderer, config);
        VulkanSceneFsrController.instance().beginSceneRendering(gameRenderer, config);
        VulkanSceneScaleController.instance().beginSceneRendering(gameRenderer, config);
    }

    public void endSceneRendering(GameRenderer gameRenderer) {
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

    private AntiAliasingConfig effectiveConfigSnapshot() {
        AntiAliasingConfig config = configManager.snapshot();
        if (!canUseAntiAliasing()) {
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
        } while (!isModeSupported(nextMode));

        return nextMode;
    }

    private AntiAliasingMode resolveSupportedMode(AntiAliasingMode mode) {
        if (isModeSupported(mode) || isNativeModeWaitingForVulkan(mode)) {
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
