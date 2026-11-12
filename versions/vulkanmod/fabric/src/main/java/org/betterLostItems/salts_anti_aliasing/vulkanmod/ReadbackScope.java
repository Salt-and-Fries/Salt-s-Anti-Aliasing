package org.betterLostItems.salts_anti_aliasing.vulkanmod;

/** Keeps the newly opened command buffer free of image uses until an immediate readback finishes. */
public final class ReadbackScope implements AutoCloseable {
    private static final ThreadLocal<Integer> DEPTH = ThreadLocal.withInitial(() -> 0);
    private ReadbackScope() { DEPTH.set(DEPTH.get() + 1); }
    public static ReadbackScope enter() { return new ReadbackScope(); }
    public static boolean active() { return DEPTH.get() != 0; }
    @Override public void close() {
        int depth = DEPTH.get() - 1;
        if (depth == 0) DEPTH.remove(); else DEPTH.set(depth);
    }
}
