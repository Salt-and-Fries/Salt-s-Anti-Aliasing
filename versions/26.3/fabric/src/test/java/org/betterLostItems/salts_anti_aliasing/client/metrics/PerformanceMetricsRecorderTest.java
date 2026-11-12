package org.betterLostItems.salts_anti_aliasing.client.metrics;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceMetricsRecorderTest {
    @TempDir
    Path tempDirectory;

    @Test
    void writesStructuredDiagnosticsAndExceptionContext() throws IOException {
        AtomicBoolean enabled = new AtomicBoolean(true);
        AntiAliasingConfig config = new AntiAliasingConfig();
        config.mode = AntiAliasingMode.MSAA;
        config.recordMetrics = true;

        PerformanceMetricsRecorder recorder = new PerformanceMetricsRecorder(
                tempDirectory,
                enabled::get,
                () -> config.mode,
                config::copy
        );

        MetricsDiagnostics.info("test.renderer", "resource_created", MetricsDiagnostics.fields(
                "dimensions", Map.of("width", 3840, "height", 2160),
                "sampleCounts", new int[] {1, 2, 4, 8, 16}
        ));
        IllegalStateException failure = new IllegalStateException("intentional diagnostics test");
        MetricsDiagnostics.error(
                "test.renderer",
                "operation_failed",
                failure,
                "resultCode", -7,
                "fallback", "8x"
        );
        recorder.recordFrame(16_666_667L, 60);
        recorder.recordFrame(120_000_000L, 8);
        recorder.close();

        JsonObject report = readLatestReport();
        assertEquals(2, report.getAsJsonObject("metadata").get("reportVersion").getAsInt());

        JsonObject diagnostics = report.getAsJsonObject("diagnostics");
        assertEquals(4_096, diagnostics.get("maximumRetainedEvents").getAsInt());
        assertTrue(diagnostics.get("retainedEventCount").getAsInt() >= 5);
        assertTrue(diagnostics.getAsJsonObject("countsByLevel").get("ERROR").getAsLong() >= 1L);
        assertTrue(diagnostics.getAsJsonObject("countsByCategory").get("test.renderer").getAsLong() >= 2L);
        assertTrue(diagnostics.getAsJsonObject("runtimeAtReportTime").get("maximumHeapBytes").getAsLong() > 0L);

        JsonObject created = findEvent(diagnostics.getAsJsonArray("events"), "resource_created");
        JsonObject details = created.getAsJsonObject("details");
        assertEquals(3840, details.getAsJsonObject("dimensions").get("width").getAsInt());
        assertEquals(5, details.getAsJsonArray("sampleCounts").size());

        JsonObject failed = findEvent(diagnostics.getAsJsonArray("events"), "operation_failed");
        assertEquals(-7, failed.getAsJsonObject("details").get("resultCode").getAsInt());
        assertEquals("java.lang.IllegalStateException", failed.getAsJsonObject("exception").get("type").getAsString());
        assertTrue(failed.getAsJsonObject("exception").get("stackTrace").getAsString()
                .contains("intentional diagnostics test"));

        assertTrue(findEvent(diagnostics.getAsJsonArray("events"), "lag_spike").isJsonObject());
        assertTrue(findEvent(diagnostics.getAsJsonArray("events"), "severe_lag_spike").isJsonObject());
    }

    @Test
    void remainsInactiveUntilRecordingIsEnabled() throws IOException {
        AtomicBoolean enabled = new AtomicBoolean(false);
        AntiAliasingConfig config = new AntiAliasingConfig();
        PerformanceMetricsRecorder recorder = new PerformanceMetricsRecorder(
                tempDirectory,
                enabled::get,
                () -> config.mode,
                config::copy
        );

        MetricsDiagnostics.info("test", "ignored_while_disabled", "value", 1);
        recorder.recordFrame(16_666_667L, 60);
        assertFalse(Files.exists(tempDirectory.resolve("salts_anti_aliasing_metrics_latest.json")));

        enabled.set(true);
        config.recordMetrics = true;
        MetricsDiagnostics.info("test", "recorded_after_enable", "value", 2);
        recorder.close();
        MetricsDiagnostics.info("test", "ignored_after_close", "value", 3);
        recorder.close();

        JsonArray events = readLatestReport().getAsJsonObject("diagnostics").getAsJsonArray("events");
        assertEquals("recorded_after_enable", findEvent(events, "recorded_after_enable").get("event").getAsString());
        assertTrue(events.asList().stream()
                .noneMatch(event -> "ignored_while_disabled".equals(event.getAsJsonObject().get("event").getAsString())));
        assertTrue(events.asList().stream()
                .noneMatch(event -> "ignored_after_close".equals(event.getAsJsonObject().get("event").getAsString())));
    }

    @Test
    void retainsTheNewestDiagnosticsAndCountsDroppedEvents() throws IOException {
        AntiAliasingConfig config = new AntiAliasingConfig();
        config.recordMetrics = true;
        PerformanceMetricsRecorder recorder = new PerformanceMetricsRecorder(
                tempDirectory,
                () -> true,
                () -> config.mode,
                config::copy
        );

        for (int sequence = 0; sequence < 4_105; sequence++) {
            MetricsDiagnostics.info("bounded", "sequence", "sequence", sequence);
        }
        recorder.close();

        JsonObject diagnostics = readLatestReport().getAsJsonObject("diagnostics");
        assertEquals(4_096, diagnostics.get("retainedEventCount").getAsInt());
        assertEquals(9, diagnostics.get("droppedEventCount").getAsLong());
        assertEquals(4_105, diagnostics.getAsJsonObject("countsByCategory").get("bounded").getAsLong());
        JsonArray events = diagnostics.getAsJsonArray("events");
        assertEquals(9, events.get(0).getAsJsonObject().getAsJsonObject("details").get("sequence").getAsInt());
        assertEquals(4_104, events.get(events.size() - 1).getAsJsonObject()
                .getAsJsonObject("details").get("sequence").getAsInt());
    }

    private JsonObject readLatestReport() throws IOException {
        Path reportPath = tempDirectory.resolve("salts_anti_aliasing_metrics_latest.json");
        return JsonParser.parseString(Files.readString(reportPath)).getAsJsonObject();
    }

    private static JsonObject findEvent(JsonArray events, String eventName) {
        return events.asList().stream()
                .map(element -> element.getAsJsonObject())
                .filter(event -> eventName.equals(event.get("event").getAsString()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Missing diagnostic event: " + eventName));
    }
}
