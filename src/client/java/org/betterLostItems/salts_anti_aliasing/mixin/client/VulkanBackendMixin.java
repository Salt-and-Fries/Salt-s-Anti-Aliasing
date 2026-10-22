package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.renderpearl.api.device.GpuDebugOptions;
import com.mojang.renderpearl.api.device.BackendCreationException;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanPhysicalDevice;
import com.mojang.renderpearl.backend.vulkan.VulkanFeatureSets;
import com.mojang.renderpearl.backend.vulkan.init.FeatureSet;
import com.mojang.renderpearl.backend.vulkan.init.VulkanFeature;
import com.mojang.renderpearl.backend.vulkan.init.VulkanPNextStruct;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
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
import java.util.HashSet;
import java.util.Set;

/**
 * Gives optional native upscalers a chance to initialize before Vulkan handles are created.
 */
@Mixin(targets = "com.mojang.renderpearl.backend.vulkan.VulkanBackend")
public abstract class VulkanBackendMixin {
    @Shadow
    private static VkDevice createDevice(
            FeatureSet featureSet,
            VulkanPhysicalDevice physicalDevice
    ) throws BackendCreationException {
        throw new AssertionError();
    }

    @Inject(method = "createDevice(Lcom/mojang/renderpearl/api/device/GpuDebugOptions;)Lcom/mojang/renderpearl/api/device/GpuDevice;", at = @At("HEAD"))
    private void saltsAntiAliasing$preInitializeDlss(
            GpuDebugOptions debugOptions,
            CallbackInfoReturnable<GpuDevice> callbackInfo
    ) throws BackendCreationException {
        MetricsDiagnostics.info(
                "vulkan.device",
                "native_upscaler_preinitialization",
                "gpuDebugOptions", debugOptions
        );
        DlssRuntime.instance().preInitializeFromEnvironment();
        FsrRuntime.instance().preInitializeFromEnvironment();
    }

    @Redirect(
            method = "createDevice(Lcom/mojang/renderpearl/api/device/GpuDebugOptions;)Lcom/mojang/renderpearl/api/device/GpuDevice;",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mojang/renderpearl/backend/vulkan/VulkanBackend;createDevice(Lcom/mojang/renderpearl/backend/vulkan/init/FeatureSet;Lcom/mojang/renderpearl/backend/vulkan/VulkanPhysicalDevice;)Lorg/lwjgl/vulkan/VkDevice;"
            )
    )
    private VkDevice saltsAntiAliasing$createDeviceWithFidelityFxSupport(
            FeatureSet featureSet,
            VulkanPhysicalDevice physicalDevice
    ) throws BackendCreationException {
        Set<String> deviceExtensions = new HashSet<>(featureSet.extensions());
        Set<VulkanFeature> vulkanFeatures = new HashSet<>(featureSet.features());
        saltsAntiAliasing$enableFidelityFxDeviceSupport(deviceExtensions, physicalDevice, vulkanFeatures);
        saltsAntiAliasing$enableDlssDeviceSupport(deviceExtensions, physicalDevice, vulkanFeatures);
        return createDevice(new FeatureSet(
                featureSet.name(), Set.copyOf(deviceExtensions), Set.copyOf(vulkanFeatures), featureSet.condition()
        ), physicalDevice);
    }

    private static void saltsAntiAliasing$enableFidelityFxDeviceSupport(
            Collection<String> deviceExtensions,
            VulkanPhysicalDevice physicalDevice,
            Set<VulkanFeature> vulkanFeatures
    ) {
        Set<String> extensionsBefore = Set.copyOf(deviceExtensions);
        Set<VulkanFeature> featuresBefore = Set.copyOf(vulkanFeatures);
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
                VulkanFeatureSets.VK12_FEATURES_STRUCT,
                "shaderFloat16",
                VkPhysicalDeviceVulkan12Features.SHADERFLOAT16
        ));

        if (physicalDevice.hasDeviceExtension(EXTSubgroupSizeControl.VK_EXT_SUBGROUP_SIZE_CONTROL_EXTENSION_NAME)) {
            deviceExtensions.add(EXTSubgroupSizeControl.VK_EXT_SUBGROUP_SIZE_CONTROL_EXTENSION_NAME);
            VulkanPNextStruct subgroupSizeControlStruct = new VulkanPNextStruct(
                    VkPhysicalDeviceSubgroupSizeControlFeatures.class
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
                    VkPhysicalDeviceDescriptorIndexingFeatures.class
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
        MetricsDiagnostics.info(
                "vulkan.device",
                "fidelityfx_device_requirements_evaluated",
                "deviceName", physicalDevice.deviceName(),
                "vendorName", physicalDevice.vendorName(),
                "driverInfo", physicalDevice.driverInfo(),
                "extensionsBefore", extensionsBefore,
                "extensionsAfter", deviceExtensions,
                "featuresBefore", featuresBefore,
                "featuresAfter", vulkanFeatures
        );
    }

    private static void saltsAntiAliasing$enableDlssDeviceSupport(
            Set<String> extensions, VulkanPhysicalDevice physicalDevice, Set<VulkanFeature> features) {
        var runtime = DlssRuntime.instance();
        for (String requirement : runtime.vulkanRequirements()) {
            if (requirement.startsWith("device:")) {
                String name = requirement.substring(7);
                if (physicalDevice.hasDeviceExtension(name)) extensions.add(name);
                else runtime.rejectVulkanRequirements("Missing device extension " + name);
            } else if (requirement.startsWith("12:")) {
                String name = requirement.substring(3);
                try {
                    int offset = VkPhysicalDeviceVulkan12Features.class.getField(name.toUpperCase(java.util.Locale.ROOT)).getInt(null);
                    VulkanFeature feature = new VulkanFeature(VulkanFeatureSets.VK12_FEATURES_STRUCT, name, offset);
                    if (saltsAntiAliasing$isFeatureSupported(physicalDevice, feature)) features.add(feature);
                    else runtime.rejectVulkanRequirements("Missing Vulkan 1.2 feature " + name);
                } catch (ReflectiveOperationException exception) {
                    runtime.rejectVulkanRequirements("Unknown Vulkan feature " + name);
                }
            } else if (requirement.startsWith("13:")) {
                // RenderPearl 26.3 creates a Vulkan 1.2 device. Do not claim a newer feature is enabled.
                runtime.rejectVulkanRequirements("The configured DLSS runtime requires Vulkan 1.3: " + requirement);
            } else if ((requirement.startsWith("computeQueues:") || requirement.startsWith("graphicsQueues:"))
                    && Integer.parseInt(requirement.substring(requirement.indexOf(':') + 1)) > 0) {
                runtime.rejectVulkanRequirements("The configured DLSS runtime needs additional queues: " + requirement);
            }
        }
        MetricsDiagnostics.info("vulkan.device", "dlss_device_requirements_evaluated",
                "requirements", java.util.List.of(runtime.vulkanRequirements()), "extensions", extensions);
    }

    private static void saltsAntiAliasing$addSupportedFeature(
            Set<VulkanFeature> vulkanFeatures,
            VulkanPhysicalDevice physicalDevice,
            VulkanFeature feature
    ) {
        boolean supported = saltsAntiAliasing$isFeatureSupported(physicalDevice, feature);
        MetricsDiagnostics.infoOnce(
                "vulkan.feature." + physicalDevice.deviceName() + "." + feature,
                "vulkan.device",
                "optional_device_feature_queried",
                "deviceName", physicalDevice.deviceName(),
                "feature", feature,
                "supported", supported
        );
        if (supported) {
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
