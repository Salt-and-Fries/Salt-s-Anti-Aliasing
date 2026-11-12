package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.Pointer;
import org.lwjgl.vulkan.*;

import java.lang.reflect.Modifier;
import java.util.*;

import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.vkGetPhysicalDeviceFeatures2;

/** Merges SDK feature requests with VulkanMod's live device-create chain without duplicate aliases. */
public final class VulkanFeatureChain {
    private record Meta(Class<?> type, int sType, Map<String, Integer> fields) {}
    private record Node(Meta meta, long address) {}
    private static final Map<String, String> PROMOTIONS = Map.ofEntries(
            Map.entry("VkPhysicalDeviceShaderFloat16Int8Features", "VkPhysicalDeviceVulkan12Features"),
            Map.entry("VkPhysicalDeviceBufferDeviceAddressFeatures", "VkPhysicalDeviceVulkan12Features"),
            Map.entry("VkPhysicalDeviceTimelineSemaphoreFeatures", "VkPhysicalDeviceVulkan12Features"),
            Map.entry("VkPhysicalDeviceDescriptorIndexingFeatures", "VkPhysicalDeviceVulkan12Features"),
            Map.entry("VkPhysicalDeviceVulkanMemoryModelFeatures", "VkPhysicalDeviceVulkan12Features"),
            Map.entry("VkPhysicalDeviceSynchronization2Features", "VkPhysicalDeviceVulkan13Features"),
            Map.entry("VkPhysicalDeviceDynamicRenderingFeatures", "VkPhysicalDeviceVulkan13Features"),
            Map.entry("VkPhysicalDeviceShaderIntegerDotProductFeatures", "VkPhysicalDeviceVulkan13Features"),
            Map.entry("VkPhysicalDeviceMaintenance4Features", "VkPhysicalDeviceVulkan13Features"),
            Map.entry("VkPhysicalDeviceSubgroupSizeControlFeatures", "VkPhysicalDeviceVulkan13Features"),
            Map.entry("VkPhysicalDevicePrivateDataFeatures", "VkPhysicalDeviceVulkan13Features"),
            Map.entry("VkPhysicalDeviceZeroInitializeWorkgroupMemoryFeatures", "VkPhysicalDeviceVulkan13Features"),
            Map.entry("VkPhysicalDeviceShaderDemoteToHelperInvocationFeatures", "VkPhysicalDeviceVulkan13Features"),
            Map.entry("VkPhysicalDeviceShaderTerminateInvocationFeatures", "VkPhysicalDeviceVulkan13Features"));
    private final MemoryStack stack;
    private final VkPhysicalDevice physical;
    private final VkDeviceCreateInfo info;
    private final LinkedHashMap<Integer, Long> chain = new LinkedHashMap<>();
    private final Map<String, Meta> metadata = new HashMap<>();
    private final Map<Integer, Node> supported = new HashMap<>();
    private final VkPhysicalDeviceFeatures core, availableCore;
    private final int apiVersion;

    public VulkanFeatureChain(MemoryStack stack, VkPhysicalDevice physical, VkDeviceCreateInfo info) {
        this.stack = stack; this.physical = physical; this.info = info;
        var properties = VkPhysicalDeviceProperties.calloc(stack);
        vkGetPhysicalDeviceProperties(physical, properties); apiVersion = properties.apiVersion();
        for (long pointer = info.pNext(); pointer != 0;) {
            var node = VkBaseOutStructure.create(pointer);
            if (chain.putIfAbsent(node.sType(), pointer) != null)
                throw new IllegalArgumentException("Duplicate original Vulkan feature structure " + node.sType());
            pointer = node.pNext() == null ? 0 : node.pNext().address();
        }
        var features2 = chain.get(VK11.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_FEATURES_2);
        if (features2 != null) {
            core = VkPhysicalDeviceFeatures2.create(features2).features();
            if (info.pEnabledFeatures() != null) {
                var requested = info.pEnabledFeatures();
                for (int offset : coreMeta().fields.values())
                    if (MemoryUtil.memGetInt(requested.address() + offset) != 0) MemoryUtil.memPutInt(core.address() + offset, 1);
                info.pEnabledFeatures(null);
            }
        } else {
            core = info.pEnabledFeatures() == null ? VkPhysicalDeviceFeatures.calloc(stack) : info.pEnabledFeatures();
            info.pEnabledFeatures(core);
        }
        availableCore = VkPhysicalDeviceFeatures.calloc(stack);
        vkGetPhysicalDeviceFeatures(physical, availableCore);
        normalizePromotedAliases();
    }

    public boolean enableCore(String field) {
        Integer offset = coreMeta().fields.get(field);
        if (offset == null) throw new IllegalArgumentException("Unknown core Vulkan feature " + field);
        if (MemoryUtil.memGetInt(availableCore.address() + offset) == 0) return false;
        MemoryUtil.memPutInt(core.address() + offset, 1);
        return true;
    }

    public boolean enable(String struct, String field) {
        String aggregate = promoted(struct);
        if (aggregate != null) struct = aggregate;
        if (struct.equals("VkPhysicalDeviceVulkan13Features") && apiVersion < VK13.VK_API_VERSION_1_3
                || struct.equals("VkPhysicalDeviceVulkan12Features") && apiVersion < VK12.VK_API_VERSION_1_2) return false;
        Meta meta = meta(struct);
        Integer offset = meta.fields.get(field);
        if (offset == null) throw new IllegalArgumentException("Unknown Vulkan feature " + struct + "." + field);
        Node available = supported.computeIfAbsent(meta.sType, id -> {
            Node node = allocate(meta);
            var query = VkPhysicalDeviceFeatures2.calloc(stack).sType$Default().pNext(node.address);
            vkGetPhysicalDeviceFeatures2(physical, query);
            return node;
        });
        if (MemoryUtil.memGetInt(available.address + offset) == 0) return false;
        long target = chain.computeIfAbsent(meta.sType, id -> allocate(meta).address);
        MemoryUtil.memPutInt(target + offset, 1);
        return true;
    }

    /** SDK offsets for Features2 include its header; LWJGL's standalone core-feature offsets do not. */
    public boolean enableOffset(String struct, int offset) {
        boolean base = struct.equals("VkPhysicalDeviceFeatures2");
        Meta meta = base ? coreMeta() : meta(struct);
        int relative = base ? offset - VkPhysicalDeviceFeatures2.FEATURES : offset;
        String field = meta.fields.entrySet().stream().filter(entry -> entry.getValue() == relative)
                .map(Map.Entry::getKey).findFirst().orElseThrow(() -> new IllegalArgumentException("Unknown feature offset " + struct + ":" + offset));
        return base ? enableCore(field) : enable(struct, field);
    }

    public void finish() {
        long next = 0;
        var pointers = new ArrayList<>(chain.values());
        for (int i = pointers.size() - 1; i >= 0; i--) {
            long address = pointers.get(i);
            MemoryUtil.memPutAddress(address + VkBaseOutStructure.PNEXT, next);
            next = address;
        }
        info.pNext(next);
    }

    private String promoted(String struct) {
        String base = struct.replaceFirst("(?:KHR|EXT)$", "");
        String target = PROMOTIONS.get(base);
        if (target == null) return null;
        int minimum = target.equals("VkPhysicalDeviceVulkan12Features") ? VK12.VK_API_VERSION_1_2 : VK13.VK_API_VERSION_1_3;
        return apiVersion >= minimum ? target : null;
    }

    private void normalizePromotedAliases() {
        for (String name : PROMOTIONS.keySet()) {
            String aggregate = promoted(name);
            if (aggregate == null) continue;
            Meta alias;
            try { alias = meta(name); }
            catch (IllegalArgumentException absentFromOldBindings) { continue; }
            Long requested = chain.get(alias.sType);
            if (requested == null) continue;
            for (var field : alias.fields.entrySet()) {
                if (MemoryUtil.memGetInt(requested + field.getValue()) != 0 && !enable(aggregate, field.getKey()))
                    throw new IllegalStateException("Existing Vulkan feature is unavailable: " + name + "." + field.getKey());
            }
            chain.remove(alias.sType);
        }
    }

    private Meta coreMeta() { return meta("VkPhysicalDeviceFeatures"); }

    private Meta meta(String name) {
        return metadata.computeIfAbsent(name, key -> {
            try {
                Class<?> type;
                try { type = Class.forName("org.lwjgl.vulkan." + key); }
                catch (ClassNotFoundException missing) {
                    // The EXT structure is an ABI-identical rename of the older VALVE structure.
                    if (!key.equals("VkPhysicalDeviceMutableDescriptorTypeFeaturesEXT")) throw missing;
                    type = Class.forName("org.lwjgl.vulkan.VkPhysicalDeviceMutableDescriptorTypeFeaturesVALVE");
                }
                Map<String, Integer> fields = new LinkedHashMap<>();
                for (var method : type.getDeclaredMethods()) {
                    if (method.getReturnType() == boolean.class && method.getParameterCount() == 0 && !Modifier.isStatic(method.getModifiers()))
                        fields.put(method.getName(), type.getField(method.getName().toUpperCase(Locale.ROOT)).getInt(null));
                }
                int sType = 0;
                if (type != VkPhysicalDeviceFeatures.class) {
                    Object node = type.getMethod("calloc", MemoryStack.class).invoke(null, stack);
                    type.getMethod("sType$Default").invoke(node);
                    sType = VkBaseOutStructure.create(((Pointer) node).address()).sType();
                }
                return new Meta(type, sType, Map.copyOf(fields));
            } catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Unknown native feature structure " + key, e); }
        });
    }

    private Node allocate(Meta meta) {
        try {
            Object node = meta.type.getMethod("calloc", MemoryStack.class).invoke(null, stack);
            meta.type.getMethod("sType$Default").invoke(node);
            return new Node(meta, ((Pointer) node).address());
        } catch (ReflectiveOperationException e) { throw new IllegalArgumentException("Cannot allocate feature structure " + meta.type.getName(), e); }
    }
}
