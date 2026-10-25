package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import com.mojang.blaze3d.vertex.VertexConsumer;

import it.unimi.dsi.fastutil.floats.FloatArrayList;

import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.MovingBlockFeatureRenderer;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.function.BiConsumer;

/**
 * Matches the actual transformed vertices emitted by vanilla entity models between frames. This
 * includes limb animation, model rotations and entity translation. Only geometry with stable
 * topology contributes. Births/topology changes retain camera flow and are counted explicitly.
 */
public final class EntityMotionGeometry {
    private record Key(Object identity, Object model, Object layer, int occurrence) {}

    private record Layer(Object identity, Object model, Object layer) {}

    private static Map<Key, FloatArrayList> previous = new HashMap<>(), current = new HashMap<>();
    private static final Map<Layer, Integer> OCCURRENCES = new HashMap<>();

    private record TaggedSubmit(Object identity, int ordinal) {}

    private record BlockIdentity(long position) {}

    private static final Object UNKNOWN = new Object();
    private static final ThreadLocal<java.util.ArrayDeque<Object>> CONTEXT =
            ThreadLocal.withInitial(java.util.ArrayDeque::new);
    private static final java.util.IdentityHashMap<Object, TaggedSubmit> SUBMISSIONS =
            new java.util.IdentityHashMap<>();
    private static final Map<Object, Integer> SUBMISSION_COUNTS = new HashMap<>();
    private static Map<Key, FloatArrayList> recycled = new HashMap<>();
    private static boolean accepting;
    private static boolean enabled;
    private record Match(FloatArrayList now, FloatArrayList old, int primitiveVertices) {}
    @FunctionalInterface interface MatchConsumer {
        void accept(FloatArrayList now, FloatArrayList old, int primitiveVertices);
    }
    private static List<Match> matches;
    private static final Set<FloatArrayList> TRUNCATED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static int vertexBudget = 1048576;
    private static int cappedMeshes, unidentifiedMeshes, newMeshes, changedTopologyMeshes, stableMeshes, matchedVertices;

    public static boolean enterEntity(int id) {
        if (!accepting) return false;
        CONTEXT.get().push(id < 0 ? UNKNOWN : Integer.valueOf(id));
        return true;
    }

    public static boolean enterBlock(long position) {
        if (!accepting) return false;
        CONTEXT.get().push(new BlockIdentity(position));
        return true;
    }

    public static void exitSubmission() {
        if (!CONTEXT.get().isEmpty()) CONTEXT.get().pop();
    }

    public static void tagSubmission(Object submit) {
        if (!accepting || CONTEXT.get().isEmpty() || SUBMISSIONS.containsKey(submit)) return;
        Object identity = CONTEXT.get().peek();
        if (identity == UNKNOWN) return;
        int ordinal = SUBMISSION_COUNTS.merge(identity, 1, Integer::sum) - 1;
        SUBMISSIONS.put(submit, new TaggedSubmit(identity, ordinal));
    }

    public static void endWorldCapture() {
        accepting = false;
    }

    public static VertexConsumer captureSubmitted(
            Object submit, Object kind, Object layer, VertexConsumer delegate) {
        if (!accepting) return delegate;
        if (vertices >= vertexBudget) { cappedMeshes++; return delegate; }
        TaggedSubmit tagged = SUBMISSIONS.get(submit);
        if (tagged == null
                && submit instanceof MovingBlockFeatureRenderer.Submit block
                && block.movingBlockRenderState() instanceof EntityTemporalState state
                && state.saltsAntiAliasing$entityId() >= 0) {
            tagged = new TaggedSubmit(state.saltsAntiAliasing$entityId(), 0);
        }
        if (tagged == null) { unidentifiedMeshes++; return delegate; }
        Key key = new Key(tagged.identity(), kind, layer, tagged.ordinal());
        FloatArrayList positions = current.get(key);
        if (positions == null) {
            positions = positions(key);
            current.put(key, positions);
        }
        return wrap(delegate, positions);
    }

    private static FloatArrayList positions(Key key) {
        FloatArrayList positions = recycled.remove(key);
        if (positions == null) return new FloatArrayList();
        positions.clear();
        return positions;
    }

    private static int vertices;

    private EntityMotionGeometry() {}

    public static void beginFrame(AntiAliasingConfig config) {
        enabled = config.mode.usesHistoryBuffers() && config.tuning.entityMotion;
        vertexBudget = config.tuning.motionVertexBudget.vertices();
        matches = null;
        TRUNCATED.clear();
        cappedMeshes = unidentifiedMeshes = newMeshes = changedTopologyMeshes = stableMeshes = matchedVertices = 0;
        recycled = previous;
        previous = current;
        current = new HashMap<>();
        OCCURRENCES.clear();
        vertices = 0;
        accepting = enabled;
        SUBMISSIONS.clear();
        SUBMISSION_COUNTS.clear();
        if (!enabled) {
            previous.clear();
            recycled.clear();
            VulkanEntityMotionRenderer.release();
        }
    }

    public static VertexConsumer capture(
            ModelFeatureRenderer.Submit<?> submit, VertexConsumer delegate) {
        if (!accepting) return delegate;
        if (vertices >= vertexBudget) { cappedMeshes++; return delegate; }
        Object identity = null;
        if (submit.state() instanceof EntityRenderState state
                && state instanceof EntityTemporalState temporal
                && temporal.saltsAntiAliasing$entityId() >= 0)
            identity = Integer.valueOf(temporal.saltsAntiAliasing$entityId());
        else if (SUBMISSIONS.containsKey(submit)) identity = SUBMISSIONS.get(submit).identity();
        if (identity == null) { unidentifiedMeshes++; return delegate; }
        Layer layer = new Layer(identity, submit.model(), submit.renderType());
        int occurrence = OCCURRENCES.merge(layer, 1, Integer::sum) - 1;
        Key key = new Key(layer.identity(), layer.model(), layer.layer(), occurrence);
        FloatArrayList positions = positions(key);
        current.put(key, positions);
        return wrap(delegate, positions);
    }

    private static VertexConsumer wrap(VertexConsumer delegate, FloatArrayList positions) {
        return new VertexConsumer() {
            public VertexConsumer addVertex(float x, float y, float z) {
                delegate.addVertex(x, y, z);
                if (vertices++ < vertexBudget) {
                    positions.add(x);
                    positions.add(y);
                    positions.add(z);
                } else TRUNCATED.add(positions);
                return this;
            }

            public VertexConsumer setColor(int r, int g, int b, int a) {
                delegate.setColor(r, g, b, a);
                return this;
            }

            public VertexConsumer setColor(int color) {
                delegate.setColor(color);
                return this;
            }

            public VertexConsumer setUv(float u, float v) {
                delegate.setUv(u, v);
                return this;
            }

            public VertexConsumer setUv1(int u, int v) {
                delegate.setUv1(u, v);
                return this;
            }

            public VertexConsumer setUv2(int u, int v) {
                delegate.setUv2(u, v);
                return this;
            }

            public VertexConsumer setUv3(float u, float v) {
                delegate.setUv3(u, v);
                return this;
            }

            public VertexConsumer setNormal(float x, float y, float z) {
                delegate.setNormal(x, y, z);
                return this;
            }

            public VertexConsumer setLineWidth(float width) {
                delegate.setLineWidth(width);
                return this;
            }
        };
    }

    static int forEachMatched(MatchConsumer consumer) {
        if (!enabled) return 0;
        if (matches == null) collectMatches();
        int drawVertices = 0;
        for (Match match : matches) {
            consumer.accept(match.now(), match.old(), match.primitiveVertices());
            drawVertices += match.now().size() / (match.primitiveVertices() * 3)
                    * (match.primitiveVertices() == 4 ? 6 : 3);
        }
        return drawVertices;
    }

    private static void collectMatches() {
        matches = new ArrayList<>();
        float[] shift = VulkanSceneTemporalController.instance().cameraTranslationToPreviousArray();
        for (var entry : current.entrySet()) {
            FloatArrayList old = previous.get(entry.getKey()), now = entry.getValue();
            int primitiveVertices = 4;
            if (entry.getKey().layer() instanceof net.minecraft.client.renderer.rendertype.RenderType layer) {
                primitiveVertices = switch (layer.primitiveTopology()) {
                    case QUADS -> 4;
                    case TRIANGLES -> 3;
                    default -> 0;
                };
            }
            if (TRUNCATED.contains(now)) { cappedMeshes++; continue; }
            if (old == null) { newMeshes++; continue; }
            if (primitiveVertices == 0 || old.size() != now.size()
                    || now.size() % (primitiveVertices * 3) != 0) { changedTopologyMeshes++; continue; }
            stableMeshes++;
            boolean changed = false;
            for (int i = 0; i < now.size(); i++)
                if (Math.abs(now.getFloat(i) - old.getFloat(i) - shift[i % 3]) > 0.00001f) {
                    changed = true;
                    break;
                }
            if (!changed) continue;
            matches.add(new Match(now, old, primitiveVertices));
            matchedVertices += now.size() / 3;
        }
        if (cappedMeshes > 0) org.betterLostItems.salts_anti_aliasing.client.metrics.MetricsDiagnostics.warnOnce(
                "motion-cap-" + vertexBudget, "temporal.geometry", "capture_budget_reached",
                "budget", vertexBudget, "cappedMeshes", cappedMeshes);
    }

    public record Coverage(int capturedVertices, int movingVertices, int stableMeshes,
                           int newMeshes, int topologyChanges, int cappedMeshes, int unidentifiedMeshes, int vertexBudget) {}
    public static Coverage coverage() {
        return new Coverage(Math.min(vertices, vertexBudget), matchedVertices, stableMeshes,
                newMeshes, changedTopologyMeshes, cappedMeshes, unidentifiedMeshes, vertexBudget);
    }
}
