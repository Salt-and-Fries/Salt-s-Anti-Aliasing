package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import com.mojang.renderpearl.backend.vulkan.init.VulkanPNextStruct;
import org.lwjgl.vulkan.VkPhysicalDeviceDescriptorIndexingFeatures;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class VulkanDeviceFeatureNormalizerTest {
    @Test
    void fsrOnlyCoreFeaturesDoNotRequireTheDescriptorIndexingUmbrellaBit() {
        var extensionStruct = new VulkanPNextStruct(VkPhysicalDeviceDescriptorIndexingFeatures.class);
        var extensions = new HashSet<>(Set.of("VK_EXT_descriptor_indexing", "VK_KHR_swapchain"));
        var features = new HashSet<>(Set.of(
                new VulkanFeature(VulkanFeatureSets.VK12_FEATURES_STRUCT, "shaderFloat16"),
                new VulkanFeature(extensionStruct, "runtimeDescriptorArray"),
                new VulkanFeature(extensionStruct, "descriptorBindingPartiallyBound")));
        VulkanDeviceFeatureNormalizer.normalize(extensions, features);
        assertEquals(Set.of("VK_KHR_swapchain"), extensions);
        assertTrue(features.contains(new VulkanFeature(VulkanFeatureSets.VK12_FEATURES_STRUCT, "runtimeDescriptorArray")));
        assertTrue(features.contains(new VulkanFeature(VulkanFeatureSets.VK12_FEATURES_STRUCT, "descriptorBindingPartiallyBound")));
        assertFalse(features.stream().anyMatch(f -> f.struct().equals(extensionStruct)));
        assertFalse(features.stream().anyMatch(f -> f.name().equals("descriptorIndexing")));
    }

    @Test
    void anExtensionFeatureStructKeepsItsDeclarationWithoutTheCoreAggregate() {
        var extensions = new HashSet<>(Set.of("VK_EXT_descriptor_indexing"));
        var features = new HashSet<>(Set.of(new VulkanFeature(
                new VulkanPNextStruct(VkPhysicalDeviceDescriptorIndexingFeatures.class), "runtimeDescriptorArray")));
        VulkanDeviceFeatureNormalizer.normalize(extensions, features);
        assertEquals(Set.of("VK_EXT_descriptor_indexing"), extensions);
    }
}
