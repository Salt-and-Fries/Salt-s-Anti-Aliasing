package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.DlssQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Thin Java owner for the optional native DLSS bridge. This class deliberately degrades to a
 * disabled status when files are absent so normal mod startup never depends on NVIDIA binaries.
 */
final class DlssNativeBridge {
    private boolean bridgeLoaded;
    private boolean interposerLoaded;
    private boolean initialized;
    private boolean preInitialized;
    private String[] requirements = new String[0];
    private DlssQualityPreset settingsPreset;
    private int settingsWidth;
    private int settingsHeight;
    private DlssOptimalSettings cachedSettings;
    private String lastBridgePath = "";
    private String lastInterposerPath = "";

    DlssRuntimeStatus configure(DlssNativeConfiguration configuration, DlssVulkanDeviceInfo deviceInfo) {
        DlssRuntimeStatus validationStatus = configuration.validate();
        if (!validationStatus.ready()) {
            return validationStatus;
        }

        if (!loadBridge(configuration.bridgePath(), configuration.pluginPath())) {
            return DlssRuntimeStatus.BRIDGE_LOAD_FAILED;
        }

        if (!preInitialized) {
            int result = preInitializeNative(configuration.pluginPath(), configuration.logPath(), configuration.applicationId());
            if (result != 0) {
                SaltsAntiAliasing.LOGGER.warn("DLSS Streamline preinitialization failed with result {}", result);
                return DlssRuntimeStatus.INITIALIZATION_FAILED;
            }
            preInitialized = true;
            requirements = requirementsNative();
            MetricsDiagnostics.info("dlss.native", "requirements_queried", "requirements", java.util.List.of(requirements));
        }

        if (deviceInfo == null || !deviceInfo.complete()) {
            return DlssRuntimeStatus.VULKAN_DEVICE_MISSING;
        }

        if (!initialized) {
            int result = initializeDeviceNative(
                    deviceInfo.instance(),
                    deviceInfo.physicalDevice(),
                    deviceInfo.device(),
                    deviceInfo.graphicsQueue(),
                    deviceInfo.graphicsQueueFamily()
            );
            if (result != 0) {
                SaltsAntiAliasing.LOGGER.warn("DLSS native initialization failed with result {}", result);
                MetricsDiagnostics.warn(
                        "dlss.native",
                        "initialization_failed",
                        "result", result,
                        "configuration", configuration,
                        "deviceInfo", deviceInfo
                );
                return DlssRuntimeStatus.INITIALIZATION_FAILED;
            }
            initialized = true;
            MetricsDiagnostics.info(
                    "dlss.native",
                    "initialized",
                    "configuration", configuration,
                    "deviceInfo", deviceInfo
            );
        }

        boolean supported = isSupportedNative();
        MetricsDiagnostics.infoOnce(
                "dlss.native.supported",
                "dlss.native",
                "support_queried",
                "supported", supported,
                "initialized", initialized
        );
        return supported ? DlssRuntimeStatus.READY : DlssRuntimeStatus.UNSUPPORTED;
    }

    DlssOptimalSettings queryOptimalSettings(DlssQualityPreset preset, int outputWidth, int outputHeight) {
        if (!initialized || outputWidth <= 0 || outputHeight <= 0) {
            return DlssOptimalSettings.fallback(outputWidth, outputHeight);
        }
        if (cachedSettings != null && settingsPreset == preset && settingsWidth == outputWidth && settingsHeight == outputHeight) {
            return cachedSettings;
        }

        int[] values = new int[6];
        float[] sharpness = new float[1];
        int result = queryOptimalSettingsNative(preset.ordinal(), outputWidth, outputHeight, values, sharpness);
        if (result != 0) {
            MetricsDiagnostics.warnOnce(
                    "dlss.native.optimal-settings." + preset + "." + outputWidth + "x" + outputHeight + "." + result,
                    "dlss.native",
                    "optimal_settings_query_failed",
                    "result", result,
                    "preset", preset,
                    "outputWidth", outputWidth,
                    "outputHeight", outputHeight,
                    "fallbackUsed", true
            );
            return DlssOptimalSettings.fallback(outputWidth, outputHeight);
        }

        cachedSettings = new DlssOptimalSettings(
                Math.max(1, values[0]),
                Math.max(1, values[1]),
                Math.max(1, values[2]),
                Math.max(1, values[3]),
                Math.max(1, values[4]),
                Math.max(1, values[5]),
                sharpness[0]
        );
        settingsPreset = preset;
        settingsWidth = outputWidth;
        settingsHeight = outputHeight;
        return cachedSettings;
    }

    int evaluate(DlssEvaluateParameters parameters) {
        if (!initialized) {
            return -1;
        }

        return evaluateNativeV2(
                parameters.commandBuffer(),
                parameters.inputColorImage(),
                parameters.inputColorView(),
                parameters.outputColorImage(),
                parameters.outputColorView(),
                parameters.depthImage(),
                parameters.depthView(),
                parameters.motionVectorImage(),
                parameters.motionVectorView(),
                parameters.renderWidth(),
                parameters.renderHeight(),
                parameters.outputWidth(),
                parameters.outputHeight(),
                parameters.jitterX(),
                parameters.jitterY(),
                parameters.resetHistory(),
                parameters.frameIndex(),
                parameters.motionVectorScaleX(),
                parameters.motionVectorScaleY(),
                parameters.currentViewProjection(),
                parameters.previousViewProjection(),
                parameters.projection(),
                parameters.cameraParameters(),
                parameters.resourceFormats(),
                parameters.resourceUsages(),
                parameters.colorRejectionImage(),
                parameters.colorRejectionView()
        );
    }

    void shutdown() {
        if (bridgeLoaded && preInitialized) {
            shutdownNative();
            initialized = false;
            preInitialized = false;
            requirements = new String[0];
            cachedSettings = null;
        }
    }

    private boolean loadBridge(String bridgePath, String pluginPath) {
        if (bridgeLoaded && bridgePath.equals(lastBridgePath)) {
            return true;
        }

        if (!loadStreamlineInterposer(pluginPath)) {
            return false;
        }

        try {
            System.load(bridgePath);
            if (abiVersionNative() != 2) {
                throw new UnsatisfiedLinkError("DLSS bridge ABI 2 required; rebuild the native bridge for Fabric 26.3");
            }
            bridgeLoaded = true;
            lastBridgePath = bridgePath;
            MetricsDiagnostics.info(
                    "dlss.native",
                    "jni_bridge_loaded",
                    "bridgePath", bridgePath,
                    "pluginPath", pluginPath
            );
            return true;
        } catch (UnsatisfiedLinkError | SecurityException exception) {
            SaltsAntiAliasing.LOGGER.warn("Unable to load DLSS JNI bridge from {}", bridgePath, exception);
            MetricsDiagnostics.error(
                    "dlss.native",
                    "jni_bridge_load_failed",
                    exception,
                    "bridgePath", bridgePath,
                    "pluginPath", pluginPath
            );
            bridgeLoaded = false;
            initialized = false;
            return false;
        }
    }

    private boolean loadStreamlineInterposer(String pluginPath) {
        if (pluginPath == null || pluginPath.isBlank()) {
            return true;
        }

        Path interposer = Path.of(pluginPath, "sl.interposer.dll").toAbsolutePath();
        String interposerPath = interposer.toString();
        if (interposerLoaded && interposerPath.equals(lastInterposerPath)) {
            return true;
        }
        if (!Files.isRegularFile(interposer)) {
            MetricsDiagnostics.warnOnce(
                    "dlss.native.interposer-missing." + interposerPath,
                    "dlss.native",
                    "streamline_interposer_missing",
                    "interposerPath", interposerPath,
                    "continuingWithoutExplicitPreload", true
            );
            return true;
        }

        try {
            System.load(interposerPath);
            interposerLoaded = true;
            lastInterposerPath = interposerPath;
            MetricsDiagnostics.info(
                    "dlss.native",
                    "streamline_interposer_loaded",
                    "interposerPath", interposerPath
            );
            return true;
        } catch (UnsatisfiedLinkError | SecurityException exception) {
            SaltsAntiAliasing.LOGGER.warn("Unable to load NVIDIA Streamline interposer from {}", interposerPath, exception);
            MetricsDiagnostics.error(
                    "dlss.native",
                    "streamline_interposer_load_failed",
                    exception,
                    "interposerPath", interposerPath
            );
            interposerLoaded = false;
            return false;
        }
    }

    String[] requirements() {
        return requirements.clone();
    }

    void present(long queue, long presentInfo, boolean after) {
        if (initialized) {
            int result = presentNative(queue, presentInfo, after);
            if (result != 0) MetricsDiagnostics.warnOnce("dlss.present." + result,
                    "dlss.native", "present_bookkeeping_failed", "result", result);
        }
    }

    private static native int abiVersionNative();
    private static native int presentNative(long queue, long presentInfo, boolean after);

    private static native int preInitializeNative(
            String pluginPath,
            String logPath,
            int applicationId
    );

    private static native String[] requirementsNative();

    private static native int initializeDeviceNative(
            long vkInstance,
            long vkPhysicalDevice,
            long vkDevice,
            long vkGraphicsQueue,
            int graphicsQueueFamily
    );

    private static native boolean isSupportedNative();

    private static native int queryOptimalSettingsNative(
            int qualityPreset,
            int outputWidth,
            int outputHeight,
            int[] outSettings,
            float[] outSharpness
    );

    private static native int evaluateNativeV2(
            long commandBuffer,
            long inputColorImage,
            long inputColorView,
            long outputColorImage,
            long outputColorView,
            long depthImage,
            long depthView,
            long motionVectorImage,
            long motionVectorView,
            int renderWidth,
            int renderHeight,
            int outputWidth,
            int outputHeight,
            float jitterX,
            float jitterY,
            boolean resetHistory,
            long frameIndex,
            float motionVectorScaleX,
            float motionVectorScaleY,
            float[] currentViewProjection,
            float[] previousViewProjection,
            float[] projection,
            float[] cameraParameters,
            int[] resourceFormats,
            int[] resourceUsages,
            long colorRejectionImage,
            long colorRejectionView
    );

    private static native void shutdownNative();
}
