package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

/**
 * Restricts optional alpha-to-coverage to opaque cutout pipelines in a multisampled pass.
 */
public final class VulkanAlphaToCoveragePolicy {
    private VulkanAlphaToCoveragePolicy() {
    }

    public static boolean shouldEnable(
            boolean requested,
            int samples,
            boolean hasAlphaCutout,
            boolean hasBlendedColorTarget
    ) {
        return requested && samples > 1 && hasAlphaCutout && !hasBlendedColorTarget;
    }
}
