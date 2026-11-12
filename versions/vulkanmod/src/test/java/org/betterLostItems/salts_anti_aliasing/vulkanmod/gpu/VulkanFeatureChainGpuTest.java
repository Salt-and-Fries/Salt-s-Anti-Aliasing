package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;

import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "salts.gpuValidation", matches = "true")
class VulkanFeatureChainGpuTest {
    @Test void mergesPromotedFeaturesAndWritesCoreOffsetsExactlyOnce() {
        try (var gpu = new HeadlessVulkan(); var stack = MemoryStack.stackPush()) {
            var dynamic = VkPhysicalDeviceDynamicRenderingFeatures.calloc(stack).sType$Default().dynamicRendering(true);
            var original = VkPhysicalDeviceVulkan12Features.calloc(stack).sType$Default().shaderFloat16(true).pNext(dynamic.address());
            var core = VkPhysicalDeviceFeatures.calloc(stack);
            var create = VkDeviceCreateInfo.calloc(stack).sType$Default().pEnabledFeatures(core).pNext(original.address());
            var chain = new VulkanFeatureChain(stack, gpu.device.getPhysicalDevice(), create);
            assertTrue(chain.enableOffset("VkPhysicalDeviceFeatures2", VkPhysicalDeviceFeatures2.FEATURES + VkPhysicalDeviceFeatures.SAMPLERATESHADING));
            assertTrue(chain.enable("VkPhysicalDeviceVulkan13Features", "synchronization2"));
            assertTrue(chain.enable("VkPhysicalDeviceShaderFloat16Int8Features", "shaderFloat16"));
            chain.finish();
            assertTrue(core.sampleRateShading());
            assertFalse(core.robustBufferAccess());
            var seen = new HashSet<Integer>();
            VkPhysicalDeviceVulkan13Features aggregate = null;
            for (long pointer = create.pNext(); pointer != 0;) {
                var node = VkBaseOutStructure.create(pointer);
                assertTrue(seen.add(node.sType()), "Duplicate enabled-feature structure");
                if (node.sType() == VK13.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_VULKAN_1_3_FEATURES)
                    aggregate = VkPhysicalDeviceVulkan13Features.create(pointer);
                pointer = node.pNext() == null ? 0 : node.pNext().address();
            }
            assertFalse(seen.contains(KHRDynamicRendering.VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_DYNAMIC_RENDERING_FEATURES_KHR));
            assertNotNull(aggregate);
            assertTrue(aggregate.dynamicRendering());
            assertTrue(aggregate.synchronization2());
            assertTrue(original.shaderFloat16());
        }
    }
}
