package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.DlssQualityPreset;

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
        configuration = DlssNativeConfiguration.fromConfig(config);
        refresh();
    }

    public synchronized void preInitializeFromEnvironment() {
        if (configuration.hasAnySetting()) {
            refresh();
        }
    }

    public synchronized void onVulkanDeviceReady(DlssVulkanDeviceInfo deviceInfo) {
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
        return bridge.queryOptimalSettings(preset, outputWidth, outputHeight);
    }

    public synchronized int evaluate(DlssEvaluateParameters parameters) {
        return bridge.evaluate(parameters);
    }

    public synchronized void shutdown() {
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
        }
    }
}
