package org.betterLostItems.salts_anti_aliasing.vulkanmod;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanFrameGenerationQueuePlanner;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanNativeDeviceInfo;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanNativeDeviceRegistry;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.*;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.xess.XessRuntime;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanFeatureChain;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK10.*;

/** SDK setup shared by adapters; allocations belong to the caller's device-creation stack. */
public final class NativeVulkanSetup {
    private static VulkanFrameGenerationQueuePlanner.Plan queues;
    private static boolean frameGenerationFeatures;
    private static boolean dynamicRendering;
    public static boolean dynamicRenderingEnabled() { return dynamicRendering; }
    private NativeVulkanSetup() {}

    public static void instance(MemoryStack stack, VkInstanceCreateInfo info) {
        var dlss = DlssRuntime.instance();
        dlss.preInitializeFromEnvironment();
        var xess = XessRuntime.instance();
        Set<String> available = instanceExtensions(stack);
        Set<String> enabled = strings(info.ppEnabledExtensionNames());
        if (available.contains("VK_EXT_debug_utils")) enabled.add("VK_EXT_debug_utils");
        for (String request : dlss.vulkanRequirements()) if (request.startsWith("instance:")) {
            String name = request.substring(9);
            if (available.contains(name)) enabled.add(name);
            else dlss.rejectVulkanRequirements("Missing instance extension " + name);
        }
        int requiredApi = VK12.VK_API_VERSION_1_2;
        for (String request : xess.instanceRequirements()) {
            if (request.startsWith("api:")) requiredApi = Math.max(requiredApi, Integer.parseInt(request.substring(4)));
            else if (request.startsWith("instance:")) {
                String name = request.substring(9);
                if (available.contains(name)) enabled.add(name);
                else xess.reject("Missing instance extension " + name);
            }
        }
        info.ppEnabledExtensionNames(pointers(stack, enabled));
        var application = info.pApplicationInfo();
        if (application != null) {
            if (VK.getInstanceVersionSupported() >= requiredApi)
                application.apiVersion(Math.max(application.apiVersion(), requiredApi));
            else xess.reject("The Vulkan loader does not support XeSS's required API version");
        }
    }

    public static void device(MemoryStack stack, VkPhysicalDevice physical, VkDeviceCreateInfo info,
                               long surface, int graphicsFamily) {
        Set<String> available = deviceExtensions(stack, physical);
        Set<String> enabled = strings(info.ppEnabledExtensionNames());
        var features = new VulkanFeatureChain(stack, physical, info);
        dynamicRendering = features.enable("VkPhysicalDeviceDynamicRenderingFeatures", "dynamicRendering");
        for (String core : List.of("sampleRateShading", "shaderInt16", "shaderStorageImageExtendedFormats", "shaderStorageImageReadWithoutFormat", "shaderStorageImageWriteWithoutFormat"))
            features.enableCore(core);
        frameGenerationFeatures = features.enableCore("shaderStorageImageReadWithoutFormat")
                & features.enableCore("shaderStorageImageWriteWithoutFormat");
        for (String feature : List.of("shaderFloat16", "timelineSemaphore", "bufferDeviceAddress", "shaderStorageBufferArrayNonUniformIndexing",
                "descriptorBindingPartiallyBound", "runtimeDescriptorArray")) features.enable("VkPhysicalDeviceVulkan12Features", feature);
        for (String feature : List.of("synchronization2", "subgroupSizeControl", "computeFullSubgroups", "privateData", "maintenance4"))
            features.enable("VkPhysicalDeviceVulkan13Features", feature);
        frameGenerationFeatures &= features.enable("VkPhysicalDeviceVulkan12Features", "timelineSemaphore")
                & features.enable("VkPhysicalDeviceVulkan12Features", "bufferDeviceAddress")
                & features.enable("VkPhysicalDeviceSynchronization2Features", "synchronization2");
        for (String extension : List.of("VK_KHR_get_memory_requirements2", "VK_KHR_dedicated_allocation", "VK_KHR_shader_float16_int8",
                "VK_EXT_subgroup_size_control", "VK_EXT_descriptor_indexing", "VK_KHR_timeline_semaphore", "VK_KHR_buffer_device_address",
                "VK_KHR_synchronization2", "VK_EXT_private_data", "VK_KHR_dynamic_rendering", "VK_KHR_maintenance4")) if (available.contains(extension)) enabled.add(extension);

        var dlss = DlssRuntime.instance();
        for (String request : dlss.vulkanRequirements()) {
            boolean accepted = true;
            if (request.startsWith("device:")) {
                String name = request.substring(7); accepted = available.contains(name);
                if (accepted) enabled.add(name);
            } else if (request.startsWith("12:")) accepted = features.enable("VkPhysicalDeviceVulkan12Features", request.substring(3));
            else if (request.startsWith("13:")) accepted = features.enable("VkPhysicalDeviceVulkan13Features", request.substring(3));
            else if (request.startsWith("computeQueues:") || request.startsWith("graphicsQueues:"))
                accepted = Integer.parseInt(request.substring(request.indexOf(':') + 1)) == 0;
            if (!accepted) dlss.rejectVulkanRequirements("Unsupported DLSS requirement " + request);
        }
        var xess = XessRuntime.instance();
        boolean xessAccepted = true;
        for (String request : xess.deviceRequirements(physical.getInstance().address(), physical.address())) {
            if (request.startsWith("device:")) {
                String name = request.substring(7);
                if (available.contains(name)) enabled.add(name); else xessAccepted = false;
            } else if (request.startsWith("feature:")) {
                String[] parts = request.split(":");
                xessAccepted &= features.enableOffset(parts[1], Integer.parseInt(parts[2]));
            } else xessAccepted = false;
        }
        if (xessAccepted) xess.acceptDeviceRequirements();
        else xess.reject("The physical device does not satisfy XeSS Vulkan requirements");

        features.finish();
        if (enabled.contains("VK_KHR_buffer_device_address")) enabled.remove("VK_EXT_buffer_device_address");
        // Individual descriptor-indexing booleans are already represented by the Vulkan 1.2 aggregate.
        enabled.remove("VK_EXT_descriptor_indexing");
        info.ppEnabledExtensionNames(pointers(stack, enabled));
        reserveQueues(stack, physical, info, surface, graphicsFamily);
        FsrRuntime.instance().setFrameGenerationDeviceFeaturesAvailable(frameGenerationFeatures && queues != null);
    }

    private static void reserveQueues(MemoryStack stack, VkPhysicalDevice physical, VkDeviceCreateInfo info, long surface, int graphicsFamily) {
        var original = info.pQueueCreateInfos();
        Map<Integer, VkDeviceQueueCreateInfo> requests = new LinkedHashMap<>();
        for (int i = 0; i < original.capacity(); i++) requests.put(original.get(i).queueFamilyIndex(), original.get(i));
        var count = stack.mallocInt(1);
        vkGetPhysicalDeviceQueueFamilyProperties(physical, count, null);
        var properties = VkQueueFamilyProperties.calloc(count.get(0), stack);
        vkGetPhysicalDeviceQueueFamilyProperties(physical, count, properties);
        List<VulkanFrameGenerationQueuePlanner.QueueFamily> families = new ArrayList<>();
        for (int family = 0; family < properties.capacity(); family++) {
            var row = properties.get(family);
            var present = stack.callocInt(1);
            if (surface != 0) VulkanContext.check(KHRSurface.vkGetPhysicalDeviceSurfaceSupportKHR(physical, family, surface, present), "Query presentation support");
            var requested = requests.get(family);
            families.add(new VulkanFrameGenerationQueuePlanner.QueueFamily(family, row.queueFlags(), row.queueCount(),
                    requested == null ? 0 : requested.queueCount(), present.get(0) != 0));
        }
        queues = VulkanFrameGenerationQueuePlanner.plan(families,
                new VulkanFrameGenerationQueuePlanner.QueueRef(graphicsFamily, 0)).orElse(null);
        if (queues == null) return;
        Map<Integer, Integer> counts = new LinkedHashMap<>();
        for (var entry : requests.entrySet()) counts.put(entry.getKey(), entry.getValue().queueCount());
        for (var queue : List.of(queues.asyncCompute(), queues.present(), queues.imageAcquire()))
            counts.merge(queue.family(), queue.index() + 1, Math::max);
        var expanded = VkDeviceQueueCreateInfo.calloc(counts.size(), stack);
        int index = 0;
        for (var entry : counts.entrySet()) {
            var create = expanded.get(index++);
            var old = requests.get(entry.getKey());
            if (old != null) create.set(old);
            create.sType$Default().queueFamilyIndex(entry.getKey());
            var priorities = stack.mallocFloat(entry.getValue());
            for (int queue = 0; queue < entry.getValue(); queue++)
                priorities.put(queue, old != null && queue < old.queueCount() ? old.pQueuePriorities().get(queue) : 1f);
            create.pQueuePriorities(priorities);
        }
        info.pQueueCreateInfos(expanded);
    }

    public static void ready(VkDevice device, VkQueue gameQueue, int graphicsFamily) {
        org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext.idleBarrier(
                org.betterLostItems.salts_anti_aliasing.client.render.vulkan.fsr.FsrRuntime.instance()::waitForPresents);
        var physical = device.getPhysicalDevice();
        var info = new VulkanNativeDeviceInfo(physical.getInstance().address(), physical.address(), device.address(),
                gameQueue.address(), graphicsFamily,
                queue(device, queues == null ? null : queues.asyncCompute()), queues == null ? -1 : queues.asyncCompute().family(),
                queue(device, queues == null ? null : queues.present()), queues == null ? -1 : queues.present().family(),
                queue(device, queues == null ? null : queues.imageAcquire()), queues == null ? -1 : queues.imageAcquire().family());
        VulkanNativeDeviceRegistry.capture(info);
        DlssVulkanDeviceRegistry.capture(new DlssVulkanDeviceInfo(info.instance(), info.physicalDevice(), info.device(),
                info.graphicsQueue(), info.graphicsQueueFamily()));
        System.out.println("SALTS_VULKANMOD_NATIVE device=" + Long.toHexString(info.device()) + " privateQueues=" + info.frameGenerationQueuesComplete()
                + " DLSS=" + DlssRuntime.instance().status() + " XeSS=" + XessRuntime.instance().message());
    }

    private static long queue(VkDevice device, VulkanFrameGenerationQueuePlanner.QueueRef reference) {
        if (reference == null) return 0;
        try (var stack = MemoryStack.stackPush()) {
            var pointer = stack.mallocPointer(1); vkGetDeviceQueue(device, reference.family(), reference.index(), pointer);
            return pointer.get(0);
        }
    }

    public static void shutdown() {
        DlssRuntime.instance().shutdown();
        XessRuntime.instance().shutdown();
        FsrRuntime.instance().shutdown();
        queues = null; frameGenerationFeatures = false;
    }

    private static Set<String> strings(PointerBuffer pointers) {
        Set<String> result = new LinkedHashSet<>();
        if (pointers != null) for (int i = pointers.position(); i < pointers.limit(); i++) result.add(MemoryUtil.memUTF8(pointers.get(i)));
        return result;
    }

    private static PointerBuffer pointers(MemoryStack stack, Set<String> names) {
        var result = stack.mallocPointer(names.size());
        for (String name : names) result.put(stack.UTF8(name));
        return result.flip();
    }

    private static Set<String> instanceExtensions(MemoryStack stack) {
        var count = stack.mallocInt(1);
        VulkanContext.check(vkEnumerateInstanceExtensionProperties((ByteBuffer) null, count, null), "Count instance extensions");
        var properties = VkExtensionProperties.calloc(count.get(0), stack);
        VulkanContext.check(vkEnumerateInstanceExtensionProperties((ByteBuffer) null, count, properties), "Query instance extensions");
        Set<String> result = new HashSet<>();
        for (int i = 0; i < properties.capacity(); i++) result.add(properties.get(i).extensionNameString());
        return result;
    }

    private static Set<String> deviceExtensions(MemoryStack stack, VkPhysicalDevice physical) {
        var count = stack.mallocInt(1);
        VulkanContext.check(vkEnumerateDeviceExtensionProperties(physical, (ByteBuffer) null, count, null), "Count device extensions");
        var properties = VkExtensionProperties.calloc(count.get(0), stack);
        VulkanContext.check(vkEnumerateDeviceExtensionProperties(physical, (ByteBuffer) null, count, properties), "Query device extensions");
        Set<String> result = new HashSet<>();
        for (int i = 0; i < properties.capacity(); i++) result.add(properties.get(i).extensionNameString());
        return result;
    }
}
