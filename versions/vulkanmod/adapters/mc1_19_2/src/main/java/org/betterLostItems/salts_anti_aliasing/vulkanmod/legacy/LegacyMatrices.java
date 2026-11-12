package org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy;

/** Mojang and JOML matrices meet through the same column-major buffer used by VulkanMod. */
public final class LegacyMatrices {
    private LegacyMatrices() {}
    public static org.joml.Matrix4f read(com.mojang.math.Matrix4f matrix) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var values = stack.mallocFloat(16); matrix.store(values);
            return new org.joml.Matrix4f(values);
        }
    }
    public static void write(com.mojang.math.Matrix4f target, org.joml.Matrix4f matrix) {
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var values = stack.mallocFloat(16); matrix.get(values); target.load(values);
        }
    }
}
