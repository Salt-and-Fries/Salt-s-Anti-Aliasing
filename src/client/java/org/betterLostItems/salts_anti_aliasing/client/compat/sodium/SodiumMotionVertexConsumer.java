package org.betterLostItems.salts_anti_aliasing.client.compat.sodium;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.MotionCapturingVertexConsumer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

/** Loaded only when Sodium is installed. Keep its bulk writer rather than forcing scalar emission. */
public final class SodiumMotionVertexConsumer extends MotionCapturingVertexConsumer implements VertexBufferWriter {
    private final VertexBufferWriter writer;

    public SodiumMotionVertexConsumer(VertexConsumer delegate, Positions positions) {
        super(delegate, positions);
        writer = delegate instanceof VertexBufferWriter bulk ? bulk : null;
    }

    @Override
    public boolean canUseIntrinsics() {
        return writer != null && writer.canUseIntrinsics();
    }

    @Override
    public void push(MemoryStack stack, long ptr, int count, VertexFormat format) {
        if (!canUseIntrinsics()) throw new IllegalStateException("The nested consumer cannot accept bulk vertices");
        var position = format.getElement("Position");
        int stride = format.getVertexSize();
        // Entity formats use float3 positions. An unfamiliar encoding must never be read as floats,
        // nor may partial geometry be matched against a previous frame. Rendering still proceeds.
        if (position == null || position.format() != GpuFormat.RGB32_FLOAT
                || position.offset() < 0 || position.offset() + 12 > stride
                || count < 0 || (count > 0 && ptr == 0)) {
            positions.invalidate();
        } else {
            // Sodium explicitly allows the delegate to overwrite ptr. Consume it before forwarding;
            // retain only copied positions, never a pointer into the caller's MemoryStack.
            long vertex = ptr + position.offset();
            int capturing = Math.max(0, Math.min(count, positions.bulkLimit(count)));
            for (int i = 0; i < capturing; i++, vertex += stride) {
                positions.vertex(MemoryUtil.memGetFloat(vertex), MemoryUtil.memGetFloat(vertex + 4),
                        MemoryUtil.memGetFloat(vertex + 8));
            }
        }
        writer.push(stack, ptr, count, format);
    }
}
