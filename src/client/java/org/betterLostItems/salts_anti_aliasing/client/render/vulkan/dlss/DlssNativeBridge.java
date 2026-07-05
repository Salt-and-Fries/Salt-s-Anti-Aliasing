package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.DlssQualityPreset;

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

        if (deviceInfo == null || !deviceInfo.complete()) {
            return DlssRuntimeStatus.VULKAN_DEVICE_MISSING;
        }

        if (!initialized) {
            int result = initializeNative(
                    configuration.pluginPath(),
                    configuration.logPath(),
                    configuration.applicationId(),
                    deviceInfo.instance(),
                    deviceInfo.physicalDevice(),
                    deviceInfo.device(),
                    deviceInfo.graphicsQueue(),
                    deviceInfo.graphicsQueueFamily()
            );
            if (result != 0) {
                SaltsAntiAliasing.LOGGER.warn("DLSS native initialization failed with result {}", result);
                return DlssRuntimeStatus.INITIALIZATION_FAILED;
            }
            initialized = true;
        }

        return isSupportedNative() ? DlssRuntimeStatus.READY : DlssRuntimeStatus.UNSUPPORTED;
    }

    DlssOptimalSettings queryOptimalSettings(DlssQualityPreset preset, int outputWidth, int outputHeight) {
        if (!initialized || outputWidth <= 0 || outputHeight <= 0) {
            return DlssOptimalSettings.fallback(outputWidth, outputHeight);
        }

        int[] values = new int[6];
        float[] sharpness = new float[1];
        int result = queryOptimalSettingsNative(preset.ordinal(), outputWidth, outputHeight, values, sharpness);
        if (result != 0) {
            return DlssOptimalSettings.fallback(outputWidth, outputHeight);
        }

        return new DlssOptimalSettings(
                Math.max(1, values[0]),
                Math.max(1, values[1]),
                Math.max(1, values[2]),
                Math.max(1, values[3]),
                Math.max(1, values[4]),
                Math.max(1, values[5]),
                sharpness[0]
        );
    }

    int evaluate(DlssEvaluateParameters parameters) {
        if (!initialized) {
            return -1;
        }

        return evaluateNative(
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
                parameters.previousViewProjection()
        );
    }

    void shutdown() {
        if (bridgeLoaded && initialized) {
            shutdownNative();
            initialized = false;
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
            bridgeLoaded = true;
            lastBridgePath = bridgePath;
            return true;
        } catch (UnsatisfiedLinkError | SecurityException exception) {
            SaltsAntiAliasing.LOGGER.warn("Unable to load DLSS JNI bridge from {}", bridgePath, exception);
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
            return true;
        }

        try {
            System.load(interposerPath);
            interposerLoaded = true;
            lastInterposerPath = interposerPath;
            return true;
        } catch (UnsatisfiedLinkError | SecurityException exception) {
            SaltsAntiAliasing.LOGGER.warn("Unable to load NVIDIA Streamline interposer from {}", interposerPath, exception);
            interposerLoaded = false;
            return false;
        }
    }

    private static native int initializeNative(
            String pluginPath,
            String logPath,
            int applicationId,
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

    private static native int evaluateNative(
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
            float[] previousViewProjection
    );

    private static native void shutdownNative();
}
