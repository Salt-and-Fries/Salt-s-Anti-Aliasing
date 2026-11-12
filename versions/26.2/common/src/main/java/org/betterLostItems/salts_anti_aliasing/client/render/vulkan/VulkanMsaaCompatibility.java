package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

/**
 * Pure validation helpers for the Vulkan MSAA path.
 *
 * <p>Keeping these checks independent of LWJGL lets the renderer reject incompatible attachment
 * and resolve combinations before recording an invalid Vulkan command.</p>
 */
public final class VulkanMsaaCompatibility {
    private static final int MAX_VULKAN_SAMPLE_COUNT = 64;

    private VulkanMsaaCompatibility() {
    }

    public static int commonAttachmentSampleCount(int... sampleCounts) {
        int commonSamples = 1;
        boolean foundAttachment = false;

        for (int samples : sampleCounts) {
            requireValidSampleCount(samples, "render-pass attachment");
            if (!foundAttachment) {
                commonSamples = samples;
                foundAttachment = true;
            } else if (samples != commonSamples) {
                throw new IllegalStateException(
                        "Vulkan render-pass attachments use incompatible sample counts: "
                                + commonSamples + "x and " + samples + "x"
                );
            }
        }

        return commonSamples;
    }

    public static int bestSupportedSampleCount(int supportedSampleMask, int requestedSamples) {
        int requested = Math.min(MAX_VULKAN_SAMPLE_COUNT, Math.max(1, requestedSamples));
        int candidate = Integer.highestOneBit(requested);
        while (candidate > 1) {
            if ((supportedSampleMask & candidate) != 0) {
                return candidate;
            }
            candidate >>= 1;
        }
        return 1;
    }

    public static void validateColorResolve(
            int sourceSamples,
            int destinationSamples,
            int sourceWidth,
            int sourceHeight,
            int destinationWidth,
            int destinationHeight,
            boolean formatsMatch
    ) {
        requireValidSampleCount(sourceSamples, "resolve source");
        requireValidSampleCount(destinationSamples, "resolve destination");

        if (sourceSamples <= 1) {
            throw new IllegalStateException("Vulkan MSAA resolve source must be multisampled");
        }
        if (destinationSamples != 1) {
            throw new IllegalStateException("Vulkan MSAA resolve destination must be single-sampled");
        }
        if (!formatsMatch) {
            throw new IllegalStateException("Vulkan MSAA resolve source and destination formats differ");
        }
        if (sourceWidth <= 0 || sourceHeight <= 0
                || sourceWidth != destinationWidth || sourceHeight != destinationHeight) {
            throw new IllegalStateException(
                    "Vulkan MSAA resolve extents differ: "
                            + sourceWidth + "x" + sourceHeight + " and "
                            + destinationWidth + "x" + destinationHeight
            );
        }
    }

    private static void requireValidSampleCount(int samples, String role) {
        if (samples < 1 || samples > MAX_VULKAN_SAMPLE_COUNT || (samples & (samples - 1)) != 0) {
            throw new IllegalArgumentException(
                    "Invalid Vulkan " + role + " sample count: " + samples
            );
        }
    }
}
