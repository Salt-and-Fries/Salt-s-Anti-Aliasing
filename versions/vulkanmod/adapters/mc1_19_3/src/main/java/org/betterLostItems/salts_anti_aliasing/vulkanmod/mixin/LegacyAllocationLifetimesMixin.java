package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.NativeResourceLifetimes;
import org.lwjgl.vulkan.*;
import org.lwjgl.util.vma.*;
import org.lwjgl.PointerBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.nio.LongBuffer;
@Mixin(value=net.vulkanmod.vulkan.memory.MemoryManager.class,remap=false)
abstract class LegacyAllocationLifetimesMixin {
    @WrapOperation(method="*",at=@At(value="INVOKE",target="Lorg/lwjgl/util/vma/Vma;vmaCreateBuffer(JLorg/lwjgl/vulkan/VkBufferCreateInfo;Lorg/lwjgl/util/vma/VmaAllocationCreateInfo;Ljava/nio/LongBuffer;Lorg/lwjgl/PointerBuffer;Lorg/lwjgl/util/vma/VmaAllocationInfo;)I"))
    private int salts$createBuffer(long allocator,VkBufferCreateInfo info,VmaAllocationCreateInfo allocationInfo,LongBuffer handles,PointerBuffer allocations,VmaAllocationInfo output,Operation<Integer> original){
        int result=original.call(allocator,info,allocationInfo,handles,allocations,output);
        if(result==0){long handle=handles.get(0),allocation=allocations.get(0);NativeResourceLifetimes.created("VmaBuffer",handle,13,()->Vma.vmaDestroyBuffer(allocator,handle,allocation));}
        return result;
    }
    @WrapOperation(method="*",at=@At(value="INVOKE",target="Lorg/lwjgl/util/vma/Vma;vmaDestroyBuffer(JJJ)V"))
    private void salts$freeBuffer(long allocator,long handle,long allocation,Operation<Void> original){
        NativeResourceLifetimes.destroyed("VmaBuffer",handle);original.call(allocator,handle,allocation);
    }
    @WrapOperation(method="*",at=@At(value="INVOKE",target="Lorg/lwjgl/util/vma/Vma;vmaCreateImage(JLorg/lwjgl/vulkan/VkImageCreateInfo;Lorg/lwjgl/util/vma/VmaAllocationCreateInfo;Ljava/nio/LongBuffer;Lorg/lwjgl/PointerBuffer;Lorg/lwjgl/util/vma/VmaAllocationInfo;)I"))
    private int salts$createImage(long allocator,VkImageCreateInfo info,VmaAllocationCreateInfo allocationInfo,LongBuffer handles,PointerBuffer allocations,VmaAllocationInfo output,Operation<Integer> original){
        int result=original.call(allocator,info,allocationInfo,handles,allocations,output);
        if(result==0){long handle=handles.get(0),allocation=allocations.get(0);NativeResourceLifetimes.created("VmaImage",handle,14,()->Vma.vmaDestroyImage(allocator,handle,allocation));}
        return result;
    }
    @WrapOperation(method="*",at=@At(value="INVOKE",target="Lorg/lwjgl/util/vma/Vma;vmaDestroyImage(JJJ)V"))
    private void salts$freeImage(long allocator,long handle,long allocation,Operation<Void> original){
        NativeResourceLifetimes.destroyed("VmaImage",handle);original.call(allocator,handle,allocation);
    }
}
