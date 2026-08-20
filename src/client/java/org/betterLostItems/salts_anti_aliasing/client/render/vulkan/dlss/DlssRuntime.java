package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.DlssQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;

/**
 * Process-wide DLSS state machine. All callers can ask for status; only configured Vulkan sessions
 * reach the native bridge.
 */
public final class DlssRuntime {
    private static final DlssRuntime INSTANCE = new DlssRuntime();

    private final DlssNativeBridge bridge = new DlssNativeBridge();
    private DlssNativeConfiguration configuration = DlssNativeConfiguration.fromEnvironment();
    private DlssRuntimeStatus status = configuration.validate();

    private DlssRuntime() {
    }

    public static DlssRuntime instance() {
        return INSTANCE;
    }

    public synchronized void configure(AntiAliasingConfig config) {
        DlssNativeConfiguration previousConfiguration = configuration;
        configuration = DlssNativeConfiguration.fromConfig(config);
        MetricsDiagnostics.info(
                "dlss.runtime",
                "configuration_applied",
                "configurationChanged", !configuration.equals(previousConfiguration),
                "bridgePath", configuration.bridgePath(),
                "pluginPath", configuration.pluginPath(),
                "logPath", configuration.logPath(),
                "applicationId", configuration.applicationId(),
                "validationStatus", configuration.validate()
        );
        refresh();
    }

    public synchronized void preInitializeFromEnvironment() {
        MetricsDiagnostics.info(
                "dlss.runtime",
                "environment_preinitialization_requested",
                "hasAnySetting", configuration.hasAnySetting(),
                "validationStatus", configuration.validate()
        );
        if (configuration.hasAnySetting()) {
            refresh();
        }
    }

    public synchronized void onVulkanDeviceReady(DlssVulkanDeviceInfo deviceInfo) {
        MetricsDiagnostics.info(
                "dlss.runtime",
                "vulkan_device_ready",
                "deviceInfo", deviceInfo,
                "deviceInfoComplete", deviceInfo != null && deviceInfo.complete()
        );
        refresh(deviceInfo);
    }

    public synchronized DlssRuntimeStatus status() {
        return status;
    }

    public synchronized boolean isReady() {
        return status.ready();
    }

    public synchronized DlssOptimalSettings queryOptimalSettings(
            DlssQualityPreset preset,
            int outputWidth,
            int outputHeight
    ) {
        DlssOptimalSettings settings = bridge.queryOptimalSettings(preset, outputWidth, outputHeight);
        MetricsDiagnostics.infoOnce(
                "dlss.optimal-settings." + preset + "." + outputWidth + "x" + outputHeight,
                "dlss.runtime",
                "optimal_settings_queried",
                "preset", preset,
                "outputWidth", outputWidth,
                "outputHeight", outputHeight,
                "settings", settings,
                "runtimeStatus", status
        );
        return settings;
    }

    public synchronized int evaluate(DlssEvaluateParameters parameters) {
        int result = bridge.evaluate(parameters);
        if (result != 0) {
            MetricsDiagnostics.warnOnce(
                    "dlss.evaluate." + result + "." + parameters.renderWidth() + "x" + parameters.renderHeight()
                            + "." + parameters.outputWidth() + "x" + parameters.outputHeight(),
                    "dlss.evaluate",
                    "evaluation_failed",
                    "result", result,
                    "parameters", parameters,
                    "runtimeStatus", status
            );
        }
        return result;
    }

    public synchronized void shutdown() {
        MetricsDiagnostics.info(
                "dlss.runtime",
                "shutdown",
                "statusBeforeShutdown", status,
                "configuration", configuration
        );
        bridge.shutdown();
    }

    private void refresh() {
        refresh(DlssVulkanDeviceRegistry.latest());
    }

    private void refresh(DlssVulkanDeviceInfo deviceInfo) {
        DlssRuntimeStatus previousStatus = status;
        status = bridge.configure(configuration, deviceInfo);
        if (status != previousStatus) {
            SaltsAntiAliasing.LOGGER.info("DLSS runtime status: {}", status.message());
            MetricsDiagnostics.info(
                    "dlss.runtime",
                    "status_changed",
                    "previousStatus", previousStatus,
                    "currentStatus", status,
                    "message", status.message(),
                    "deviceInfo", deviceInfo,
                    "configuration", configuration
            );
        }
    }
}
