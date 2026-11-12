package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.vma.VmaAllocatorCreateInfo;
import org.lwjgl.util.vma.VmaVulkanFunctions;
import org.lwjgl.vulkan.*;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;
import static org.lwjgl.vulkan.VK11.VK_API_VERSION_1_1;

/** Real GPU fixture with its own queue: never touches a development Minecraft client. */
final class HeadlessVulkan implements AutoCloseable {
    VkInstance instance;
    VkDevice device;
    VkQueue queue;
    VkCommandBuffer commands;
    long pool, allocator;
    VulkanContext context;

    HeadlessVulkan() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var app = VkApplicationInfo.calloc(stack).sType$Default().apiVersion(VK_API_VERSION_1_1)
                    .pApplicationName(stack.UTF8("Salt's Anti Aliasing GPU validation"));
            var instanceInfo = VkInstanceCreateInfo.calloc(stack).sType$Default().pApplicationInfo(app);
            PointerBuffer pointer = stack.mallocPointer(1);
            VulkanContext.check(vkCreateInstance(instanceInfo, null, pointer), "Create test instance");
            instance = new VkInstance(pointer.get(0), instanceInfo);
            var count = stack.mallocInt(1);
            VulkanContext.check(vkEnumeratePhysicalDevices(instance, count, null), "Count GPUs");
            if (count.get(0) == 0) throw new IllegalStateException("No Vulkan GPU");
            var devices = stack.mallocPointer(count.get(0));
            VulkanContext.check(vkEnumeratePhysicalDevices(instance, count, devices), "Enumerate GPUs");
            var physical = new VkPhysicalDevice(devices.get(0), instance);
            vkGetPhysicalDeviceQueueFamilyProperties(physical, count, null);
            var families = VkQueueFamilyProperties.calloc(count.get(0), stack);
            vkGetPhysicalDeviceQueueFamilyProperties(physical, count, families);
            int family = -1;
            for (int i = 0; i < families.capacity(); i++) if ((families.get(i).queueFlags() & VK_QUEUE_GRAPHICS_BIT) != 0) { family = i; break; }
            if (family < 0) throw new IllegalStateException("No graphics queue");
            var queues = VkDeviceQueueCreateInfo.calloc(1, stack).sType$Default().queueFamilyIndex(family)
                    .pQueuePriorities(stack.floats(1));
            var deviceInfo = VkDeviceCreateInfo.calloc(stack).sType$Default().pQueueCreateInfos(queues);
            VulkanContext.check(vkCreateDevice(physical, deviceInfo, null, pointer), "Create test device");
            device = new VkDevice(pointer.get(0), physical, deviceInfo);
            vkGetDeviceQueue(device, family, 0, pointer); queue = new VkQueue(pointer.get(0), device);
            var functions = VmaVulkanFunctions.calloc(stack).set(instance, device);
            var allocatorInfo = VmaAllocatorCreateInfo.calloc(stack).instance(instance).physicalDevice(physical)
                    .device(device).pVulkanFunctions(functions).vulkanApiVersion(VK_API_VERSION_1_1);
            VulkanContext.check(vmaCreateAllocator(allocatorInfo, pointer), "Create test allocator");
            allocator = pointer.get(0); context = new VulkanContext(device, allocator);
            var poolInfo = VkCommandPoolCreateInfo.calloc(stack).sType$Default().queueFamilyIndex(family)
                    .flags(VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT);
            var handle = stack.mallocLong(1);
            VulkanContext.check(vkCreateCommandPool(device, poolInfo, null, handle), "Create test command pool");
            pool = handle.get(0);
            var allocate = VkCommandBufferAllocateInfo.calloc(stack).sType$Default().commandPool(pool)
                    .level(VK_COMMAND_BUFFER_LEVEL_PRIMARY).commandBufferCount(1);
            VulkanContext.check(vkAllocateCommandBuffers(device, allocate, pointer), "Allocate test commands");
            commands = new VkCommandBuffer(pointer.get(0), device);
            var properties = VkPhysicalDeviceProperties.calloc(stack);
            vkGetPhysicalDeviceProperties(physical, properties);
            System.out.println("SALTS_VULKANMOD_GPU " + properties.deviceNameString());
        } catch (RuntimeException e) { close(); throw e; }
    }

    void begin() {
        VulkanContext.check(vkResetCommandBuffer(commands, 0), "Reset test commands");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            VulkanContext.check(vkBeginCommandBuffer(commands, VkCommandBufferBeginInfo.calloc(stack).sType$Default()), "Begin test commands");
        }
    }

    void submit() {
        VulkanContext.check(vkEndCommandBuffer(commands), "End test commands");
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var info = VkSubmitInfo.calloc(1, stack).sType$Default().pCommandBuffers(stack.pointers(commands.address()));
            VulkanContext.check(vkQueueSubmit(queue, info, VK_NULL_HANDLE), "Submit test commands");
            VulkanContext.check(vkQueueWaitIdle(queue), "Wait for test GPU");
        }
    }

    @Override public void close() {
        if (device != null) org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext.waitIdle(device);
        if (pool != 0) vkDestroyCommandPool(device, pool, null);
        if (allocator != 0) vmaDestroyAllocator(allocator);
        if (device != null) vkDestroyDevice(device, null);
        if (instance != null) vkDestroyInstance(instance, null);
    }
}
