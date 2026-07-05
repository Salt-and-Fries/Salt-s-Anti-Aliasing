package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import java.util.function.Supplier;

/**
 * Render-thread scoped Vulkan MSAA state read by mixins that patch Minecraft's Vulkan internals.
 */
public final class VulkanMsaaState {
    private static final ThreadLocal<Integer> TEXTURE_SAMPLE_COUNT = ThreadLocal.withInitial(() -> 1);
    private static final ThreadLocal<Integer> PIPELINE_SAMPLE_COUNT = ThreadLocal.withInitial(() -> 1);

    private VulkanMsaaState() {
    }

    static <T> T withTextureSampleCount(int samples, Supplier<T> action) {
        int previousSamples = TEXTURE_SAMPLE_COUNT.get();
        TEXTURE_SAMPLE_COUNT.set(sanitize(samples));
        try {
            return action.get();
        } finally {
            TEXTURE_SAMPLE_COUNT.set(previousSamples);
        }
    }

    public static int textureSampleCount(int originalSamples) {
        int samples = TEXTURE_SAMPLE_COUNT.get();
        return samples > 1 ? samples : originalSamples;
    }

    static void setPipelineSampleCount(int samples) {
        PIPELINE_SAMPLE_COUNT.set(sanitize(samples));
    }

    static void clearPipelineSampleCount() {
        PIPELINE_SAMPLE_COUNT.set(1);
    }

    public static int pipelineSampleCount(int originalSamples) {
        int samples = PIPELINE_SAMPLE_COUNT.get();
        return samples > 1 ? samples : originalSamples;
    }

    private static int sanitize(int samples) {
        return Math.max(1, samples);
    }
}
