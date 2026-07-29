package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanAlphaToCoveragePolicyTest {
    @Test
    void enablesOpaqueCutoutsOnlyWhenMultisampled() {
        assertFalse(VulkanAlphaToCoveragePolicy.shouldEnable(1, true, false));
        assertTrue(VulkanAlphaToCoveragePolicy.shouldEnable(2, true, false));
        assertTrue(VulkanAlphaToCoveragePolicy.shouldEnable(4, true, false));
        assertTrue(VulkanAlphaToCoveragePolicy.shouldEnable(8, true, false));
        assertTrue(VulkanAlphaToCoveragePolicy.shouldEnable(16, true, false));
    }

    @Test
    void excludesPipelinesWithoutCutoutOrWithBlending() {
        assertFalse(VulkanAlphaToCoveragePolicy.shouldEnable(4, false, false));
        assertFalse(VulkanAlphaToCoveragePolicy.shouldEnable(4, true, true));
        assertFalse(VulkanAlphaToCoveragePolicy.shouldEnable(4, false, true));
    }
}
