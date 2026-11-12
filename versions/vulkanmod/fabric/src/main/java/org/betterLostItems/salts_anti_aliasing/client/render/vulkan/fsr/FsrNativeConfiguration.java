package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;

/**
 * External AMD FSR runtime settings.
 */
public record FsrNativeConfiguration(
        String bridgePath,
        String runtimePath,
        String logPath
) {
    private static final String BRIDGE_PROPERTY = "salts.fsr.bridgePath";
    private static final String RUNTIME_PROPERTY = "salts.fsr.runtimePath";
    private static final String LOG_PROPERTY = "salts.fsr.logPath";
    private static final String BRIDGE_ENV = "SALTS_FSR_BRIDGE_PATH";
    private static final String RUNTIME_ENV = "SALTS_FSR_RUNTIME_PATH";
    private static final String LOG_ENV = "SALTS_FSR_LOG_PATH";

    public static FsrNativeConfiguration fromConfig(AntiAliasingConfig config) {
        String bridgePath = firstNonBlank(System.getProperty(BRIDGE_PROPERTY), System.getenv(BRIDGE_ENV), config.fsrBridgePath);
        String runtimePath = firstNonBlank(System.getProperty(RUNTIME_PROPERTY), System.getenv(RUNTIME_ENV), config.fsrRuntimePath);
        String logPath = firstNonBlank(System.getProperty(LOG_PROPERTY), System.getenv(LOG_ENV), config.fsrLogPath);
        return withBundledFallback(bridgePath, runtimePath, logPath);
    }

    public static FsrNativeConfiguration fromEnvironment() {
        String bridgePath = firstNonBlank(System.getProperty(BRIDGE_PROPERTY), System.getenv(BRIDGE_ENV), "");
        String runtimePath = firstNonBlank(System.getProperty(RUNTIME_PROPERTY), System.getenv(RUNTIME_ENV), "");
        String logPath = firstNonBlank(System.getProperty(LOG_PROPERTY), System.getenv(LOG_ENV), "");
        return withBundledFallback(bridgePath, runtimePath, logPath);
    }

    public boolean hasAnySetting() {
        return !bridgePath.isBlank() || !runtimePath.isBlank();
    }

    public FsrRuntimeStatus validate() {
        if (!hasAnySetting()) {
            return FsrRuntimeStatus.NOT_CONFIGURED;
        }
        if (bridgePath.isBlank()) {
            return FsrRuntimeStatus.BRIDGE_PATH_MISSING;
        }
        if (runtimePath.isBlank()) {
            return FsrRuntimeStatus.RUNTIME_PATH_MISSING;
        }
        return FsrRuntimeStatus.UPSCALING_READY;
    }

    private static String firstNonBlank(String first, String second, String fallback) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        if (second != null && !second.isBlank()) {
            return second.trim();
        }
        return fallback == null ? "" : fallback.trim();
    }

    private static FsrNativeConfiguration withBundledFallback(String bridgePath, String runtimePath, String logPath) {
        if (bridgePath.isBlank() && runtimePath.isBlank()) {
            return FsrBundledNativeResolver.resolve(logPath)
                    .orElseGet(() -> new FsrNativeConfiguration("", "", logPath));
        }

        return new FsrNativeConfiguration(bridgePath, runtimePath, logPath);
    }
}
