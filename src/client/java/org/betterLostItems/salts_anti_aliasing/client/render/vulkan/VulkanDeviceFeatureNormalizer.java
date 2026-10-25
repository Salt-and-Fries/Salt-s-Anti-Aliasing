package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;
import java.util.HashSet;
import java.util.Set;

/** Promoted feature structs must not coexist with their Vulkan 1.2 aggregate. */
public final class VulkanDeviceFeatureNormalizer {
    private VulkanDeviceFeatureNormalizer() {}
    private static final Set<String> PROMOTED = Set.of(
            "VkPhysicalDevice8BitStorageFeatures", "VkPhysicalDeviceShaderAtomicInt64Features",
            "VkPhysicalDeviceShaderFloat16Int8Features", "VkPhysicalDeviceDescriptorIndexingFeatures",
            "VkPhysicalDeviceScalarBlockLayoutFeatures", "VkPhysicalDeviceImagelessFramebufferFeatures",
            "VkPhysicalDeviceUniformBufferStandardLayoutFeatures", "VkPhysicalDeviceShaderSubgroupExtendedTypesFeatures",
            "VkPhysicalDeviceSeparateDepthStencilLayoutsFeatures", "VkPhysicalDeviceHostQueryResetFeatures",
            "VkPhysicalDeviceTimelineSemaphoreFeatures", "VkPhysicalDeviceBufferDeviceAddressFeatures",
            "VkPhysicalDeviceVulkanMemoryModelFeatures");

    public static void normalize(Set<String> extensions, Set<VulkanFeature> features) {
        boolean aggregate = features.stream().anyMatch(f -> f.struct().pNextStructClass() == VkPhysicalDeviceVulkan12Features.class);
        if (aggregate) {
            for (VulkanFeature feature : new HashSet<>(features)) {
                if (!PROMOTED.contains(feature.struct().pNextStructClass().getSimpleName())) continue;
                features.remove(feature);
                // The same boolean is represented once, in the aggregate, with its new offset.
                features.add(new VulkanFeature(VulkanFeatureSets.VK12_FEATURES_STRUCT, feature.name()));
            }
        }
        boolean coreAddress = aggregate && features.stream().anyMatch(f -> f.struct().pNextStructClass() == VkPhysicalDeviceVulkan12Features.class
                && f.name().equals("bufferDeviceAddress"));
        if (coreAddress || extensions.contains("VK_KHR_buffer_device_address"))
            extensions.remove("VK_EXT_buffer_device_address");
    }
}
