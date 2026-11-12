package org.betterLostItems.salts_anti_aliasing.validation.mixin;
import net.vulkanmod.vulkan.Vulkan;
import net.vulkanmod.vulkan.memory.MemoryManager;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.VulkanContext;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=MemoryManager.class,remap=false)
abstract class ResourceAllocationNamesMixin {
    @Unique private static String salts$caller(){
        return java.util.Arrays.stream(Thread.currentThread().getStackTrace())
            .filter(f->!f.getClassName().contains("MemoryManager")&&!f.getClassName().equals("java.lang.Thread"))
            .limit(10).map(StackTraceElement::toString).collect(java.util.stream.Collectors.joining(" / "));
    }
    @Inject(method="createBuffer(JIILjava/nio/LongBuffer;Lorg/lwjgl/PointerBuffer;)V",at=@At("RETURN"))
    private void salts$buffer(long bytes,int usage,int properties,java.nio.LongBuffer buffer,org.lwjgl.PointerBuffer allocation,CallbackInfo info){
        new VulkanContext(Vulkan.getDevice(),Vulkan.getAllocator()).debugName(org.lwjgl.vulkan.VK10.VK_OBJECT_TYPE_BUFFER,buffer.get(0),"VulkanMod buffer bytes="+bytes+" "+salts$caller());
    }
}
