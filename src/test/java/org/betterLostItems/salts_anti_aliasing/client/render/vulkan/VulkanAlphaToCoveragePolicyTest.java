package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void preservesMaterialSpecificCutoutThresholds() {
        Map<String, String> terrainDefines = Map.of("ALPHA_CUTOUT", "0.5", "FOG", "1");
        Map<String, String> entityDefines = Map.of("ALPHA_CUTOUT", "0.1");
        Map<String, String> opaqueDefines = Map.of("FOG", "1");

        assertEquals(terrainDefines, VulkanAlphaToCoveragePolicy.preserveShaderDefines(terrainDefines));
        assertEquals(entityDefines, VulkanAlphaToCoveragePolicy.preserveShaderDefines(entityDefines));
        assertEquals(opaqueDefines, VulkanAlphaToCoveragePolicy.preserveShaderDefines(opaqueDefines));
    }
}
