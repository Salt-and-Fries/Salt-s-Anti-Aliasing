package org.betterLostItems.salts_anti_aliasing.client.debug;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
import java.nio.ByteBuffer;

/** Analyzes a completed GPU readback without opening another command buffer or waiting mid-frame. */
public final class EdgeDebugAnalyzer {
    private static final float EDGE_SIGNAL_THRESHOLD = .08f;
    private static final float HARSH_SMOOTHNESS_THRESHOLD = .28f;
    private EdgeDebugStats latestStats = EdgeDebugStats.unavailable(AntiAliasingMode.OFF);
    public EdgeDebugStats latestStats() { return latestStats; }
    public void reset(AntiAliasingMode mode) { latestStats = EdgeDebugStats.unavailable(mode); }
    public void acceptRgba(ByteBuffer rgba, int width, int height, AntiAliasingMode mode) {
        if (rgba.remaining() < Math.multiplyExact(Math.multiplyExact(width, height), 4))
            throw new IllegalArgumentException("Edge-analysis readback is too short");
        int base = rgba.position();
        latestStats = analyze((x, y) -> {
            int offset = base + (y * width + x) * 4;
            return (rgba.get(offset + 3) & 255) << 24 | (rgba.get(offset) & 255) << 16
                    | (rgba.get(offset + 1) & 255) << 8 | rgba.get(offset + 2) & 255;
        }, width, height, mode);
        MetricsDiagnostics.info("edge_debug", "frame_analyzed", "mode", mode, "sampleWidth", width,
                "sampleHeight", height, "qualityScore", latestStats.qualityScore(), "edgeCoverage", latestStats.edgeCoverage());
    }
    private static EdgeDebugStats analyze(java.util.function.IntBinaryOperator pixels, int width, int height, AntiAliasingMode mode) {


        if (width <= 2 || height <= 2) {
            return EdgeDebugStats.unavailable(mode);
        }

        int totalPixels = 0;
        int edgePixels = 0;
        int harshPixels = 0;
        float smoothAccumulator = 0.0f;

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                float center = luma(pixels.applyAsInt(x, y));
                float north = luma(pixels.applyAsInt(x, y - 1));
                float south = luma(pixels.applyAsInt(x, y + 1));
                float west = luma(pixels.applyAsInt(x - 1, y));
                float east = luma(pixels.applyAsInt(x + 1, y));
                float northWest = luma(pixels.applyAsInt(x - 1, y - 1));
                float northEast = luma(pixels.applyAsInt(x + 1, y - 1));
                float southWest = luma(pixels.applyAsInt(x - 1, y + 1));
                float southEast = luma(pixels.applyAsInt(x + 1, y + 1));

                float minLuma = min(center, north, south, west, east, northWest, northEast, southWest, southEast);
                float maxLuma = max(center, north, south, west, east, northWest, northEast, southWest, southEast);
                float range = maxLuma - minLuma;

                float gradientX = Math.abs((northEast + 2.0f * east + southEast) - (northWest + 2.0f * west + southWest)) * 0.25f;
                float gradientY = Math.abs((southWest + 2.0f * south + southEast) - (northWest + 2.0f * north + northEast)) * 0.25f;
                float edgeSignal = Math.max(range, (float) Math.sqrt(gradientX * gradientX + gradientY * gradientY));
                totalPixels++;

                if (edgeSignal < EDGE_SIGNAL_THRESHOLD) {
                    continue;
                }

                edgePixels++;
                float neighborAverage = (north + south + west + east + northWest + northEast + southWest + southEast) / 8.0f;
                float transitionBalance = 1.0f - clamp(Math.abs(center - neighborAverage) / Math.max(range, 0.02f), 0.0f, 1.0f);

                int intermediateNeighbors = 0;
                float lowerBand = minLuma + range * 0.2f;
                float upperBand = maxLuma - range * 0.2f;
                intermediateNeighbors += isIntermediate(north, lowerBand, upperBand);
                intermediateNeighbors += isIntermediate(south, lowerBand, upperBand);
                intermediateNeighbors += isIntermediate(west, lowerBand, upperBand);
                intermediateNeighbors += isIntermediate(east, lowerBand, upperBand);
                intermediateNeighbors += isIntermediate(northWest, lowerBand, upperBand);
                intermediateNeighbors += isIntermediate(northEast, lowerBand, upperBand);
                intermediateNeighbors += isIntermediate(southWest, lowerBand, upperBand);
                intermediateNeighbors += isIntermediate(southEast, lowerBand, upperBand);

                float transitionWidth = intermediateNeighbors / 8.0f;
                float smoothness = clamp(transitionBalance * 0.58f + transitionWidth * 0.42f, 0.0f, 1.0f);
                smoothAccumulator += smoothness;

                if (smoothness < HARSH_SMOOTHNESS_THRESHOLD && edgeSignal > 0.14f) {
                    harshPixels++;
                }
            }
        }

        if (edgePixels == 0) {
            return new EdgeDebugStats(mode, 0.0f, 0.0f, 1.0f, 100);
        }

        float edgeCoverage = edgePixels / (float) Math.max(totalPixels, 1);
        float harshRatio = harshPixels / (float) edgePixels;
        float smoothRatio = smoothAccumulator / edgePixels;
        int qualityScore = Math.round(clamp((smoothRatio * 0.72f + (1.0f - harshRatio) * 0.28f) * 100.0f, 0.0f, 100.0f));
        return new EdgeDebugStats(mode, edgeCoverage, harshRatio, smoothRatio, qualityScore);
    }

    /**
     * Coordinates choose downscale factor within the anti-aliasing render, configuration, or compatibility flow.
     * @param width width value supplied by the caller or Minecraft callback
     * @param height height value supplied by the caller or Minecraft callback
     * @return sampling divisor chosen to keep readback cost bounded
     */

    /**
     * Checks is intermediate without mutating runtime or configuration state.
     * @param sample sample value supplied by the caller or Minecraft callback
     * @param lowerBand lower band value supplied by the caller or Minecraft callback
     * @param upperBand upper band value supplied by the caller or Minecraft callback
     * @return whether the requested condition is true
     */
    private static int isIntermediate(float sample, float lowerBand, float upperBand) {
        return sample > lowerBand && sample < upperBand ? 1 : 0;
    }

    /**
     * Handles luma as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param argb argb value supplied by the caller or Minecraft callback
     * @return perceived brightness used for edge and variance calculations
     */
    private static float luma(int argb) {
        float red = (argb >>> 16 & 255) / 255.0f;
        float green = (argb >>> 8 & 255) / 255.0f;
        float blue = (argb & 255) / 255.0f;
        return red * 0.299f + green * 0.587f + blue * 0.114f;
    }

    /**
     * Clamps the supplied value to an inclusive range before it can affect rendering or persisted configuration.
     * @param value value supplied by the caller or Minecraft callback
     * @param min min value supplied by the caller or Minecraft callback
     * @param max max value supplied by the caller or Minecraft callback
     * @return value clamped to the supported range
     */
    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    /**
     * Handles min as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param values values value supplied by the caller or Minecraft callback
     * @return smallest value in the supplied sample set
     */
    private static float min(float... values) {
        float result = values[0];
        for (int i = 1; i < values.length; i++) {
            result = Math.min(result, values[i]);
        }
        return result;
    }

    /**
     * Handles max as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param values values value supplied by the caller or Minecraft callback
     * @return largest value in the supplied sample set
     */
    private static float max(float... values) {
        float result = values[0];
        for (int i = 1; i < values.length; i++) {
            result = Math.max(result, values[i]);
        }
        return result;
    }
}
