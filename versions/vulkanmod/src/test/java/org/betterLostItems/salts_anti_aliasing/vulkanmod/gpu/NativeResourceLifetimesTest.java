package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class NativeResourceLifetimesTest {
    @Test void allocatorCleanupLeavesQueueOwnersAliveUntilTheirNativeCleanup() {
        var freed = new ArrayList<String>();
        NativeResourceLifetimes.created("CommandPool", 11, 0, () -> freed.add("pool"));
        NativeResourceLifetimes.created("Fence", 12, 12, () -> freed.add("fence"));
        NativeResourceLifetimes.created("VmaBuffer", 13, 13, () -> freed.add("buffer"));
        NativeResourceLifetimes.closeBeforeAllocator();
        assertEquals(List.of("buffer"), freed);
        NativeResourceLifetimes.destroyed("Fence", 12);
        NativeResourceLifetimes.close();
        assertEquals(List.of("buffer", "pool"), freed);
    }

    @Test void retiresChildrenBeforeBackingStorageAndOnlyOnce() {
        var freed = new ArrayList<String>();
        NativeResourceLifetimes.created("ImageAllocation", 1, 14, () -> freed.add("storage"));
        NativeResourceLifetimes.created("ImageView", 2, 8, () -> freed.add("view"));
        NativeResourceLifetimes.created("Framebuffer", 3, 3, () -> freed.add("framebuffer"));
        NativeResourceLifetimes.close();
        NativeResourceLifetimes.close();
        assertEquals(List.of("framebuffer", "view", "storage"), freed);
    }

    @Test void ordinaryNativeDestructionRetainsOtherObjectTypesWithTheSameHandle() {
        var freed = new ArrayList<String>();
        NativeResourceLifetimes.created("Buffer", 4, 13, () -> freed.add("buffer"));
        NativeResourceLifetimes.created("Image", 4, 14, () -> freed.add("image"));
        NativeResourceLifetimes.destroyed("Buffer", 4);
        NativeResourceLifetimes.close();
        assertEquals(List.of("image"), freed);
    }
}
