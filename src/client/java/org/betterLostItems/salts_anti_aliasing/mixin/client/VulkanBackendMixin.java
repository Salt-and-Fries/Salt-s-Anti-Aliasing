package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.EXTDescriptorIndexing;
import org.lwjgl.vulkan.EXTSubgroupSizeControl;
import org.lwjgl.vulkan.KHRDedicatedAllocation;
import org.lwjgl.vulkan.KHRGetMemoryRequirements2;
import org.lwjgl.vulkan.KHRShaderFloat16Int8;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkDevice;
import org.lwjgl.vulkan.VkPhysicalDeviceDescriptorIndexingFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures2;
import org.lwjgl.vulkan.VkPhysicalDeviceSubgroupSizeControlFeatures;
import org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Collection;
import java.util.Set;

/**
 * Gives optional native upscalers a chance to initialize before Vulkan handles are created.
 */
@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanBackend")
public abstract class VulkanBackendMixin {
    @Shadow
    private static VkDevice createDevice(
            Collection<String> deviceExtensions,
            VulkanPhysicalDevice physicalDevice,
            Set<VulkanFeature> vulkanFeatures
    ) throws BackendCreationException {
        throw new AssertionError();
    }

    @Inject(method = "createDevice", at = @At("HEAD"))
    private void saltsAntiAliasing$preInitializeDlss(
            long window,
            ShaderSource shaderSource,
            GpuDebugOptions debugOptions,
            Runnable criticalShaderLoader,
            CallbackInfoReturnable<GpuDevice> callbackInfo
    ) throws BackendCreationException {
        DlssRuntime.instance().preInitializeFromEnvironment();
        FsrRuntime.instance().preInitializeFromEnvironment();
    }

    @Redirect(
            method = "createDevice(JLcom/mojang/blaze3d/shaders/ShaderSource;Lcom/mojang/blaze3d/shaders/GpuDebugOptions;Ljava/lang/Runnable;)Lcom/mojang/blaze3d/systems/GpuDevice;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/blaze3d/vulkan/VulkanBackend;createDevice(Ljava/util/Collection;Lcom/mojang/blaze3d/vulkan/VulkanPhysicalDevice;Ljava/util/Set;)Lorg/lwjgl/vulkan/VkDevice;"
            )
    )
    private VkDevice saltsAntiAliasing$createDeviceWithFidelityFxSupport(
            Collection<String> deviceExtensions,
            VulkanPhysicalDevice physicalDevice,
            Set<VulkanFeature> vulkanFeatures
    ) throws BackendCreationException {
        saltsAntiAliasing$enableFidelityFxDeviceSupport(deviceExtensions, physicalDevice, vulkanFeatures);
        return createDevice(deviceExtensions, physicalDevice, vulkanFeatures);
    }

    private static void saltsAntiAliasing$enableFidelityFxDeviceSupport(
            Collection<String> deviceExtensions,
            VulkanPhysicalDevice physicalDevice,
            Set<VulkanFeature> vulkanFeatures
    ) {
        if (physicalDevice.hasDeviceExtension(KHRGetMemoryRequirements2.VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME)) {
            deviceExtensions.add(KHRGetMemoryRequirements2.VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME);

            if (physicalDevice.hasDeviceExtension(KHRDedicatedAllocation.VK_KHR_DEDICATED_ALLOCATION_EXTENSION_NAME)) {
                deviceExtensions.add(KHRDedicatedAllocation.VK_KHR_DEDICATED_ALLOCATION_EXTENSION_NAME);
            }
        }

        if (physicalDevice.hasDeviceExtension(KHRShaderFloat16Int8.VK_KHR_SHADER_FLOAT16_INT8_EXTENSION_NAME)) {
            deviceExtensions.add(KHRShaderFloat16Int8.VK_KHR_SHADER_FLOAT16_INT8_EXTENSION_NAME);
        }
        saltsAntiAliasing$addSupportedFeature(vulkanFeatures, physicalDevice, new VulkanFeature(
                VulkanBackend.VK12_FEATURES_STRUCT,
                "shaderFloat16",
                VkPhysicalDeviceVulkan12Features.SHADERFLOAT16
        ));

        if (physicalDevice.hasDeviceExtension(EXTSubgroupSizeControl.VK_EXT_SUBGROUP_SIZE_CONTROL_EXTENSION_NAME)) {
            deviceExtensions.add(EXTSubgroupSizeControl.VK_EXT_SUBGROUP_SIZE_CONTROL_EXTENSION_NAME);
            VulkanPNextStruct subgroupSizeControlStruct = new VulkanPNextStruct(
                    EXTSubgroupSizeControl.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_SUBGROUP_SIZE_CONTROL_FEATURES_EXT,
                    VkPhysicalDeviceSubgroupSizeControlFeatures.SIZEOF
            );
            saltsAntiAliasing$addSupportedFeature(vulkanFeatures, physicalDevice, new VulkanFeature(
                    subgroupSizeControlStruct,
                    "subgroupSizeControl",
                    VkPhysicalDeviceSubgroupSizeControlFeatures.SUBGROUPSIZECONTROL
            ));
            saltsAntiAliasing$addSupportedFeature(vulkanFeatures, physicalDevice, new VulkanFeature(
                    subgroupSizeControlStruct,
                    "computeFullSubgroups",
                    VkPhysicalDeviceSubgroupSizeControlFeatures.COMPUTEFULLSUBGROUPS
            ));
        }

        if (physicalDevice.hasDeviceExtension(EXTDescriptorIndexing.VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME)) {
            deviceExtensions.add(EXTDescriptorIndexing.VK_EXT_DESCRIPTOR_INDEXING_EXTENSION_NAME);
            VulkanPNextStruct descriptorIndexingStruct = new VulkanPNextStruct(
                    EXTDescriptorIndexing.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DESCRIPTOR_INDEXING_FEATURES_EXT,
                    VkPhysicalDeviceDescriptorIndexingFeatures.SIZEOF
            );
            saltsAntiAliasing$addSupportedFeature(vulkanFeatures, physicalDevice, new VulkanFeature(
                    descriptorIndexingStruct,
                    "shaderStorageBufferArrayNonUniformIndexing",
                    VkPhysicalDeviceDescriptorIndexingFeatures.SHADERSTORAGEBUFFERARRAYNONUNIFORMINDEXING
            ));
            saltsAntiAliasing$addSupportedFeature(vulkanFeatures, physicalDevice, new VulkanFeature(
                    descriptorIndexingStruct,
                    "descriptorBindingPartiallyBound",
                    VkPhysicalDeviceDescriptorIndexingFeatures.DESCRIPTORBINDINGPARTIALLYBOUND
            ));
            saltsAntiAliasing$addSupportedFeature(vulkanFeatures, physicalDevice, new VulkanFeature(
                    descriptorIndexingStruct,
                    "runtimeDescriptorArray",
                    VkPhysicalDeviceDescriptorIndexingFeatures.RUNTIMEDESCRIPTORARRAY
            ));
        }
    }

    private static void saltsAntiAliasing$addSupportedFeature(
            Set<VulkanFeature> vulkanFeatures,
            VulkanPhysicalDevice physicalDevice,
            VulkanFeature feature
    ) {
        if (saltsAntiAliasing$isFeatureSupported(physicalDevice, feature)) {
            vulkanFeatures.add(feature);
        }
    }

    private static boolean saltsAntiAliasing$isFeatureSupported(
            VulkanPhysicalDevice physicalDevice,
            VulkanFeature feature
    ) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VkPhysicalDeviceFeatures2 features = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
            feature.struct().findOrCreateStructInPNextChain(features, stack);
            VK12.vkGetPhysicalDeviceFeatures2(physicalDevice.vkPhysicalDevice(), features);
            return feature.get(features);
        }
    }
}
