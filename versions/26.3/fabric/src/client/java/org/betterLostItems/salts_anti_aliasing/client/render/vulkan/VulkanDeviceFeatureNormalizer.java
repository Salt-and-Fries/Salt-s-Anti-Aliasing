package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan13Features;
import com.mojang.renderpearl.backend.vulkan.init.VulkanPNextStruct;
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
    private static final Set<String> PROMOTED_13 = Set.of(
            "VkPhysicalDeviceImageRobustnessFeatures", "VkPhysicalDeviceInlineUniformBlockFeatures",
            "VkPhysicalDevicePipelineCreationCacheControlFeatures", "VkPhysicalDevicePrivateDataFeatures",
            "VkPhysicalDeviceShaderDemoteToHelperInvocationFeatures", "VkPhysicalDeviceShaderTerminateInvocationFeatures",
            "VkPhysicalDeviceSubgroupSizeControlFeatures", "VkPhysicalDeviceSynchronization2Features",
            "VkPhysicalDeviceTextureCompressionASTCHDRFeatures", "VkPhysicalDeviceZeroInitializeWorkgroupMemoryFeatures",
            "VkPhysicalDeviceDynamicRenderingFeatures", "VkPhysicalDeviceShaderIntegerDotProductFeatures",
            "VkPhysicalDeviceMaintenance4Features");

    public static void normalize(Set<String> extensions, Set<VulkanFeature> features) {
        if (features.stream().anyMatch(f -> f.struct().pNextStructClass() == VkPhysicalDeviceVulkan13Features.class)) {
            var vk13 = new VulkanPNextStruct(VkPhysicalDeviceVulkan13Features.class);
            for (VulkanFeature feature : new HashSet<>(features)) {
                if (!PROMOTED_13.contains(feature.struct().pNextStructClass().getSimpleName())) continue;
                features.remove(feature);
                features.add(new VulkanFeature(vk13, feature.name()));
            }
        }
        boolean aggregate = features.stream().anyMatch(f -> f.struct().pNextStructClass() == VkPhysicalDeviceVulkan12Features.class);
        if (aggregate) {
            for (VulkanFeature feature : new HashSet<>(features)) {
                if (!PROMOTED.contains(feature.struct().pNextStructClass().getSimpleName())) continue;
                features.remove(feature);
                // The same boolean is represented once, in the aggregate, with its new offset.
                features.add(new VulkanFeature(VulkanFeatureSets.VK12_FEATURES_STRUCT, feature.name()));
            }
            // Minecraft requests Vulkan 1.2. The individual descriptor-indexing bits
            // above now use the core struct, so the promoted EXT declaration is redundant.
            // Keeping it would additionally require descriptorIndexing == VK_TRUE
            // (VUID-02833), even when FidelityFX only needs a supported subset of bits.
            // A configured DLSS runtime happened to enable that umbrella bit; FSR-only
            // sessions must also create a valid device without relying on NVIDIA's list.
            extensions.remove("VK_EXT_descriptor_indexing");
        }
        boolean coreAddress = aggregate && features.stream().anyMatch(f -> f.struct().pNextStructClass() == VkPhysicalDeviceVulkan12Features.class
                && f.name().equals("bufferDeviceAddress"));
        if (coreAddress || extensions.contains("VK_KHR_buffer_device_address"))
            extensions.remove("VK_EXT_buffer_device_address");
    }
}
