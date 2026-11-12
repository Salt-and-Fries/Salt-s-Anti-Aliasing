package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class VulkanFeatureSupportTest {
    @Test
    void coreSampleShadingWritesTheInlineFeaturesMember() {
        var feature = VulkanFeatureSupport.feature(
                VulkanFeatureSupport.struct(VkPhysicalDeviceFeatures.class), "sampleRateShading");
        assertEquals(VkPhysicalDeviceFeatures2.FEATURES + VkPhysicalDeviceFeatures.SAMPLERATESHADING, feature.offset());
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var features = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
            feature.set(features, true, stack);
            assertTrue(features.features().sampleRateShading());
            assertTrue(feature.get(features));
        }
    }

    @Test
    void promotedFeaturesWriteTheirVulkan13AggregateOffsets() {
        var synchronization = VulkanFeatureSupport.feature(
                VulkanFeatureSupport.struct(VkPhysicalDeviceSynchronization2Features.class), "synchronization2");
        Set<com.mojang.blaze3d.vulkan.init.VulkanFeature> enabled = new HashSet<>(Set.of(
                synchronization, VulkanFeatureSupport.feature(
                        VulkanFeatureSupport.struct(VkPhysicalDeviceVulkan13Features.class), "shaderIntegerDotProduct")));
        VulkanDeviceFeatureNormalizer.normalize(new HashSet<>(), enabled);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var features = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
            for (var feature : enabled) feature.set(features, true, stack);
            var aggregate = VkPhysicalDeviceVulkan13Features.create(features.pNext());
            assertTrue(aggregate.synchronization2());
            assertTrue(aggregate.shaderIntegerDotProduct());
        }
    }
}
