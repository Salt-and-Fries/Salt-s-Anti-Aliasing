package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.vulkan.VulkanBackend;
import com.mojang.blaze3d.vulkan.init.VulkanFeature;
import com.mojang.blaze3d.vulkan.init.VulkanPNextStruct;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VkPhysicalDeviceFeatures;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/** Converts LWJGL feature metadata to 26.2's explicit structure-size and boolean-offset API. */
public final class VulkanFeatureSupport {
    private static final ConcurrentHashMap<Class<?>, VulkanPNextStruct> STRUCTS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Integer, Class<?>> TYPES = new ConcurrentHashMap<>();

    private VulkanFeatureSupport() {}

    public static VulkanPNextStruct struct(Class<?> type) {
        if (type == VkPhysicalDeviceFeatures.class) {
            TYPES.put(VulkanBackend.VK10_FEATURES_STRUCT.sType(), type);
            return VulkanBackend.VK10_FEATURES_STRUCT;
        }
        return STRUCTS.computeIfAbsent(type, key -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                Object value = key.getMethod("calloc", MemoryStack.class).invoke(null, stack);
                key.getMethod("sType$Default").invoke(value);
                int sType = (int) key.getMethod("sType").invoke(value);
                TYPES.put(sType, key);
                return new VulkanPNextStruct(sType, key.getField("SIZEOF").getInt(null));
            } catch (ReflectiveOperationException exception) {
                throw new IllegalArgumentException("Unknown Vulkan feature structure " + key.getName(), exception);
            }
        });
    }

    public static VulkanPNextStruct struct(String name) {
        try {
            return struct(Class.forName("org.lwjgl.vulkan." + name));
        } catch (ClassNotFoundException exception) {
            throw new IllegalArgumentException("Unknown Vulkan feature structure " + name, exception);
        }
    }

    public static VulkanFeature feature(VulkanPNextStruct struct, String name) {
        Class<?> type = TYPES.get(struct.sType());
        if (type == null) {
            if (struct.equals(VulkanBackend.VK10_FEATURES_STRUCT)) type = VkPhysicalDeviceFeatures.class;
            else if (struct.equals(VulkanBackend.VK12_FEATURES_STRUCT)) type = org.lwjgl.vulkan.VkPhysicalDeviceVulkan12Features.class;
            else throw new IllegalArgumentException("Unregistered Vulkan feature structure " + struct);
        }
        try {
            int offset = type.getField(name.toUpperCase(Locale.ROOT)).getInt(null);
            // 26.2's VulkanFeature constructor adds the inline Features2 header for core flags.
            return new VulkanFeature(struct, name, offset);
        } catch (ReflectiveOperationException exception) {
            throw new IllegalArgumentException("Unknown Vulkan feature " + name, exception);
        }
    }
}
