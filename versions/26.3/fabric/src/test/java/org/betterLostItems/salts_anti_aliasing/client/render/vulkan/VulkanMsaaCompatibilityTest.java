package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VulkanMsaaCompatibilityTest {
    @Test
    void acceptsUniformRenderPassSampleCounts() {
        assertEquals(1, VulkanMsaaCompatibility.commonAttachmentSampleCount());
        assertEquals(1, VulkanMsaaCompatibility.commonAttachmentSampleCount(1, 1));
        assertEquals(2, VulkanMsaaCompatibility.commonAttachmentSampleCount(2, 2));
        assertEquals(4, VulkanMsaaCompatibility.commonAttachmentSampleCount(4, 4));
        assertEquals(8, VulkanMsaaCompatibility.commonAttachmentSampleCount(8, 8));
        assertEquals(16, VulkanMsaaCompatibility.commonAttachmentSampleCount(16, 16));
    }

    @Test
    void rejectsMixedRenderPassSampleCounts() {
        assertThrows(
                IllegalStateException.class,
                () -> VulkanMsaaCompatibility.commonAttachmentSampleCount(4, 1)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> VulkanMsaaCompatibility.commonAttachmentSampleCount(3)
        );
    }

    @Test
    void selectsFallbackFromNonContiguousCapabilityMask() {
        int supportedSamples = 2 | 8;
        assertEquals(8, VulkanMsaaCompatibility.bestSupportedSampleCount(supportedSamples, 16));
        assertEquals(2, VulkanMsaaCompatibility.bestSupportedSampleCount(supportedSamples, 4));
        assertEquals(1, VulkanMsaaCompatibility.bestSupportedSampleCount(supportedSamples, 1));
        assertEquals(1, VulkanMsaaCompatibility.bestSupportedSampleCount(1, 16));
    }

    @Test
    void acceptsOnlyMultisampleToSingleSampleMatchingResolves() {
        for (int samples : new int[]{2, 4, 8, 16}) {
            VulkanMsaaCompatibility.validateColorResolve(
                    samples, 1, 1920, 1080, 1920, 1080, true
            );
        }

        assertThrows(
                IllegalStateException.class,
                () -> VulkanMsaaCompatibility.validateColorResolve(1, 1, 1, 1, 1, 1, true)
        );
        assertThrows(
                IllegalStateException.class,
                () -> VulkanMsaaCompatibility.validateColorResolve(4, 4, 1, 1, 1, 1, true)
        );
        assertThrows(
                IllegalStateException.class,
                () -> VulkanMsaaCompatibility.validateColorResolve(4, 1, 1, 1, 2, 1, true)
        );
        assertThrows(
                IllegalStateException.class,
                () -> VulkanMsaaCompatibility.validateColorResolve(4, 1, 1, 1, 1, 1, false)
        );
    }
}
