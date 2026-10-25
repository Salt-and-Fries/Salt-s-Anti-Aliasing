package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.GpuFence;
import com.mojang.renderpearl.api.commands.GpuQueryPool;

import org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics;

import java.util.ArrayDeque;

/** Sparse, nonblocking GPU measurements; inactive unless metrics recording is enabled. */
public final class VulkanGpuProfiler {
    private record Pending(String label, GpuQueryPool queries, GpuFence fence) {}

    private static final ArrayDeque<Pending> PENDING = new ArrayDeque<>();
    private static long frame;
    private static boolean sample;

    private VulkanGpuProfiler() {}

    static void release() {
        while (!PENDING.isEmpty()) {
            Pending pending = PENDING.remove();
            pending.fence().close(); pending.queries().close();
        }
        sample = false;
    }

    public static void beginFrame() {
        sample = MetricsDiagnostics.enabled() && ++frame % 120 == 0;
        while (!PENDING.isEmpty()) {
            Pending p = PENDING.peek();
            if (!p.fence().awaitCompletion(0)) break;
            PENDING.remove();
            try {
                var values = p.queries().getValues(0, 2);
                if (values[0].isPresent() && values[1].isPresent()) {
                    double ms =
                            (values[1].getAsLong() - values[0].getAsLong())
                                    * RenderSystem.getDevice().getDeviceInfo().timestampPeriod()
                                    / 1_000_000.0;
                    MetricsDiagnostics.info(
                            "gpu.profile",
                            "pass_duration",
                            "pass",
                            p.label(),
                            "gpuMilliseconds",
                            ms);
                }
            } finally {
                p.fence().close();
                p.queries().close();
            }
        }
    }

    public static Scope scope(String label) {
        if (!sample
                || PENDING.size() >= 32
                || RenderSystem.getDevice().getDeviceInfo().timestampPeriod() <= 0)
            return Scope.INACTIVE;
        GpuQueryPool queries = RenderSystem.getDevice().createTimestampQueryPool(2);
        RenderSystem.getDevice().createCommandEncoder().writeTimestamp(queries, 0);
        return new Scope(label, queries);
    }

    public static final class Scope implements AutoCloseable {
        private static final Scope INACTIVE = new Scope("", null);
        private final String label;
        private final GpuQueryPool queries;

        private Scope(String label, GpuQueryPool queries) {
            this.label = label;
            this.queries = queries;
        }

        public void close() {
            if (queries == null) return;
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            encoder.writeTimestamp(queries, 1);
            PENDING.add(new Pending(label, queries, encoder.createFence()));
        }
    }
}
