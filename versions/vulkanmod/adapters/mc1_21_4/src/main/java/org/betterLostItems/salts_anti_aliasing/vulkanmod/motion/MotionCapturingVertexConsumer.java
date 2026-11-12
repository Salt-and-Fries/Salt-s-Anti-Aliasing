package org.betterLostItems.salts_anti_aliasing.vulkanmod.motion;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Forwards attributes unchanged while recording the final, transformed position exactly once. */
public class MotionCapturingVertexConsumer implements VertexConsumer {
    public interface Positions {
        void vertex(float x, float y, float z);
        void invalidate();
        default int bulkLimit(int requested) { return requested; }
    }

    protected final VertexConsumer delegate;
    protected final Positions positions;

    public MotionCapturingVertexConsumer(VertexConsumer delegate, Positions positions) {
        this.delegate = delegate;
        this.positions = positions;
    }

    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        positions.vertex(x, y, z);
        return this;
    }
    public VertexConsumer setColor(int r, int g, int b, int a) { delegate.setColor(r,g,b,a); return this; }
    public VertexConsumer setColor(int color) { delegate.setColor(color); return this; }
    public VertexConsumer setUv(float u, float v) { delegate.setUv(u,v); return this; }
    public VertexConsumer setUv1(int u, int v) { delegate.setUv1(u,v); return this; }
    public VertexConsumer setUv2(int u, int v) { delegate.setUv2(u,v); return this; }
    
    public VertexConsumer setNormal(float x, float y, float z) { delegate.setNormal(x,y,z); return this; }
}
