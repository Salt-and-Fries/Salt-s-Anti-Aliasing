package org.betterLostItems.salts_anti_aliasing.client.metrics;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Array;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Process-wide structured diagnostic sink used by renderer code that cannot conveniently hold a
 * reference to the active metrics recorder.
 *
 * <p>Calls are deliberately cheap no-ops unless {@code recordMetrics} is enabled. Values are
 * normalized before crossing the sink boundary so the metrics report can always be serialized,
 * even when a caller supplies an LWJGL or Minecraft implementation object.</p>
 */
public final class MetricsDiagnostics {
    private static final int MAX_VALUE_LENGTH = 16_384;
    private static final int MAX_STACK_TRACE_LENGTH = 65_536;
    private static final int MAX_COLLECTION_ENTRIES = 256;
    private static final int MAX_ONCE_KEYS = 8_192;

    private static volatile BooleanSupplier enabledSupplier = () -> false;
    private static volatile Consumer<DiagnosticEvent> eventSink = event -> {
    };
    private static final Set<String> ONCE_KEYS = Collections.synchronizedSet(new LinkedHashSet<>());

    private MetricsDiagnostics() {
    }

    static void configure(BooleanSupplier enabled, Consumer<DiagnosticEvent> sink) {
        enabledSupplier = enabled == null ? () -> false : enabled;
        eventSink = sink == null ? event -> {
        } : sink;
        ONCE_KEYS.clear();
    }

    public static boolean enabled() {
        try {
            return enabledSupplier.getAsBoolean();
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    public static void info(String category, String event, Object... keyValues) {
        record("INFO", category, event, null, keyValues);
    }

    public static void warn(String category, String event, Object... keyValues) {
        record("WARN", category, event, null, keyValues);
    }

    public static void warn(
            String category,
            String event,
            Map<String, ?> details,
            Throwable exception
    ) {
        recordFields("WARN", category, event, exception, details);
    }

    public static void error(String category, String event, Throwable exception, Object... keyValues) {
        record("ERROR", category, event, exception, keyValues);
    }

    public static void error(
            String category,
            String event,
            Map<String, ?> details,
            Throwable exception
    ) {
        recordFields("ERROR", category, event, exception, details);
    }

    public static void infoOnce(String key, String category, String event, Object... keyValues) {
        recordOnce(key, "INFO", category, event, null, keyValues);
    }

    public static void warnOnce(String key, String category, String event, Object... keyValues) {
        recordOnce(key, "WARN", category, event, null, keyValues);
    }

    public static void warnOnce(
            String key,
            String category,
            String event,
            Throwable exception,
            Object... keyValues
    ) {
        recordOnce(key, "WARN", category, event, exception, keyValues);
    }

    /** Records JVM, operating-system, loader, and loaded-mod context once per metrics recorder. */
    public static void recordEnvironment() {
        if (!enabled()) {
            return;
        }

        Runtime runtime = Runtime.getRuntime();
        java.lang.management.OperatingSystemMXBean operatingSystem =
                ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean extendedOperatingSystem =
                operatingSystem instanceof com.sun.management.OperatingSystemMXBean extended ? extended : null;
        List<String> loadedMods = new ArrayList<>();
        try {
            for (ModContainer container : FabricLoader.getInstance().getAllMods()) {
                loadedMods.add(
                        container.getMetadata().getId() + ":"
                                + container.getMetadata().getVersion().getFriendlyString()
                );
            }
            Collections.sort(loadedMods);
        } catch (RuntimeException exception) {
            loadedMods.add("unavailable: " + exception.getClass().getName());
        }

        infoOnce(
                "environment.runtime",
                "environment",
                "runtime_context",
                "osName", System.getProperty("os.name", ""),
                "osVersion", System.getProperty("os.version", ""),
                "osArchitecture", System.getProperty("os.arch", ""),
                "javaVersion", System.getProperty("java.version", ""),
                "javaVendor", System.getProperty("java.vendor", ""),
                "javaVmName", System.getProperty("java.vm.name", ""),
                "javaVmVersion", System.getProperty("java.vm.version", ""),
                "availableProcessors", runtime.availableProcessors(),
                "systemLoadAverage", operatingSystem.getSystemLoadAverage(),
                "totalPhysicalMemoryBytes", extendedOperatingSystem == null
                        ? null : extendedOperatingSystem.getTotalMemorySize(),
                "freePhysicalMemoryBytes", extendedOperatingSystem == null
                        ? null : extendedOperatingSystem.getFreeMemorySize(),
                "committedVirtualMemoryBytes", extendedOperatingSystem == null
                        ? null : extendedOperatingSystem.getCommittedVirtualMemorySize(),
                "processCpuTimeNs", extendedOperatingSystem == null
                        ? null : extendedOperatingSystem.getProcessCpuTime(),
                "systemCpuLoad", extendedOperatingSystem == null
                        ? null : extendedOperatingSystem.getCpuLoad(),
                "processCpuLoad", extendedOperatingSystem == null
                        ? null : extendedOperatingSystem.getProcessCpuLoad(),
                "maximumHeapBytes", runtime.maxMemory(),
                "committedHeapBytes", runtime.totalMemory(),
                "freeHeapBytes", runtime.freeMemory(),
                "loadedMods", loadedMods
        );
    }

    public static Map<String, Object> fields(Object... keyValues) {
        LinkedHashMap<String, Object> fields = new LinkedHashMap<>();
        if (keyValues == null) {
            return fields;
        }
        if (keyValues.length == 1 && keyValues[0] instanceof Map<?, ?> map) {
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ >= MAX_COLLECTION_ENTRIES) {
                    fields.put("<truncated>", map.size() - MAX_COLLECTION_ENTRIES + " more entries");
                    break;
                }
                fields.put(String.valueOf(entry.getKey()), normalize(entry.getValue(), 0));
            }
            return fields;
        }

        for (int index = 0; index < keyValues.length; index += 2) {
            String key = String.valueOf(keyValues[index]);
            Object value = index + 1 < keyValues.length ? keyValues[index + 1] : "<missing value>";
            fields.put(key, normalize(value, 0));
        }
        return fields;
    }

    /** Produces a complete, stable configuration object suitable for embedding in event details. */
    public static Map<String, Object> configSnapshot(AntiAliasingConfig config) {
        if (config == null) {
            return Map.of();
        }
        return fields(
                "configVersion", config.configVersion,
                "mode", config.mode,
                "qualityPreset", config.qualityPreset,
                "sharpenStrength", config.sharpenStrength,
                "msaaSampleLevel", config.msaaSampleLevel,
                "msaaAlphaToCoverage", config.msaaAlphaToCoverage,
                "ssaaScaleLevel", config.ssaaScaleLevel,
                "nisUpscaleQualityPreset", config.nisUpscaleQualityPreset,
                "dlssQualityPreset", config.dlssQualityPreset,
                "fsrQualityPreset", config.fsrQualityPreset,
                "internalResolutionScale", config.internalResolutionScale,
                "keepHudAtNativeResolution", config.keepHudAtNativeResolution,
                "debugViewsEnabled", config.debugViewsEnabled,
                "recordMetrics", config.recordMetrics,
                "dlssBridgePath", config.dlssBridgePath,
                "dlssPluginPath", config.dlssPluginPath,
                "dlssLogPath", config.dlssLogPath,
                "dlssApplicationId", config.dlssApplicationId,
                "fsrBridgePath", config.fsrBridgePath,
                "fsrRuntimePath", config.fsrRuntimePath,
                "fsrLogPath", config.fsrLogPath
        );
    }

    private static void recordOnce(
            String key,
            String level,
            String category,
            String event,
            Throwable exception,
            Object... keyValues
    ) {
        if (!enabled()) {
            return;
        }
        if (!rememberOnceKey(key)) {
            return;
        }
        recordEnabled(level, category, event, exception, keyValues);
    }

    private static boolean rememberOnceKey(String key) {
        synchronized (ONCE_KEYS) {
            if (ONCE_KEYS.contains(key)) {
                return false;
            }
            if (ONCE_KEYS.size() >= MAX_ONCE_KEYS) {
                ONCE_KEYS.remove(ONCE_KEYS.iterator().next());
            }
            return ONCE_KEYS.add(key);
        }
    }

    private static void record(
            String level,
            String category,
            String event,
            Throwable exception,
            Object... keyValues
    ) {
        if (!enabled()) {
            return;
        }
        recordEnabled(level, category, event, exception, keyValues);
    }

    private static void recordEnabled(
            String level,
            String category,
            String event,
            Throwable exception,
            Object... keyValues
    ) {
        DiagnosticEvent diagnosticEvent = new DiagnosticEvent(
                Instant.now().toString(),
                level,
                category == null ? "general" : category,
                event == null ? "unspecified" : event,
                Thread.currentThread().getName(),
                fields(keyValues),
                exception == null ? null : describeException(exception)
        );
        try {
            eventSink.accept(diagnosticEvent);
        } catch (RuntimeException ignored) {
            // Diagnostics must never alter rendering behavior or turn a recoverable failure fatal.
        }
    }

    private static void recordFields(
            String level,
            String category,
            String event,
            Throwable exception,
            Map<String, ?> details
    ) {
        if (!enabled()) {
            return;
        }
        DiagnosticEvent diagnosticEvent = new DiagnosticEvent(
                Instant.now().toString(),
                level,
                category == null ? "general" : category,
                event == null ? "unspecified" : event,
                Thread.currentThread().getName(),
                fields(details),
                exception == null ? null : describeException(exception)
        );
        try {
            eventSink.accept(diagnosticEvent);
        } catch (RuntimeException ignored) {
            // Diagnostics must never alter rendering behavior or turn a recoverable failure fatal.
        }
    }

    private static DiagnosticException describeException(Throwable exception) {
        StringWriter stackTrace = new StringWriter();
        exception.printStackTrace(new PrintWriter(stackTrace));
        return new DiagnosticException(
                exception.getClass().getName(),
                truncate(exception.getMessage(), MAX_VALUE_LENGTH),
                truncate(stackTrace.toString(), MAX_STACK_TRACE_LENGTH)
        );
    }

    private static Object normalize(Object value, int depth) {
        if (value == null || value instanceof Number || value instanceof Boolean) {
            return value;
        }
        if (value instanceof CharSequence sequence) {
            return truncate(sequence.toString(), MAX_VALUE_LENGTH);
        }
        if (value instanceof Enum<?> enumValue) {
            return enumValue.name();
        }
        if (value instanceof Path path) {
            return truncate(path.toAbsolutePath().normalize().toString(), MAX_VALUE_LENGTH);
        }
        if (depth >= 4) {
            return truncate(String.valueOf(value), MAX_VALUE_LENGTH);
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
            int count = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (count++ >= MAX_COLLECTION_ENTRIES) {
                    normalized.put("<truncated>", map.size() - MAX_COLLECTION_ENTRIES + " more entries");
                    break;
                }
                normalized.put(String.valueOf(entry.getKey()), normalize(entry.getValue(), depth + 1));
            }
            return normalized;
        }
        if (value instanceof Collection<?> collection) {
            ArrayList<Object> normalized = new ArrayList<>();
            int count = 0;
            for (Object entry : collection) {
                if (count++ >= MAX_COLLECTION_ENTRIES) {
                    normalized.add("<" + (collection.size() - MAX_COLLECTION_ENTRIES) + " more entries>");
                    break;
                }
                normalized.add(normalize(entry, depth + 1));
            }
            return normalized;
        }
        if (value.getClass().isArray()) {
            int length = Array.getLength(value);
            ArrayList<Object> normalized = new ArrayList<>(Math.min(length, MAX_COLLECTION_ENTRIES));
            for (int index = 0; index < Math.min(length, MAX_COLLECTION_ENTRIES); index++) {
                normalized.add(normalize(Array.get(value, index), depth + 1));
            }
            if (length > MAX_COLLECTION_ENTRIES) {
                normalized.add("<" + (length - MAX_COLLECTION_ENTRIES) + " more entries>");
            }
            return normalized;
        }
        return truncate(String.valueOf(value), MAX_VALUE_LENGTH);
    }

    private static String truncate(String value, int maximumLength) {
        if (value == null || value.length() <= maximumLength) {
            return value;
        }
        return value.substring(0, maximumLength) + "\n<truncated>";
    }

    public record DiagnosticEvent(
            String timestamp,
            String level,
            String category,
            String event,
            String thread,
            Map<String, Object> details,
            DiagnosticException exception
    ) {
    }

    public record DiagnosticException(String type, String message, String stackTrace) {
    }
}
