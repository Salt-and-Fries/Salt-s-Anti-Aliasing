package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VulkanAlphaToCoveragePolicyTest {
    @Test
    void remainsDisabledUnlessTheUserRequestsIt() {
        assertFalse(VulkanAlphaToCoveragePolicy.shouldEnable(false, 8, true, false));
        assertTrue(VulkanAlphaToCoveragePolicy.shouldEnable(true, 8, true, false));
    }

    @Test
    void requiresMultisamplingAndAnOpaqueCutoutPipeline() {
        assertFalse(VulkanAlphaToCoveragePolicy.shouldEnable(true, 1, true, false));
        assertFalse(VulkanAlphaToCoveragePolicy.shouldEnable(true, 8, false, false));
        assertFalse(VulkanAlphaToCoveragePolicy.shouldEnable(true, 8, true, true));
    }
}
