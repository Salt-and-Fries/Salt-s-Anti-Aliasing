package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.vulkan.VulkanPhysicalDevice;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntMaps;
import it.unimi.dsi.fastutil.ints.IntIntPair;
import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanFrameGenerationQueueAccess;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.VulkanFrameGenerationQueuePlanner;
import org.lwjgl.glfw.GLFWVulkan;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.VK12;
import org.lwjgl.vulkan.VkPhysicalDevice;
import org.lwjgl.vulkan.VkQueueFamilyProperties;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Reserves three queues that only the FidelityFX frame-generation swapchain will use.
 */
@Mixin(VulkanPhysicalDevice.class)
public abstract class VulkanPhysicalDeviceMixin implements VulkanFrameGenerationQueueAccess {
    @Shadow
    @Final
    @Mutable
    private Int2IntMap queueFamilyCreateInfoMap;

    @Unique
    private VulkanFrameGenerationQueuePlanner.Plan saltsAntiAliasing$frameGenerationQueuePlan;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void saltsAntiAliasing$reserveFrameGenerationQueues(
            VkPhysicalDevice physicalDevice,
            CallbackInfo callbackInfo
    ) {
        VulkanPhysicalDevice owner = (VulkanPhysicalDevice) (Object) this;
        IntIntPair graphicsQueue = owner.graphicsQueueFamilyAndIndex();
        if (physicalDevice == null || graphicsQueue == null) {
            return;
        }

        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer familyCount = stack.callocInt(1);
            VK12.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, familyCount, null);
            int count = familyCount.get(0);
            if (count <= 0) {
                return;
            }

            VkQueueFamilyProperties.Buffer properties = VkQueueFamilyProperties.calloc(count, stack);
            VK12.vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, familyCount, properties);

            List<VulkanFrameGenerationQueuePlanner.QueueFamily> families = new ArrayList<>(count);
            for (int familyIndex = 0; familyIndex < count; familyIndex++) {
                VkQueueFamilyProperties family = properties.get(familyIndex);
                families.add(new VulkanFrameGenerationQueuePlanner.QueueFamily(
                        familyIndex,
                        family.queueFlags(),
                        family.queueCount(),
                        queueFamilyCreateInfoMap.get(familyIndex),
                        GLFWVulkan.glfwGetPhysicalDevicePresentationSupport(
                                physicalDevice.getInstance(),
                                physicalDevice,
                                familyIndex
                        )
                ));
            }

            saltsAntiAliasing$frameGenerationQueuePlan = VulkanFrameGenerationQueuePlanner.plan(
                    families,
                    new VulkanFrameGenerationQueuePlanner.QueueRef(
                            graphicsQueue.leftInt(),
                            graphicsQueue.rightInt()
                    )
            ).orElse(null);
            if (saltsAntiAliasing$frameGenerationQueuePlan == null) {
                return;
            }

            Int2IntArrayMap expandedQueueCounts = new Int2IntArrayMap();
            expandedQueueCounts.putAll(queueFamilyCreateInfoMap);
            saltsAntiAliasing$includeQueue(expandedQueueCounts, saltsAntiAliasing$frameGenerationQueuePlan.asyncCompute());
            saltsAntiAliasing$includeQueue(expandedQueueCounts, saltsAntiAliasing$frameGenerationQueuePlan.present());
            saltsAntiAliasing$includeQueue(expandedQueueCounts, saltsAntiAliasing$frameGenerationQueuePlan.imageAcquire());
            queueFamilyCreateInfoMap = Int2IntMaps.unmodifiable(expandedQueueCounts);
        } catch (RuntimeException exception) {
            saltsAntiAliasing$frameGenerationQueuePlan = null;
            SaltsAntiAliasing.LOGGER.warn("Unable to reserve dedicated Vulkan queues for FSR3 frame generation", exception);
        }
    }

    @Unique
    private static void saltsAntiAliasing$includeQueue(
            Int2IntMap queueCounts,
            VulkanFrameGenerationQueuePlanner.QueueRef queue
    ) {
        queueCounts.put(queue.family(), Math.max(queueCounts.get(queue.family()), queue.index() + 1));
    }

    @Override
    public VulkanFrameGenerationQueuePlanner.Plan saltsAntiAliasing$frameGenerationQueuePlan() {
        return saltsAntiAliasing$frameGenerationQueuePlan;
    }
}
