package org.betterLostItems.salts_anti_aliasing.client.debug;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Screenshot;
import net.minecraft.util.ARGB;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;

/**
 * Samples rendered frames to estimate edge strength and aliasing behavior for the optional edge-
 * debug HUD.
 */
public final class EdgeDebugAnalyzer {
    private static final int ANALYSIS_INTERVAL_FRAMES = 12;
    private static final float EDGE_SIGNAL_THRESHOLD = 0.08f;
    private static final float HARSH_SMOOTHNESS_THRESHOLD = 0.28f;

    private EdgeDebugStats latestStats = EdgeDebugStats.unavailable(AntiAliasingMode.OFF);
    private boolean debugActive;
    private boolean capturePending;
    private int framesUntilCapture;
    private int analysisGeneration;

    /**
     * Handles latest stats as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return latest edge-debug statistics snapshot
     */
    public EdgeDebugStats latestStats() {
        return latestStats;
    }

    /**
     * Handles reset as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     */
    public void reset(AntiAliasingMode mode) {
        analysisGeneration++;
        capturePending = false;
        framesUntilCapture = 0;
        latestStats = EdgeDebugStats.unavailable(mode);
    }

    /**
     * Coordinates capture if needed within the anti-aliasing render, configuration, or compatibility flow.
     * @param mainTarget main target value supplied by the caller or Minecraft callback
     * @param config configuration object being normalized, copied, or committed
     */
    public void captureIfNeeded(RenderTarget mainTarget, AntiAliasingConfig config) {
        if (!config.debugViewsEnabled) {
            if (debugActive) {
                debugActive = false;
                reset(config.mode);
            }
            return;
        }

        debugActive = true;
        if (latestStats.mode() != config.mode) {
            reset(config.mode);
        }

        if (capturePending || mainTarget.getColorTexture() == null || mainTarget.width <= 2 || mainTarget.height <= 2) {
            return;
        }

        framesUntilCapture++;
        if (framesUntilCapture < ANALYSIS_INTERVAL_FRAMES) {
            return;
        }

        framesUntilCapture = 0;
        capturePending = true;
        int generation = analysisGeneration;
        int downscaleFactor = chooseDownscaleFactor(mainTarget.width, mainTarget.height);
        AntiAliasingMode mode = config.mode;

        Screenshot.takeScreenshot(mainTarget, downscaleFactor, image -> {
            try (image) {
                if (generation == analysisGeneration) {
                    latestStats = analyze(image, mode);
                }
            } catch (RuntimeException exception) {
                SaltsAntiAliasing.LOGGER.warn("Failed to analyze Salt's Anti Aliasing edge debug frame", exception);
            } finally {
                if (generation == analysisGeneration) {
                    capturePending = false;
                }
            }
        });
    }

    /**
     * Handles analyze as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param image image value supplied by the caller or Minecraft callback
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @return statistics extracted from the sampled frame buffer
     */
    private static EdgeDebugStats analyze(NativeImage image, AntiAliasingMode mode) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (width <= 2 || height <= 2) {
            return EdgeDebugStats.unavailable(mode);
        }

        int totalPixels = 0;
        int edgePixels = 0;
        int harshPixels = 0;
        float smoothAccumulator = 0.0f;

        for (int y = 1; y < height - 1; y++) {
            for (int x = 1; x < width - 1; x++) {
                float center = luma(image.getPixel(x, y));
                float north = luma(image.getPixel(x, y - 1));
                float south = luma(image.getPixel(x, y + 1));
                float west = luma(image.getPixel(x - 1, y));
                float east = luma(image.getPixel(x + 1, y));
                float northWest = luma(image.getPixel(x - 1, y - 1));
                float northEast = luma(image.getPixel(x + 1, y - 1));
                float southWest = luma(image.getPixel(x - 1, y + 1));
                float southEast = luma(image.getPixel(x + 1, y + 1));

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
    private static int chooseDownscaleFactor(int width, int height) {
        int[] preferredFactors = {4, 3, 2, 1};
        for (int factor : preferredFactors) {
            if (width % factor == 0 && height % factor == 0) {
                return factor;
            }
        }
        return 1;
    }

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
        float red = ARGB.red(argb) / 255.0f;
        float green = ARGB.green(argb) / 255.0f;
        float blue = ARGB.blue(argb) / 255.0f;
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
