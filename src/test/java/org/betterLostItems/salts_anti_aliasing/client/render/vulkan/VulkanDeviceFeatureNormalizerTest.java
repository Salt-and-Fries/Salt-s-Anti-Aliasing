package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import com.mojang.renderpearl.backend.vulkan.init.VulkanPNextStruct;
import org.lwjgl.vulkan.VkPhysicalDeviceDescriptorIndexingFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class VulkanDeviceFeatureNormalizerTest {
    @Test void coreFeatureOffsetsIncludeTheFeatures2Header() {
        var feature = new VulkanFeature(VulkanFeatureSets.VK10_FEATURES_STRUCT, "shaderStorageImageWriteWithoutFormat");
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var features = org.lwjgl.vulkan.VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
            feature.set(features, true, stack);
            assertTrue(features.features().shaderStorageImageWriteWithoutFormat());
            assertTrue(feature.get(features));
            assertFalse(features.features().shaderStorageImageReadWithoutFormat());
        }
    }
    @Test void combinesPromotedBooleansWithoutDroppingTheirMeaning() {
        var extensions = new HashSet<>(Set.of("VK_KHR_buffer_device_address", "VK_EXT_buffer_device_address", "VK_KHR_swapchain"));
        var features = new HashSet<>(Set.of(
                new VulkanFeature(VulkanFeatureSets.VK12_FEATURES_STRUCT, "shaderFloat16"),
                new VulkanFeature(VulkanFeatureSets.VK12_FEATURES_STRUCT, "bufferDeviceAddress"),
                new VulkanFeature(new VulkanPNextStruct(VkPhysicalDeviceDescriptorIndexingFeatures.class), "runtimeDescriptorArray")));
        VulkanDeviceFeatureNormalizer.normalize(extensions, features);
        assertFalse(extensions.contains("VK_EXT_buffer_device_address"));
        assertTrue(extensions.contains("VK_KHR_swapchain"));
        assertEquals(3, features.size());
        assertTrue(features.stream().allMatch(f -> f.struct().pNextStructClass() == VkPhysicalDeviceVulkan12Features.class));
        assertTrue(features.stream().anyMatch(f -> f.name().equals("runtimeDescriptorArray")));
    }
}
