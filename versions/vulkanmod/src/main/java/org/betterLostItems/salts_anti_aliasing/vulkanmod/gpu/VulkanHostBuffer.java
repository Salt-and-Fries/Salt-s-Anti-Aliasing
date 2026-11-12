package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.vma.VmaAllocationCreateInfo;
import org.lwjgl.util.vma.VmaAllocationInfo;
import org.lwjgl.vulkan.VkBufferCreateInfo;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

import static org.lwjgl.util.vma.Vma.*;
import static org.lwjgl.vulkan.VK10.*;

/** Persistently mapped host allocation; only update after the owning frame fence completes. */
public final class VulkanHostBuffer implements AutoCloseable {
    // VMA changed these bindings from void to VkResult across VulkanMod releases.
    private static final MethodHandle FLUSH = mappedOperation("vmaFlushAllocation");
    private static final MethodHandle INVALIDATE = mappedOperation("vmaInvalidateAllocation");
    private final VulkanContext context;
    public final long buffer;
    private final long allocation;
    private final ByteBuffer mapped;
    private boolean closed;

    public VulkanHostBuffer(VulkanContext context, int bytes, int usage) {
        if (bytes < 1) throw new IllegalArgumentException("Empty buffer");
        this.context = context;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var info = VkBufferCreateInfo.calloc(stack).sType$Default().size(bytes)
                    .usage(usage).sharingMode(VK_SHARING_MODE_EXCLUSIVE);
            var alloc = VmaAllocationCreateInfo.calloc(stack).usage(VMA_MEMORY_USAGE_CPU_TO_GPU)
                    .flags(VMA_ALLOCATION_CREATE_MAPPED_BIT);
            var buffer = stack.mallocLong(1); var allocation = stack.mallocPointer(1);
            var allocationInfo = VmaAllocationInfo.calloc(stack);
            VulkanContext.check(vmaCreateBuffer(context.allocator(), info, alloc, buffer, allocation, allocationInfo), "Create mapped buffer");
            this.buffer = buffer.get(0); this.allocation = allocation.get(0);
            if (allocationInfo.pMappedData() == 0) {
                vmaDestroyBuffer(context.allocator(), this.buffer, this.allocation);
                throw new IllegalStateException("Host buffer could not be mapped");
            }
            mapped = MemoryUtil.memByteBuffer(allocationInfo.pMappedData(), bytes).order(ByteOrder.nativeOrder());
        }
    }

    public void write(ByteBuffer source) {
        if (closed || source.remaining() > mapped.capacity()) throw new IllegalArgumentException("Invalid mapped-buffer write");
        mapped.clear(); mapped.put(source.duplicate());
        while (mapped.hasRemaining()) mapped.put((byte) 0);
        mappedOperation(FLUSH);
    }

    public ByteBuffer read() {
        if (closed) throw new IllegalStateException("Buffer is closed");
        mappedOperation(INVALIDATE);
        return mapped.duplicate().clear().order(ByteOrder.nativeOrder());
    }

    private static MethodHandle mappedOperation(String name) {
        try {
            var method = org.lwjgl.util.vma.Vma.class.getMethod(name, long.class, long.class, long.class, long.class);
            return MethodHandles.publicLookup().unreflect(method)
                    .asType(MethodType.methodType(int.class, long.class, long.class, long.class, long.class));
        } catch (ReflectiveOperationException e) { throw new ExceptionInInitializerError(e); }
    }

    private void mappedOperation(MethodHandle operation) {
        try {
            int result = (int) operation.invokeExact(context.allocator(), allocation, 0L, (long) mapped.capacity());
            VulkanContext.check(result, "Synchronize mapped VMA memory");
        }
        catch (Throwable e) { throw new IllegalStateException("VMA mapped-memory synchronization failed", e); }
    }

    @Override public void close() {
        if (closed) return;
        closed = true;
        vmaDestroyBuffer(context.allocator(), buffer, allocation);
    }
}
