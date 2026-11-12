package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan13Features;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import java.util.HashSet;
import java.util.Set;

/** Promoted feature structs must not coexist with their Vulkan 1.2 aggregate. */
public final class VulkanDeviceFeatureNormalizer {
    private VulkanDeviceFeatureNormalizer() {}
    private static final Set<VulkanPNextStruct> PROMOTED = Set.of(
            "VkPhysicalDevice8BitStorageFeatures", "VkPhysicalDeviceShaderAtomicInt64Features",
            "VkPhysicalDeviceShaderFloat16Int8Features", "VkPhysicalDeviceDescriptorIndexingFeatures",
            "VkPhysicalDeviceScalarBlockLayoutFeatures", "VkPhysicalDeviceImagelessFramebufferFeatures",
            "VkPhysicalDeviceUniformBufferStandardLayoutFeatures", "VkPhysicalDeviceShaderSubgroupExtendedTypesFeatures",
            "VkPhysicalDeviceSeparateDepthStencilLayoutsFeatures", "VkPhysicalDeviceHostQueryResetFeatures",
            "VkPhysicalDeviceTimelineSemaphoreFeatures", "VkPhysicalDeviceBufferDeviceAddressFeatures",
            "VkPhysicalDeviceVulkanMemoryModelFeatures").stream().map(VulkanFeatureSupport::struct).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Set<VulkanPNextStruct> PROMOTED_13 = Set.of(
            "VkPhysicalDeviceImageRobustnessFeatures", "VkPhysicalDeviceInlineUniformBlockFeatures",
            "VkPhysicalDevicePipelineCreationCacheControlFeatures", "VkPhysicalDevicePrivateDataFeatures",
            "VkPhysicalDeviceShaderDemoteToHelperInvocationFeatures", "VkPhysicalDeviceShaderTerminateInvocationFeatures",
            "VkPhysicalDeviceSubgroupSizeControlFeatures", "VkPhysicalDeviceSynchronization2Features",
            "VkPhysicalDeviceTextureCompressionASTCHDRFeatures", "VkPhysicalDeviceZeroInitializeWorkgroupMemoryFeatures",
            "VkPhysicalDeviceDynamicRenderingFeatures", "VkPhysicalDeviceShaderIntegerDotProductFeatures",
            "VkPhysicalDeviceMaintenance4Features").stream().map(VulkanFeatureSupport::struct).collect(java.util.stream.Collectors.toUnmodifiableSet());

    public static void normalize(Set<String> extensions, Set<VulkanFeature> features) {
        if (features.stream().anyMatch(f -> f.struct().equals(VulkanFeatureSupport.struct(VkPhysicalDeviceVulkan13Features.class)))) {
            var vk13 = VulkanFeatureSupport.struct(VkPhysicalDeviceVulkan13Features.class);
            for (VulkanFeature feature : new HashSet<>(features)) {
                if (!PROMOTED_13.contains(feature.struct())) continue;
                features.remove(feature);
                features.add(VulkanFeatureSupport.feature(vk13, feature.name()));
            }
        }
        boolean aggregate = features.stream().anyMatch(f -> f.struct().equals(VulkanFeatureSupport.struct(VkPhysicalDeviceVulkan12Features.class)));
        if (aggregate) {
            for (VulkanFeature feature : new HashSet<>(features)) {
                if (!PROMOTED.contains(feature.struct())) continue;
                features.remove(feature);
                // The same boolean is represented once, in the aggregate, with its new offset.
                features.add(VulkanFeatureSupport.feature(VulkanBackend.VK12_FEATURES_STRUCT, feature.name()));
            }
            // Minecraft requests Vulkan 1.2. The individual descriptor-indexing bits
            // above now use the core struct, so the promoted EXT declaration is redundant.
            // Keeping it would additionally require descriptorIndexing == VK_TRUE
            // (VUID-02833), even when FidelityFX only needs a supported subset of bits.
            // A configured DLSS runtime happened to enable that umbrella bit; FSR-only
            // sessions must also create a valid device without relying on NVIDIA's list.
            extensions.remove("VK_EXT_descriptor_indexing");
        }
        boolean coreAddress = aggregate && features.stream().anyMatch(f -> f.struct().equals(VulkanFeatureSupport.struct(VkPhysicalDeviceVulkan12Features.class))
                && f.name().equals("bufferDeviceAddress"));
        if (coreAddress || extensions.contains("VK_KHR_buffer_device_address"))
            extensions.remove("VK_EXT_buffer_device_address");
    }
}
