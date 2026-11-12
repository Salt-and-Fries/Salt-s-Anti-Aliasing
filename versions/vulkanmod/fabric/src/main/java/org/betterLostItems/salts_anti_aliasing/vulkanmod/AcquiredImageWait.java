package org.betterLostItems.salts_anti_aliasing.vulkanmod;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.vulkan.*;
import java.util.ArrayList;
import java.util.function.ToIntFunction;
import static org.lwjgl.vulkan.VK10.*;

/** A screenshot can flush a frame early; its acquire semaphore must be consumed exactly once. */
public final class AcquiredImageWait {
    private static long acquired;
    private static boolean consumed;
    private AcquiredImageWait() {}
    public static void acquired(int result, long semaphore) {
        acquired = result == VK_SUCCESS || result == KHRSwapchain.VK_SUBOPTIMAL_KHR ? semaphore : 0;
        consumed = false;
    }
    public static int submit(VkSubmitInfo info, ToIntFunction<VkSubmitInfo> submit) {
        if (acquired == 0) return submit.applyAsInt(info);
        try (var stack = MemoryStack.stackPush()) {
            var waits = new ArrayList<Long>(); var stages = new ArrayList<Integer>();
            var oldWaits = info.pWaitSemaphores(); var oldStages = info.pWaitDstStageMask();
            boolean included = false;
            for (int i = 0; i < info.waitSemaphoreCount(); i++) {
                long semaphore = oldWaits.get(i);
                if (semaphore == acquired) {
                    if (consumed) continue;
                    included = true;
                }
                waits.add(semaphore); stages.add(semaphore == acquired ? VK_PIPELINE_STAGE_ALL_COMMANDS_BIT : oldStages.get(i));
            }
            if (!consumed && !included) { waits.add(acquired); stages.add(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT); }
            var semaphores = stack.mallocLong(waits.size()); var masks = stack.mallocInt(waits.size());
            for (int i = 0; i < waits.size(); i++) { semaphores.put(i, waits.get(i)); masks.put(i, stages.get(i)); }
            var copy = VkSubmitInfo.malloc(stack).set(info).waitSemaphoreCount(waits.size())
                    .pWaitSemaphores(semaphores).pWaitDstStageMask(masks);
            int result = submit.applyAsInt(copy);
            if (result == VK_SUCCESS) consumed = true;
            return result;
        }
    }
}
