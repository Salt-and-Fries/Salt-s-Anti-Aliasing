package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.Minecraft;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.joml.Quaternionf;

import java.util.*;
import java.util.function.BiConsumer;

/** Keeps only the current and previous extracted particle frames, including billboard roll/scale. */
public final class ParticleMotionGeometry {
    public record Quad(SingleQuadParticle.Layer layer, float[] positions,
                       float u0, float u1, float v0, float v1, float alpha) {}
    private static IdentityHashMap<Object, List<Quad>> current = new IdentityHashMap<>();
    private static IdentityHashMap<Object, List<Quad>> previous = new IdentityHashMap<>();
    private static Object level;
    private static boolean enabled;
    private static int count, unmatched, dropped, maxQuads;

    private ParticleMotionGeometry() {}

    public static void beginExtraction() {
        var runtime = SaltsAntiAliasingClient.runtimeOrNull();
        boolean next = runtime != null && runtime.activeMode() == AntiAliasingMode.DLSS_SUPER_RESOLUTION
                && runtime.dlssRuntimeStatus().ready() && runtime.canUseAntiAliasing()
                && runtime.configSnapshot().tuning.entityMotion;
        Object nextLevel = Minecraft.getInstance().level;
        previous = current;
        current = new IdentityHashMap<>();
        if (!enabled || !next || nextLevel != level) previous.clear();
        level = nextLevel;
        enabled = next;
        maxQuads = next ? runtime.configSnapshot().tuning.motionVertexBudget.vertices() / 6 : 0;
        count = unmatched = dropped = 0;
    }

    public static void capture(Object identity, SingleQuadParticle.Layer layer,
                               float x, float y, float z, float qx, float qy, float qz, float qw,
                               float size, float u0, float u1, float v0, float v1, int color) {
        if (!enabled) return;
        if (count >= maxQuads) { dropped++; return; }
        current.computeIfAbsent(identity, k -> new ArrayList<>(1)).add(new Quad(layer,
                ParticleMotionQuad.positions(x, y, z, new Quaternionf(qx, qy, qz, qw), size),
                u0, u1, v0, v1, ((color >>> 24) & 255) / 255.0f));
        count++;
    }

    static void forEach(BiConsumer<Quad, Quad> consumer) {
        unmatched = 0;
        if (!enabled) return;
        for (var entry : current.entrySet()) {
            var old = previous.get(entry.getKey());
            for (int i = 0; i < entry.getValue().size(); i++) {
                Quad now = entry.getValue().get(i);
                Quad before = old != null && i < old.size() && old.get(i).layer() == now.layer()
                        ? old.get(i) : null;
                if (before == null) { unmatched++; continue; }
                consumer.accept(now, before);
            }
        }
    }

    public static int capturedQuads() { return count; }
    public static int unmatchedQuads() { return unmatched; }
    public static int droppedQuads() { return dropped; }

    public static void clear() {
        current.clear(); previous.clear(); level = null; enabled = false; count = unmatched = dropped = 0;
    }
}
