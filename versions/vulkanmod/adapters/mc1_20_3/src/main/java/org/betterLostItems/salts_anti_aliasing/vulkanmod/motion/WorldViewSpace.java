package org.betterLostItems.salts_anti_aliasing.vulkanmod.motion;

/** Older entity consumers receive positions with the world view already applied. */
public final class WorldViewSpace {
    private static final org.joml.Matrix4f INVERSE = new org.joml.Matrix4f();
    private WorldViewSpace() {}
    public static void capture(org.joml.Matrix4fc view) { INVERSE.set(view).invert(); }
    public static void cameraRelative(org.joml.Vector3f position) { INVERSE.transformPosition(position); }
}
