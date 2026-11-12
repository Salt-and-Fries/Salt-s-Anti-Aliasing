package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import org.lwjgl.vulkan.VkPhysicalDeviceDescriptorIndexingFeatures;
import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

final class VulkanDeviceFeatureNormalizerTest {
    @Test
    void fsrOnlyCoreFeaturesDoNotRequireTheDescriptorIndexingUmbrellaBit() {
        var extensionStruct = VulkanFeatureSupport.struct(VkPhysicalDeviceDescriptorIndexingFeatures.class);
        var extensions = new HashSet<>(Set.of("VK_EXT_descriptor_indexing", "VK_KHR_swapchain"));
        var features = new HashSet<>(Set.of(
                VulkanFeatureSupport.feature(VulkanBackend.VK12_FEATURES_STRUCT, "shaderFloat16"),
                VulkanFeatureSupport.feature(extensionStruct, "runtimeDescriptorArray"),
                VulkanFeatureSupport.feature(extensionStruct, "descriptorBindingPartiallyBound")));
        VulkanDeviceFeatureNormalizer.normalize(extensions, features);
        assertEquals(Set.of("VK_KHR_swapchain"), extensions);
        assertTrue(features.contains(VulkanFeatureSupport.feature(VulkanBackend.VK12_FEATURES_STRUCT, "runtimeDescriptorArray")));
        assertTrue(features.contains(VulkanFeatureSupport.feature(VulkanBackend.VK12_FEATURES_STRUCT, "descriptorBindingPartiallyBound")));
        assertFalse(features.stream().anyMatch(f -> f.struct().equals(extensionStruct)));
        assertFalse(features.stream().anyMatch(f -> f.name().equals("descriptorIndexing")));
    }

    @Test
    void anExtensionFeatureStructKeepsItsDeclarationWithoutTheCoreAggregate() {
        var extensions = new HashSet<>(Set.of("VK_EXT_descriptor_indexing"));
        var features = new HashSet<>(Set.of(VulkanFeatureSupport.feature(VulkanFeatureSupport.struct(VkPhysicalDeviceDescriptorIndexingFeatures.class), "runtimeDescriptorArray")));
        VulkanDeviceFeatureNormalizer.normalize(extensions, features);
        assertEquals(Set.of("VK_EXT_descriptor_indexing"), extensions);
    }
}
