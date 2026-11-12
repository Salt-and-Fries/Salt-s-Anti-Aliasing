package org.betterLostItems.salts_anti_aliasing.vulkanmod.motion;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Forwards attributes unchanged while recording the final, transformed position exactly once. */
public class MotionCapturingVertexConsumer implements VertexConsumer, net.vulkanmod.interfaces.ExtendedVertexBuilder {
    public interface Positions {
        void vertex(float x, float y, float z);
        void invalidate();
        default int bulkLimit(int requested) { return requested; }
    }

    protected final VertexConsumer delegate;
    protected final Positions positions;
    private final org.joml.Vector3f cameraRelative = new org.joml.Vector3f();

    public MotionCapturingVertexConsumer(VertexConsumer delegate, Positions positions) {
        this.delegate = delegate;
        this.positions = positions;
    }

    public VertexConsumer vertex(double x, double y, double z) {
        delegate.vertex(x, y, z);
        record((float) x, (float) y, (float) z);
        return this;
    }
    protected void record(float x, float y, float z) {
        WorldViewSpace.cameraRelative(cameraRelative.set(x, y, z));
        positions.vertex(cameraRelative.x, cameraRelative.y, cameraRelative.z);
    }
    // These VulkanMod releases cast model consumers to their packed-vertex API.
    public void vertex(float x, float y, float z, int color, float u, float v, int overlay, int light, int normal) {
        if (delegate instanceof net.vulkanmod.interfaces.ExtendedVertexBuilder fast) {
            fast.vertex(x, y, z, color, u, v, overlay, light, normal);
        } else {
            delegate.vertex(x, y, z).color(color & 255, color >>> 8 & 255, color >>> 16 & 255, color >>> 24)
                    .uv(u, v).overlayCoords(overlay).uv2(light)
                    .normal((byte) normal / 127f, (byte) (normal >>> 8) / 127f, (byte) (normal >>> 16) / 127f).endVertex();
        }
        record(x, y, z);
    }
    public void vertex(float x, float y, float z, float u, float v, int color, int light) {
        delegate.vertex(x, y, z).uv(u, v)
                .color(color & 255, color >>> 8 & 255, color >>> 16 & 255, color >>> 24).uv2(light).endVertex();
        record(x, y, z);
    }
    public VertexConsumer color(int r, int g, int b, int a) { delegate.color(r,g,b,a); return this; }
    public VertexConsumer uv(float u, float v) { delegate.uv(u,v); return this; }
    public VertexConsumer overlayCoords(int u, int v) { delegate.overlayCoords(u,v); return this; }
    public VertexConsumer uv2(int u, int v) { delegate.uv2(u,v); return this; }
    public VertexConsumer normal(float x, float y, float z) { delegate.normal(x,y,z); return this; }
    public void endVertex() { delegate.endVertex(); }
    public void defaultColor(int r, int g, int b, int a) { delegate.defaultColor(r,g,b,a); }
    public void unsetDefaultColor() { delegate.unsetDefaultColor(); }
}
