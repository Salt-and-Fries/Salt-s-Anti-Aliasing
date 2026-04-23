package org.betterLostItems.salts_anti_aliasing.client.metrics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

public final class PerformanceMetricsRecorder {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final DateTimeFormatter FILE_TIMESTAMP =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    private static final double NANOS_PER_SECOND = 1_000_000_000.0d;
    private static final double NANOS_PER_MILLISECOND = 1_000_000.0d;
    private static final long WRITE_INTERVAL_NANOS = Duration.ofSeconds(5).toNanos();
    private static final double LAG_SPIKE_THRESHOLD_MS = 50.0d;
    private static final double LAG_SPIKE_RESET_MS = 40.0d;
    private static final double SEVERE_SPIKE_THRESHOLD_MS = 100.0d;
    private static final double SEVERE_SPIKE_RESET_MS = 80.0d;
    private static final int FPS_DROP_BASELINE_WINDOW = 120;
    private static final int FPS_DROP_MIN_BASELINE_SAMPLES = 30;
    private static final double FPS_DROP_RATIO = 0.72d;
    private static final double FPS_DROP_RECOVERY_RATIO = 0.88d;
    private static final double FPS_DROP_MIN_DELTA = 12.0d;
    private static final double BELOW_60_FPS = 60.0d;
    private static final double BELOW_45_FPS = 45.0d;
    private static final double BELOW_30_FPS = 30.0d;

    private final Path metricsDirectory;
    private final Path latestReportPath;
    private final BooleanSupplier recordingEnabledSupplier;
    private final Supplier<AntiAliasingMode> modeSupplier;
    private final Supplier<AntiAliasingConfig> configSnapshotSupplier;
    private Session activeSession;

    public PerformanceMetricsRecorder(
            BooleanSupplier recordingEnabledSupplier,
            Supplier<AntiAliasingMode> modeSupplier,
            Supplier<AntiAliasingConfig> configSnapshotSupplier
    ) {
        this.metricsDirectory = FabricLoader.getInstance().getGameDir().resolve("logs");
        this.latestReportPath = metricsDirectory.resolve("salts_anti_aliasing_metrics_latest.json");
        this.recordingEnabledSupplier = recordingEnabledSupplier;
        this.modeSupplier = modeSupplier;
        this.configSnapshotSupplier = configSnapshotSupplier;
    }

    public synchronized void recordFrame(long frameTimeNs, int displayedFps) {
        if (!recordingEnabledSupplier.getAsBoolean()) {
            finishActiveSessionIfNeeded("record_metrics disabled");
            return;
        }

        if (frameTimeNs <= 0L) {
            return;
        }

        double fps = NANOS_PER_SECOND / frameTimeNs;
        if (!Double.isFinite(fps) || fps <= 0.0d) {
            return;
        }

        if (activeSession == null) {
            startSession();
        }

        activeSession.recordFrame(modeSupplier.get(), frameTimeNs, displayedFps);
        if (activeSession.shouldWriteSnapshot()) {
            writeLatestSnapshot();
        }
    }

    public synchronized void close() {
        finishActiveSessionIfNeeded("client shutdown");
    }

    private void startSession() {
        AntiAliasingConfig startConfig = configSnapshotSupplier.get();
        activeSession = new Session(startConfig, metricsDirectory);
        SaltsAntiAliasing.LOGGER.info("Performance metrics recording enabled -> {}", latestReportPath);
        writeLatestSnapshot();
    }

    private void finishActiveSessionIfNeeded(String endedBecause) {
        if (activeSession == null) {
            return;
        }

        writeReports(true, endedBecause);
        SaltsAntiAliasing.LOGGER.info("Saved performance metrics report to {}", activeSession.archiveReportPath);
        activeSession = null;
    }

    private void writeLatestSnapshot() {
        writeReports(false, "recording");
    }

    private void writeReports(boolean finalReport, String endedBecause) {
        if (activeSession == null) {
            return;
        }

        try {
            Files.createDirectories(metricsDirectory);

            AntiAliasingConfig currentConfig = configSnapshotSupplier.get();
            MetricsReport report = activeSession.createReport(
                    currentConfig,
                    latestReportPath,
                    finalReport,
                    endedBecause
            );

            writeJson(latestReportPath, report);
            if (finalReport) {
                writeJson(activeSession.archiveReportPath, report);
            }
            activeSession.markWritten();
        } catch (IOException exception) {
            SaltsAntiAliasing.LOGGER.error("Failed to write performance metrics report", exception);
        }
    }

    private static void writeJson(Path path, Object report) throws IOException {
        try (Writer writer = Files.newBufferedWriter(path)) {
            GSON.toJson(report, writer);
        }
    }

    private static double round(double value) {
        return round(value, 2);
    }

    private static double round(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return 0.0d;
        }

        double scale = Math.pow(10.0d, decimals);
        return Math.round(value * scale) / scale;
    }

    private static final class Session {
        private final Instant startedAt = Instant.now();
        private final AntiAliasingConfig configAtStart;
        private final EnumMap<AntiAliasingMode, Aggregate> perMode = new EnumMap<>(AntiAliasingMode.class);
        private final EnumMap<AntiAliasingMode, Integer> modeEntryCounts = new EnumMap<>(AntiAliasingMode.class);
        private final Aggregate overall = new Aggregate();
        private final RollingAverage fpsBaseline = new RollingAverage(FPS_DROP_BASELINE_WINDOW);
        private final Path archiveReportPath;

        private AntiAliasingMode currentMode;
        private boolean inLagSpike;
        private boolean inSevereSpike;
        private boolean inFpsDrop;
        private long modeSwitches;
        private long lastWrittenAtNano = System.nanoTime();

        private Session(AntiAliasingConfig configAtStart, Path metricsDirectory) {
            this.configAtStart = configAtStart.copy();
            for (AntiAliasingMode mode : AntiAliasingMode.implementedModes()) {
                perMode.put(mode, new Aggregate());
                modeEntryCounts.put(mode, 0);
            }

            String sessionFile = "salts_anti_aliasing_metrics_" + FILE_TIMESTAMP.format(startedAt) + ".json";
            this.archiveReportPath = metricsDirectory.resolve(sessionFile);
        }

        private void recordFrame(AntiAliasingMode mode, long frameTimeNs, int displayedFps) {
            double frameTimeMs = frameTimeNs / NANOS_PER_MILLISECOND;
            double fps = NANOS_PER_SECOND / frameTimeNs;
            if (!Double.isFinite(frameTimeMs) || frameTimeMs <= 0.0d) {
                return;
            }

            if (currentMode != mode) {
                if (currentMode != null) {
                    modeSwitches++;
                }
                currentMode = mode;
                modeEntryCounts.merge(mode, 1, Integer::sum);
                inLagSpike = false;
                inSevereSpike = false;
                inFpsDrop = false;
            }

            double baselineFps = fpsBaseline.average();
            boolean baselineReady = fpsBaseline.size() >= FPS_DROP_MIN_BASELINE_SAMPLES;

            overall.recordSample(fps, frameTimeMs, displayedFps);
            perMode.get(mode).recordSample(fps, frameTimeMs, displayedFps);

            if (frameTimeMs >= LAG_SPIKE_THRESHOLD_MS) {
                if (!inLagSpike) {
                    overall.lagSpikes++;
                    perMode.get(mode).lagSpikes++;
                    inLagSpike = true;
                }
            } else if (frameTimeMs < LAG_SPIKE_RESET_MS) {
                inLagSpike = false;
            }

            if (frameTimeMs >= SEVERE_SPIKE_THRESHOLD_MS) {
                if (!inSevereSpike) {
                    overall.severeLagSpikes++;
                    perMode.get(mode).severeLagSpikes++;
                    inSevereSpike = true;
                }
            } else if (frameTimeMs < SEVERE_SPIKE_RESET_MS) {
                inSevereSpike = false;
            }

            if (baselineReady) {
                boolean fpsDrop = fps <= baselineFps * FPS_DROP_RATIO && (baselineFps - fps) >= FPS_DROP_MIN_DELTA;
                if (fpsDrop) {
                    if (!inFpsDrop) {
                        overall.fpsDrops++;
                        perMode.get(mode).fpsDrops++;
                        inFpsDrop = true;
                    }
                } else if (fps > baselineFps * FPS_DROP_RECOVERY_RATIO) {
                    inFpsDrop = false;
                }
            }

            fpsBaseline.push(fps);
        }

        private boolean shouldWriteSnapshot() {
            return System.nanoTime() - lastWrittenAtNano >= WRITE_INTERVAL_NANOS;
        }

        private void markWritten() {
            lastWrittenAtNano = System.nanoTime();
        }

        private MetricsReport createReport(
                AntiAliasingConfig currentConfig,
                Path latestReportPath,
                boolean finalReport,
                String endedBecause
        ) {
            MetricsReport report = new MetricsReport();
            report.metadata = new ReportMetadata();
            report.metadata.reportVersion = 1;
            report.metadata.startedAt = startedAt.toString();
            report.metadata.endedAt = Instant.now().toString();
            report.metadata.finalReport = finalReport;
            report.metadata.endedBecause = endedBecause;
            report.metadata.latestReportPath = latestReportPath.toString();
            report.metadata.sessionReportPath = archiveReportPath.toString();

            report.thresholds = new Thresholds();
            report.thresholds.lagSpikeThresholdMs = LAG_SPIKE_THRESHOLD_MS;
            report.thresholds.severeLagSpikeThresholdMs = SEVERE_SPIKE_THRESHOLD_MS;
            report.thresholds.fpsDropBaselineWindowFrames = FPS_DROP_BASELINE_WINDOW;
            report.thresholds.fpsDropPercentOfBaseline = FPS_DROP_RATIO;
            report.thresholds.fpsDropMinimumDelta = FPS_DROP_MIN_DELTA;

            report.configAtStart = configAtStart.copy();
            report.configAtReportTime = currentConfig.copy();

            report.session = new SessionSummary();
            report.session.modesObserved = countModesObserved();
            report.session.modeSwitches = modeSwitches;
            report.session.currentMode = currentMode == null ? AntiAliasingMode.OFF.displayName() : currentMode.displayName();

            report.overall = overall.toSummary(countModesObserved());
            report.perMode = new LinkedHashMap<>();
            for (AntiAliasingMode mode : AntiAliasingMode.implementedModes()) {
                PerformanceSummary summary = perMode.get(mode).toSummary(modeEntryCounts.getOrDefault(mode, 0));
                report.perMode.put(mode.name(), summary);
            }

            return report;
        }

        private int countModesObserved() {
            int observed = 0;
            for (int count : modeEntryCounts.values()) {
                if (count > 0) {
                    observed++;
                }
            }
            return observed;
        }
    }

    private static final class Aggregate {
        private final DoubleSeries fpsSamples = new DoubleSeries();

        private long frames;
        private long lagSpikes;
        private long severeLagSpikes;
        private long fpsDrops;
        private long framesBelow60Fps;
        private long framesBelow45Fps;
        private long framesBelow30Fps;
        private double totalFrameTimeMs;
        private double totalDisplayedFps;
        private double minFps = Double.POSITIVE_INFINITY;
        private double maxFps = 0.0d;
        private double worstFrameTimeMs = 0.0d;
        private double bestFrameTimeMs = Double.POSITIVE_INFINITY;
        private double timeBelow60FpsMs;
        private double timeBelow45FpsMs;
        private double timeBelow30FpsMs;

        private void recordSample(double fps, double frameTimeMs, int displayedFps) {
            frames++;
            totalFrameTimeMs += frameTimeMs;
            totalDisplayedFps += displayedFps;
            minFps = Math.min(minFps, fps);
            maxFps = Math.max(maxFps, fps);
            worstFrameTimeMs = Math.max(worstFrameTimeMs, frameTimeMs);
            bestFrameTimeMs = Math.min(bestFrameTimeMs, frameTimeMs);
            fpsSamples.add(fps);

            if (fps < BELOW_60_FPS) {
                framesBelow60Fps++;
                timeBelow60FpsMs += frameTimeMs;
            }
            if (fps < BELOW_45_FPS) {
                framesBelow45Fps++;
                timeBelow45FpsMs += frameTimeMs;
            }
            if (fps < BELOW_30_FPS) {
                framesBelow30Fps++;
                timeBelow30FpsMs += frameTimeMs;
            }
        }

        private PerformanceSummary toSummary(int modeEntries) {
            PerformanceSummary summary = new PerformanceSummary();
            summary.modeEntries = modeEntries;
            summary.framesSampled = frames;

            double recordedSeconds = totalFrameTimeMs / 1000.0d;
            double recordedMinutes = recordedSeconds / 60.0d;
            summary.timeRecordedSeconds = round(recordedSeconds);
            summary.timeRecordedMinutes = round(recordedMinutes);

            if (frames == 0L || recordedSeconds <= 0.0d) {
                return summary;
            }

            double[] sortedFps = fpsSamples.sortedCopy();
            summary.averageFps = round(frames / recordedSeconds);
            summary.averageDisplayedFps = round(totalDisplayedFps / frames);
            summary.medianFps = round(percentile(sortedFps, 0.50d));
            summary.minimumFps = round(minFps);
            summary.maximumFps = round(maxFps);
            summary.onePercentLowFps = round(averageLowest(sortedFps, 0.01d));
            summary.fivePercentLowFps = round(averageLowest(sortedFps, 0.05d));
            summary.averageFrameTimeMs = round(totalFrameTimeMs / frames, 3);
            summary.medianFrameTimeMs = round(frameTimeMsFromFps(percentile(sortedFps, 0.50d)), 3);
            summary.percentile95FrameTimeMs = round(frameTimeMsFromFps(percentile(sortedFps, 0.05d)), 3);
            summary.bestFrameTimeMs = round(bestFrameTimeMs, 3);
            summary.worstFrameTimeMs = round(worstFrameTimeMs, 3);

            summary.lagSpikesTotal = lagSpikes;
            summary.lagSpikesPerMinute = round(ratePerMinute(lagSpikes, recordedMinutes));
            summary.severeLagSpikesTotal = severeLagSpikes;
            summary.severeLagSpikesPerMinute = round(ratePerMinute(severeLagSpikes, recordedMinutes));
            summary.fpsDropsTotal = fpsDrops;
            summary.fpsDropsPerMinute = round(ratePerMinute(fpsDrops, recordedMinutes));

            summary.framesBelow60Fps = framesBelow60Fps;
            summary.framesBelow45Fps = framesBelow45Fps;
            summary.framesBelow30Fps = framesBelow30Fps;
            summary.timeBelow60FpsSeconds = round(timeBelow60FpsMs / 1000.0d);
            summary.timeBelow45FpsSeconds = round(timeBelow45FpsMs / 1000.0d);
            summary.timeBelow30FpsSeconds = round(timeBelow30FpsMs / 1000.0d);
            return summary;
        }

        private static double averageLowest(double[] sortedAscending, double ratio) {
            if (sortedAscending.length == 0) {
                return 0.0d;
            }

            int count = Math.max(1, (int) Math.ceil(sortedAscending.length * ratio));
            double total = 0.0d;
            for (int index = 0; index < count; index++) {
                total += sortedAscending[index];
            }
            return total / count;
        }

        private static double percentile(double[] sortedAscending, double percentile) {
            if (sortedAscending.length == 0) {
                return 0.0d;
            }

            double clamped = Math.max(0.0d, Math.min(1.0d, percentile));
            double index = clamped * (sortedAscending.length - 1);
            int lower = (int) Math.floor(index);
            int upper = (int) Math.ceil(index);
            if (lower == upper) {
                return sortedAscending[lower];
            }

            double weight = index - lower;
            return sortedAscending[lower] * (1.0d - weight) + sortedAscending[upper] * weight;
        }

        private static double frameTimeMsFromFps(double fps) {
            if (!Double.isFinite(fps) || fps <= 0.0d) {
                return 0.0d;
            }
            return 1000.0d / fps;
        }

        private static double ratePerMinute(long count, double minutes) {
            if (minutes <= 0.0d) {
                return 0.0d;
            }

            return count / minutes;
        }
    }

    private static final class RollingAverage {
        private final double[] values;
        private int size;
        private int cursor;
        private double sum;

        private RollingAverage(int capacity) {
            this.values = new double[Math.max(1, capacity)];
        }

        private void push(double value) {
            if (size < values.length) {
                values[size++] = value;
                sum += value;
                return;
            }

            sum -= values[cursor];
            values[cursor] = value;
            sum += value;
            cursor = (cursor + 1) % values.length;
        }

        private int size() {
            return size;
        }

        private double average() {
            return size == 0 ? 0.0d : sum / size;
        }
    }

    private static final class DoubleSeries {
        private double[] values = new double[1024];
        private int size;

        private void add(double value) {
            if (size == values.length) {
                values = Arrays.copyOf(values, values.length * 2);
            }
            values[size++] = value;
        }

        private double[] sortedCopy() {
            double[] copy = Arrays.copyOf(values, size);
            Arrays.sort(copy);
            return copy;
        }
    }

    private static final class MetricsReport {
        public ReportMetadata metadata;
        public Thresholds thresholds;
        public AntiAliasingConfig configAtStart;
        public AntiAliasingConfig configAtReportTime;
        public SessionSummary session;
        public PerformanceSummary overall;
        public Map<String, PerformanceSummary> perMode;
    }

    private static final class ReportMetadata {
        public int reportVersion;
        public String startedAt;
        public String endedAt;
        public boolean finalReport;
        public String endedBecause;
        public String latestReportPath;
        public String sessionReportPath;
    }

    private static final class Thresholds {
        public double lagSpikeThresholdMs;
        public double severeLagSpikeThresholdMs;
        public int fpsDropBaselineWindowFrames;
        public double fpsDropPercentOfBaseline;
        public double fpsDropMinimumDelta;
    }

    private static final class SessionSummary {
        public int modesObserved;
        public long modeSwitches;
        public String currentMode;
    }

    private static final class PerformanceSummary {
        public int modeEntries;
        public long framesSampled;
        public double timeRecordedSeconds;
        public double timeRecordedMinutes;
        public double averageFps;
        public double averageDisplayedFps;
        public double medianFps;
        public double minimumFps;
        public double maximumFps;
        public double onePercentLowFps;
        public double fivePercentLowFps;
        public double averageFrameTimeMs;
        public double medianFrameTimeMs;
        public double percentile95FrameTimeMs;
        public double bestFrameTimeMs;
        public double worstFrameTimeMs;
        public long lagSpikesTotal;
        public double lagSpikesPerMinute;
        public long severeLagSpikesTotal;
        public double severeLagSpikesPerMinute;
        public long fpsDropsTotal;
        public double fpsDropsPerMinute;
        public long framesBelow60Fps;
        public long framesBelow45Fps;
        public long framesBelow30Fps;
        public double timeBelow60FpsSeconds;
        public double timeBelow45FpsSeconds;
        public double timeBelow30FpsSeconds;
    }
}
