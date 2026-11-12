package org.betterLostItems.salts_anti_aliasing.client.render.vulkan.xess;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanFeatureSupport;

import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import java.lang.reflect.Modifier;
import java.util.*;

/** Merge Intel's queried features into Blaze3D before VkDevice creation. */
public final class XessDeviceRequirements {
    private XessDeviceRequirements() {}
    public static void enable(Set<String> extensions, Set<VulkanFeature> features, VulkanPhysicalDevice physical) {
        var runtime = XessRuntime.instance();
        String[] requirements = runtime.deviceRequirements(physical.vkPhysicalDevice().getInstance().address(), physical.vkPhysicalDevice().address());
        Set<String> additions = new HashSet<>();
        Set<VulkanFeature> featureAdditions = new HashSet<>();
        try {
            for (String requirement : requirements) {
                String[] parts = requirement.split(":");
                if (parts[0].equals("device")) {
                    if (!physical.hasDeviceExtension(parts[1])) throw new IllegalStateException("Missing extension " + parts[1]);
                    additions.add(parts[1]);
                } else if (parts[0].equals("feature")) {
                    VulkanFeature feature = feature(parts[1], Integer.parseInt(parts[2]));
                    try (MemoryStack stack = MemoryStack.stackPush()) {
                        var supported = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default();
                        feature.struct().findOrCreateStructInPNextChain(supported, stack);
                        VK12.vkGetPhysicalDeviceFeatures2(physical.vkPhysicalDevice(), supported);
                        if (!feature.get(supported)) throw new IllegalStateException("Missing feature " + feature);
                    }
                    featureAdditions.add(feature);
                } else throw new IllegalStateException("Unknown XeSS requirement " + requirement);
            }
            extensions.addAll(additions); features.addAll(featureAdditions);
            runtime.acceptDeviceRequirements();
            MetricsDiagnostics.info("xess.runtime", "device_requirements_enabled", "requirements", List.of(requirements));
        } catch (ReflectiveOperationException | RuntimeException e) {
            runtime.reject("XeSS unsupported: " + e.getMessage());
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    static VulkanFeature feature(String structName, int offset) throws ReflectiveOperationException {
        Class<?> type = switch (structName) {
            case "VkPhysicalDeviceFeatures2" -> VkPhysicalDeviceFeatures.class;
            case "VkPhysicalDeviceVulkan12Features" -> VkPhysicalDeviceVulkan12Features.class;
            case "VkPhysicalDeviceVulkan13Features" -> VkPhysicalDeviceVulkan13Features.class;
            case "VkPhysicalDeviceShaderFloat16Int8Features" -> VkPhysicalDeviceShaderFloat16Int8Features.class;
            case "VkPhysicalDeviceMutableDescriptorTypeFeaturesEXT" -> VkPhysicalDeviceMutableDescriptorTypeFeaturesEXT.class;
            case "VkPhysicalDeviceShaderIntegerDotProductFeatures" -> VkPhysicalDeviceShaderIntegerDotProductFeatures.class;
            default -> throw new IllegalArgumentException("Unknown XeSS feature structure " + structName);
        };
        int fieldOffset = type == VkPhysicalDeviceFeatures.class ? offset - VkPhysicalDeviceFeatures2.FEATURES : offset;
        for (var method : type.getDeclaredMethods()) {
            if (method.getReturnType() != boolean.class || method.getParameterCount() != 0 || Modifier.isStatic(method.getModifiers())) continue;
            String name = method.getName();
            if (type.getField(name.toUpperCase(Locale.ROOT)).getInt(null) != fieldOffset) continue;
            if (type == VkPhysicalDeviceFeatures.class) return VulkanFeatureSupport.feature(VulkanBackend.VK10_FEATURES_STRUCT, name);
            return new VulkanFeature(VulkanFeatureSupport.struct(type), name, offset);
        }
        throw new IllegalArgumentException("Unknown feature offset " + structName + ":" + offset);
    }
}
