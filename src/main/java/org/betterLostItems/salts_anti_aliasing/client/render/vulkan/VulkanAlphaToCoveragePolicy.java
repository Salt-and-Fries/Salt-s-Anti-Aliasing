package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import java.util.Map;

/**
 * Decides whether a pipeline can replace binary alpha testing with Vulkan alpha-to-coverage.
 */
public final class VulkanAlphaToCoveragePolicy {
    private VulkanAlphaToCoveragePolicy() {
    }

    public static boolean shouldEnable(
            int samples,
            boolean hasAlphaCutout,
            boolean hasBlendedColorTarget
    ) {
        return samples > 1 && hasAlphaCutout && !hasBlendedColorTarget;
    }

    /**
     * Copies a pipeline's shader values without changing its material-specific alpha cutoff.
     */
    public static Map<String, String> preserveShaderDefines(Map<String, String> shaderDefines) {
        return Map.copyOf(shaderDefines);
    }
}
