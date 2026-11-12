package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.client.config.*;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanNativeDeviceRegistry;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.*;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.*;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.xess.XessRuntime;
import java.util.function.Consumer;

/** Shared Fabric configuration and UI contract; game adapters consume immutable snapshots. */
public final class RenderRuntime {
    public interface Adapter {
        void changed(AntiAliasingConfig config);
        void retry();
        String status();
        String allocations();
        String motionSummary();
        String motionDetails();
        default boolean frameGenerationConnected() { return false; }
        default org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugStats edgeStats() {
            return org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugStats.unavailable(AntiAliasingMode.OFF);
        }
    }
    private final ConfigManager configManager;
    private final Adapter adapter;
    private final org.betterLostItems.salts_anti_aliasing.client.metrics.PerformanceMetricsRecorder metrics;

    public RenderRuntime(Adapter adapter) {
        this.adapter = adapter;
        configManager = ConfigManager.createDefault();
        configManager.load();
        metrics = new org.betterLostItems.salts_anti_aliasing.client.metrics.PerformanceMetricsRecorder(
                configManager::recordMetricsEnabled, configManager::mode, configManager::snapshot);
        org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics.recordEnvironment();
        rebuildPipeline();
    }
    public void recordRenderedFrame(long nanos, int fps) { metrics.recordFrame(nanos, fps); }
    public void flushMetrics() { metrics.flushSnapshot(); }
    public void shutdownMetrics() { metrics.close(); }
    public AntiAliasingConfig configSnapshot() { return configManager.snapshot(); }
    public AntiAliasingMode activeMode() { return configManager.mode(); }
    public void editSettings(Consumer<AntiAliasingConfig> editor) { configManager.edit(editor); rebuildPipeline(); }
    public void editSettingsLive(Consumer<AntiAliasingConfig> editor) { configManager.editLive(editor); rebuildPipeline(); }
    public void editTuning(Consumer<RenderTuning> editor) { editSettingsLive(c -> editor.accept(c.tuning)); }
    public void saveSettings() { configManager.save(); }
    public void rebuildPipeline() {
        var config = configSnapshot();
        DlssRuntime.instance().configure(config);
        FsrRuntime.instance().configure(config);
        adapter.changed(config);
    }
    public void retryRendering() { adapter.retry(); }
    public boolean isVulkanActive() { return VulkanNativeDeviceRegistry.latest() != null; }
    public boolean canUseAntiAliasing() { return isVulkanActive(); }
    public boolean canSelectMode(AntiAliasingMode mode) {
        if (mode == AntiAliasingMode.OFF || mode == AntiAliasingMode.DLSS_SUPER_RESOLUTION || mode.usesXessQualityControl()) return true;
        if (!canUseAntiAliasing()) return false;
        if (mode.usesFsrFrameGeneration() && (!adapter.frameGenerationConnected() || !FsrRuntime.instance().status().frameGenerationReady())) return false;
        return !mode.usesFsrQualityControl() || FsrRuntime.instance().status().upscalingReady();
    }
    public boolean canConfigureMode(AntiAliasingMode mode) {
        return canUseAntiAliasing() && canSelectMode(mode)
                && (mode != AntiAliasingMode.DLSS_SUPER_RESOLUTION || DlssRuntime.instance().status().ready())
                && (!mode.usesXessQualityControl() || XessRuntime.instance().isReady());
    }
    public String modeUnavailableReason(AntiAliasingMode mode) {
        if (!canUseAntiAliasing()) return "VulkanMod's graphics device is not ready.";
        if (mode.usesFsrFrameGeneration() && !adapter.frameGenerationConnected()) return "Frame-generation presentation is not connected in this development target.";
        if (mode.usesFsrQualityControl()) return FsrRuntime.instance().status().message();
        if (mode.usesXessQualityControl()) return XessRuntime.instance().message();
        if (mode == AntiAliasingMode.DLSS_SUPER_RESOLUTION) return DlssRuntime.instance().status().message();
        return "";
    }
    public AntiAliasingMode setMode(AntiAliasingMode mode) {
        var selected = AntiAliasingMode.clampImplemented(mode);
        if (canSelectMode(selected)) editSettings(c -> { c.mode = selected; c.tuning.debugView = RenderTuning.DebugView.NONE; c.debugViewsEnabled = false; });
        return activeMode();
    }
    public AntiAliasingMode cycleMode() {
        var values = AntiAliasingMode.selectableModes();
        int start = values.indexOf(activeMode().selectionMode());
        for (int i = 1; i <= values.size(); i++) {
            var next = values.get(Math.floorMod(start + i, values.size()));
            if (canSelectMode(next) && canConfigureMode(next)) return setMode(next);
        }
        return activeMode();
    }
    public String backendName() { return "VulkanMod"; }
    public String describePlan() { return backendName() + " / " + activeMode().displayName(); }
    public String renderStatusText() { return adapter.status(); }
    public boolean debugViewsEnabled() { return configSnapshot().debugViewsEnabled; }
    public org.betterLostItems.salts_anti_aliasing.client.debug.EdgeDebugStats edgeDebugStats() { return adapter.edgeStats(); }
    public String allocationStatusText() { return adapter.allocations(); }
    public String dlssMotionCoverageText() { return adapter.motionSummary(); }
    public String dlssMotionCoverageDetails() { return adapter.motionDetails(); }
    public String dlssMemoryStatusText() {
        long bytes = DlssRuntime.instance().estimatedVramBytes();
        return bytes < 0 ? "DLSS SDK VRAM estimate: unavailable" : String.format(java.util.Locale.ROOT, "DLSS SDK VRAM estimate: %.1f MiB", bytes / 1048576.0);
    }
    public String frameGenerationStatusText() {
        var fsr = FsrRuntime.instance();
        return "Game frames: " + fsr.renderedPresentCount() + " / display presents: " + fsr.frameGenerationPresentCount();
    }
    public void resetAllSettings() { configManager.resetControls(); rebuildPipeline(); retryRendering(); }
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
                case SGSR1_UPSCALE -> {
                    c.sgsr1QualityPreset=defaults.sgsr1QualityPreset;
                    c.tuning.sgsr1EdgeDirection=defaults.tuning.sgsr1EdgeDirection;
                    c.tuning.spatialAa=defaults.tuning.spatialAa;
                    c.tuning.smaaQuality=defaults.tuning.smaaQuality;
                }
                case SGSR2_SUPER_RESOLUTION -> c.sgsr2QualityPreset=defaults.sgsr2QualityPreset;
                case XESS_SUPER_RESOLUTION -> c.xessQualityPreset=defaults.xessQualityPreset;
                case DLSS_SUPER_RESOLUTION -> {c.dlssQualityPreset=defaults.dlssQualityPreset;c.tuning.dlssModel=defaults.tuning.dlssModel;c.tuning.dlssColorInput=defaults.tuning.dlssColorInput;}
                case FSR2_SUPER_RESOLUTION,FSR3_SUPER_RESOLUTION,FSR3_SUPER_RESOLUTION_FRAME_GENERATION -> c.fsrQualityPreset=defaults.fsrQualityPreset;
                default -> {}
            }
            if(c.mode.usesHistoryBuffers()) {c.tuning.entityMotion=true;c.tuning.motionVertexBudget=defaults.tuning.motionVertexBudget;}
            if(c.mode==AntiAliasingMode.DLSS_SUPER_RESOLUTION || c.mode.usesXessQualityControl() || c.mode.usesSgsr2QualityControl() || c.mode.usesFsrQualityControl() || c.mode.usesSpatialUpscaleQualityControl()) {
                c.tuning.upscalerHandAa=defaults.tuning.upscalerHandAa;
                c.tuning.upscalerHandAlphaToCoverage=defaults.tuning.upscalerHandAlphaToCoverage;
                c.tuning.mipBiasMode=defaults.tuning.mipBiasMode;c.tuning.mipBias=defaults.tuning.mipBias;c.tuning.mipBiasOffset=defaults.tuning.mipBiasOffset;
            }
            if(c.mode==AntiAliasingMode.NIS_UPSCALE || c.mode==AntiAliasingMode.FSR1_UPSCALE || c.mode.usesSgsr1QualityControl())
                for(var pre:java.util.List.of(AntiAliasingMode.FXAA,AntiAliasingMode.SMAA))
                    for(var setting:org.betterLostItems.salts_anti_aliasing.client.config.NumericTuningSetting.forMode(pre,true))
                        setting.reset(c.tuning,defaults.tuning);
        });
        rebuildPipeline();retryRendering();
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

    public SgsrQualityPreset sgsrQualityPreset() {
        var config=configManager.snapshot(); return config.mode.usesSgsr2QualityControl() ? config.sgsr2QualityPreset : config.sgsr1QualityPreset;
    }
    public SgsrQualityPreset setSgsrQualityPreset(SgsrQualityPreset quality) {
        configManager.edit(config -> {if(config.mode.usesSgsr2QualityControl()) config.sgsr2QualityPreset=quality;else config.sgsr1QualityPreset=quality;});
        rebuildPipeline(); return sgsrQualityPreset();
    }

    public XessQualityPreset xessQualityPreset() { return configManager.snapshot().xessQualityPreset; }
    public XessQualityPreset setXessQualityPreset(XessQualityPreset preset) {
        configManager.edit(config -> config.xessQualityPreset = preset);
        rebuildPipeline(); return xessQualityPreset();
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

}
