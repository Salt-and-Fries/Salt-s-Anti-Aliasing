package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.DlssQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.config.DlssModelPreset;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;

/**
 * Process-wide DLSS state machine. All callers can ask for status; only configured Vulkan sessions
 * reach the native bridge.
 */
public final class DlssRuntime {
    private static final DlssRuntime INSTANCE = new DlssRuntime();

    private final DlssNativeBridge bridge = new DlssNativeBridge();
    private DlssNativeConfiguration configuration = DlssNativeConfiguration.fromStartup();
    private DlssRuntimeStatus status = configuration.validate();
    private boolean requirementsSatisfied = true;
    private boolean deviceCreated;
    private boolean restartRequired;
    private long sdkEstimatedVramBytes = -1;
    private long nextMemoryQuery;

    private DlssRuntime() {}

    public static DlssRuntime instance() {
        return INSTANCE;
    }

    public synchronized void configure(AntiAliasingConfig config) {
        DlssNativeConfiguration previousConfiguration = configuration;
        configuration = DlssNativeConfiguration.fromConfig(config);
        if (restartRequired
                || (deviceCreated
                        && !configuration.equals(previousConfiguration)
                        && configuration.validate().ready())) {
            requireRestart();
            return;
        }
        MetricsDiagnostics.info(
                "dlss.runtime",
                "configuration_applied",
                "configurationChanged",
                !configuration.equals(previousConfiguration),
                "bridgePath",
                configuration.bridgePath(),
                "pluginPath",
                configuration.pluginPath(),
                "logPath",
                configuration.logPath(),
                "applicationId",
                configuration.applicationId(),
                "validationStatus",
                configuration.validate());
        refresh();
    }

    public synchronized void preInitializeFromEnvironment() {
        MetricsDiagnostics.info(
                "dlss.runtime",
                "environment_preinitialization_requested",
                "hasAnySetting",
                configuration.hasAnySetting(),
                "validationStatus",
                configuration.validate());
        if (configuration.hasAnySetting()) {
            refresh();
        }
    }

    public synchronized void onVulkanDeviceReady(DlssVulkanDeviceInfo deviceInfo) {
        deviceCreated = true;
        MetricsDiagnostics.info(
                "dlss.runtime",
                "vulkan_device_ready",
                "deviceInfo",
                deviceInfo,
                "deviceInfoComplete",
                deviceInfo != null && deviceInfo.complete());
        refresh(deviceInfo);
    }

    public synchronized DlssRuntimeStatus status() {
        return status;
    }

    public synchronized boolean hasConfiguredRuntime() {
        return !configuration.pluginPath().isBlank();
    }

    /** Imported runtime files take effect in a fresh process, before graphics initialization. */
    public synchronized void requireRestart() {
        restartRequired = true;
        status = DlssRuntimeStatus.RESTART_REQUIRED;
    }

    public synchronized boolean isReady() {
        return status.ready();
    }

    public synchronized String[] vulkanRequirements() {
        return bridge.requirements();
    }

    public synchronized void present(long queue, long presentInfo, boolean after) {
        bridge.present(queue, presentInfo, after);
    }

    public synchronized void rejectVulkanRequirements(String reason) {
        requirementsSatisfied = false;
        status = DlssRuntimeStatus.UNSUPPORTED;
        SaltsAntiAliasing.LOGGER.warn("DLSS Vulkan requirements are unavailable: {}", reason);
        MetricsDiagnostics.warn(
                "dlss.runtime", "vulkan_requirements_unavailable", "reason", reason);
    }

    public synchronized DlssOptimalSettings queryOptimalSettings(
            DlssQualityPreset preset, DlssModelPreset model, boolean hdr, int outputWidth, int outputHeight) {
        DlssOptimalSettings settings =
                bridge.queryOptimalSettings(preset, model, hdr, outputWidth, outputHeight);
        MetricsDiagnostics.infoOnce(
                "dlss.optimal-settings." + preset + "." + model + ".hdr-" + hdr + "." + outputWidth + "x" + outputHeight,
                "dlss.runtime",
                "optimal_settings_queried",
                "preset",
                preset,
                "requestedModel",
                model,
                "linearHdrInput", hdr,
                "outputWidth",
                outputWidth,
                "outputHeight",
                outputHeight,
                "settings",
                settings,
                "runtimeStatus",
                status);
        return settings;
    }

    public synchronized int evaluate(DlssEvaluateParameters parameters) {
        int result = bridge.evaluate(parameters);
        long now = System.nanoTime();
        if (result == 0 && now >= nextMemoryQuery) {
            sdkEstimatedVramBytes = bridge.estimatedVramBytes();
            nextMemoryQuery = now + 1_000_000_000L;
        }
        if (result != 0) {
            MetricsDiagnostics.warnOnce(
                    "dlss.evaluate."
                            + result
                            + "."
                            + parameters.renderWidth()
                            + "x"
                            + parameters.renderHeight()
                            + "."
                            + parameters.outputWidth()
                            + "x"
                            + parameters.outputHeight(),
                    "dlss.evaluate",
                    "evaluation_failed",
                    "result",
                    result,
                    "parameters",
                    parameters,
                    "runtimeStatus",
                    status);
        }
        return result;
    }

    public synchronized void releaseRenderResources() {
        sdkEstimatedVramBytes = -1;
        nextMemoryQuery = 0;
        bridge.releaseRenderResources();
        MetricsDiagnostics.info("lifecycle", "native_feature_retired", "feature", "DLSS");
    }

    /** The SDK's estimate, distinct from directly owned textures and driver-wide VRAM. */
    public synchronized long estimatedVramBytes() { return sdkEstimatedVramBytes; }

    public synchronized void shutdown() {
        MetricsDiagnostics.info(
                "dlss.runtime",
                "shutdown",
                "statusBeforeShutdown",
                status,
                "configuration",
                configuration);
        bridge.shutdown();
    }

    private void refresh() {
        refresh(DlssVulkanDeviceRegistry.latest());
    }

    private void refresh(DlssVulkanDeviceInfo deviceInfo) {
        if (restartRequired) {
            status = DlssRuntimeStatus.RESTART_REQUIRED;
            return;
        }
        DlssRuntimeStatus previousStatus = status;
        status =
                requirementsSatisfied
                        ? bridge.configure(configuration, deviceInfo)
                        : DlssRuntimeStatus.UNSUPPORTED;
        if (status != previousStatus) {
            SaltsAntiAliasing.LOGGER.info("DLSS runtime status: {}", status.message());
            MetricsDiagnostics.info(
                    "dlss.runtime",
                    "status_changed",
                    "previousStatus",
                    previousStatus,
                    "currentStatus",
                    status,
                    "message",
                    status.message(),
                    "deviceInfo",
                    deviceInfo,
                    "configuration",
                    configuration);
        }
    }
}
