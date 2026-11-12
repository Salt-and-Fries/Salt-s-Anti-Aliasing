package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import java.util.IdentityHashMap;

/** Render-thread ownership of the native pipelines attached to Minecraft render descriptions. */
public final class RenderPipelineLifetimes {
    private record Allocation(Object identity, Runnable retire, Runnable destroy) {}
    private static final IdentityHashMap<Object, Allocation> owners = new IdentityHashMap<>();
    private static final IdentityHashMap<Object, Integer> references = new IdentityHashMap<>();
    private RenderPipelineLifetimes() {}

    public static void replaced(Object owner, Object identity, Runnable retire, Runnable destroy) {
        var previous = owners.get(owner);
        if (previous != null && previous.identity == identity) return;
        if (identity == null) owners.remove(owner);
        else {
            owners.put(owner, new Allocation(identity, retire, destroy));
            references.merge(identity, 1, Integer::sum);
        }
        if (previous != null) {
            int remaining = references.get(previous.identity) - 1;
            if (remaining == 0) {
                references.remove(previous.identity);
                previous.retire.run();
            } else references.put(previous.identity, remaining);
        }
    }

    /** Called after device-idle, before VulkanMod destroys the allocator and device. */
    public static void close() {
        var remaining = new IdentityHashMap<Object, Allocation>();
        for (var allocation : owners.values()) remaining.put(allocation.identity, allocation);
        owners.clear(); references.clear();
        for (var allocation : remaining.values()) allocation.destroy.run();
    }
}
