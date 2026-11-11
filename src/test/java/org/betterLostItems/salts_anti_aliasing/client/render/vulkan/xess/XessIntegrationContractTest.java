package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.xess;

import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import com.mojang.renderpearl.backend.vulkan.init.VulkanPNextStruct;
import org.betterLostItems.salts_anti_aliasing.client.config.*;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanDeviceFeatureNormalizer;
import org.junit.jupiter.api.Test;
import org.lwjgl.vulkan.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class XessIntegrationContractTest {
    @Test void nativeOffsetsResolveToCorrectVulkanFeatureNames() throws Exception {
        var core = XessDeviceRequirements.feature("VkPhysicalDeviceFeatures2",
                VkPhysicalDeviceFeatures2.FEATURES + VkPhysicalDeviceFeatures.SHADERSTORAGEIMAGEWRITEWITHOUTFORMAT);
        assertEquals("shaderStorageImageWriteWithoutFormat", core.name());
        var mutable = XessDeviceRequirements.feature("VkPhysicalDeviceMutableDescriptorTypeFeaturesEXT",
                VkPhysicalDeviceMutableDescriptorTypeFeaturesEXT.MUTABLEDESCRIPTORTYPE);
        assertEquals("mutableDescriptorType", mutable.name());
        var dot = XessDeviceRequirements.feature("VkPhysicalDeviceVulkan13Features",
                VkPhysicalDeviceVulkan13Features.SHADERINTEGERDOTPRODUCT);
        assertEquals("shaderIntegerDotProduct", dot.name());
        assertThrows(IllegalArgumentException.class, () -> XessDeviceRequirements.feature("UnknownStruct", 16));
    }
    @Test void xessVulkan13ChainMergesMinecraftAndFidelityFxPromotedFeatures() {
        Set<VulkanFeature> features = new HashSet<>(List.of(
                new VulkanFeature(new VulkanPNextStruct(VkPhysicalDeviceVulkan13Features.class), "shaderIntegerDotProduct"),
                new VulkanFeature(new VulkanPNextStruct(VkPhysicalDeviceSynchronization2Features.class), "synchronization2"),
                new VulkanFeature(new VulkanPNextStruct(VkPhysicalDeviceDynamicRenderingFeatures.class), "dynamicRendering"),
                new VulkanFeature(new VulkanPNextStruct(VkPhysicalDeviceSubgroupSizeControlFeatures.class), "subgroupSizeControl"),
                new VulkanFeature(new VulkanPNextStruct(VkPhysicalDevicePrivateDataFeatures.class), "privateData")));
        VulkanDeviceFeatureNormalizer.normalize(new HashSet<>(), features);
        assertEquals(5, features.size());
        assertTrue(features.stream().allMatch(f -> f.struct().pNextStructClass() == VkPhysicalDeviceVulkan13Features.class));
    }
    @Test void xessCoverageUsesActualSdkDimensionsIncludingRounding() {
        assertEquals(8, new XessOptimalSettings(1920,1080).jitterPhaseCount(1920,1080));
        assertEquals(72, new XessOptimalSettings(640,360).jitterPhaseCount(1920,1080));
        var odd = new XessOptimalSettings(753,423);
        assertTrue(odd.jitterPhaseCount(1280,720) >= 8.0 * 1280 * 720 / (753.0 * 423));
        assertThrows(IllegalArgumentException.class, () -> new XessOptimalSettings(0,720));
    }
    @Test void loadedXessConfigurationSurvivesCopyAndSanitization() {
        AntiAliasingConfig config = new AntiAliasingConfig();
        config.mode = AntiAliasingMode.XESS_SUPER_RESOLUTION;
        config.xessQualityPreset = XessQualityPreset.NATIVE_AA;
        var copy = config.copy(); copy.sanitize();
        assertEquals(AntiAliasingMode.XESS_SUPER_RESOLUTION, copy.mode);
        assertEquals(1.0f, copy.sceneRenderScale());
        assertTrue(copy.mode.usesHistoryBuffers());
        copy.xessQualityPreset = null; copy.sanitize();
        assertEquals(XessQualityPreset.QUALITY, copy.xessQualityPreset);
        assertEquals(XessQualityPreset.NATIVE_AA, config.xessQualityPreset);
    }
}
