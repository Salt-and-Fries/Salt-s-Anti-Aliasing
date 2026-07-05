package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;

/**
 * External DLSS runtime settings. System properties and environment variables intentionally
 * override disk config so the bridge can be initialized before normal client config is loaded.
 */
public record DlssNativeConfiguration(
        String bridgePath,
        String pluginPath,
        String logPath,
        int applicationId
) {
    private static final String BRIDGE_PROPERTY = "salts.dlss.bridgePath";
    private static final String PLUGIN_PROPERTY = "salts.dlss.pluginPath";
    private static final String LOG_PROPERTY = "salts.dlss.logPath";
    private static final String APPLICATION_ID_PROPERTY = "salts.dlss.applicationId";
    private static final String BRIDGE_ENV = "SALTS_DLSS_BRIDGE_PATH";
    private static final String PLUGIN_ENV = "SALTS_DLSS_PLUGIN_PATH";
    private static final String LOG_ENV = "SALTS_DLSS_LOG_PATH";
    private static final String APPLICATION_ID_ENV = "SALTS_DLSS_APPLICATION_ID";

    public static DlssNativeConfiguration fromConfig(AntiAliasingConfig config) {
        return new DlssNativeConfiguration(
                firstNonBlank(System.getProperty(BRIDGE_PROPERTY), System.getenv(BRIDGE_ENV), config.dlssBridgePath),
                firstNonBlank(System.getProperty(PLUGIN_PROPERTY), System.getenv(PLUGIN_ENV), config.dlssPluginPath),
                firstNonBlank(System.getProperty(LOG_PROPERTY), System.getenv(LOG_ENV), config.dlssLogPath),
                firstPositiveInt(System.getProperty(APPLICATION_ID_PROPERTY), System.getenv(APPLICATION_ID_ENV), config.dlssApplicationId)
        );
    }

    public static DlssNativeConfiguration fromEnvironment() {
        return new DlssNativeConfiguration(
                firstNonBlank(System.getProperty(BRIDGE_PROPERTY), System.getenv(BRIDGE_ENV), ""),
                firstNonBlank(System.getProperty(PLUGIN_PROPERTY), System.getenv(PLUGIN_ENV), ""),
                firstNonBlank(System.getProperty(LOG_PROPERTY), System.getenv(LOG_ENV), ""),
                firstPositiveInt(System.getProperty(APPLICATION_ID_PROPERTY), System.getenv(APPLICATION_ID_ENV), 0)
        );
    }

    public boolean hasAnySetting() {
        return !bridgePath.isBlank() || !pluginPath.isBlank() || !logPath.isBlank() || applicationId > 0;
    }

    public DlssRuntimeStatus validate() {
        if (!hasAnySetting()) {
            return DlssRuntimeStatus.NOT_CONFIGURED;
        }
        if (bridgePath.isBlank()) {
            return DlssRuntimeStatus.BRIDGE_PATH_MISSING;
        }
        if (pluginPath.isBlank()) {
            return DlssRuntimeStatus.PLUGIN_PATH_MISSING;
        }
        if (applicationId <= 0) {
            return DlssRuntimeStatus.APPLICATION_ID_MISSING;
        }
        return DlssRuntimeStatus.READY;
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

    private static int firstPositiveInt(String first, String second, int fallback) {
        int parsed = parsePositiveInt(first);
        if (parsed > 0) {
            return parsed;
        }

        parsed = parsePositiveInt(second);
        return parsed > 0 ? parsed : Math.max(0, fallback);
    }

    private static int parsePositiveInt(String value) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Math.max(0, Integer.parseInt(value.trim()));
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}
