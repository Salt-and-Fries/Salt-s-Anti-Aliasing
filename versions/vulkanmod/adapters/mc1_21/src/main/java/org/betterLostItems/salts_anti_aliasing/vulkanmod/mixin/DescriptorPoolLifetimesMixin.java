package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.NativeResourceLifetimes;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.nio.LongBuffer;
import static org.lwjgl.vulkan.VK10.*;
@Mixin(value=net.vulkanmod.vulkan.shader.DescriptorSets.class,remap=false)
abstract class DescriptorPoolLifetimesMixin {
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkCreateDescriptorPool(Lorg/lwjgl/vulkan/VkDevice;Lorg/lwjgl/vulkan/VkDescriptorPoolCreateInfo;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"),require=0)
    private static int salts$createDescriptorPool(VkDevice device,VkDescriptorPoolCreateInfo info,VkAllocationCallbacks allocator,LongBuffer handles,Operation<Integer> original){
        int result=original.call(device,info,allocator,handles);
        if(result==VK_SUCCESS){long handle=handles.get(0);NativeResourceLifetimes.created("DescriptorPool",handle,2,()->vkDestroyDescriptorPool(device,handle,null));}
        return result;
    }
    @WrapOperation(method={"*","<init>","<clinit>"},at=@At(value="INVOKE",target="Lorg/lwjgl/vulkan/VK10;vkDestroyDescriptorPool(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkAllocationCallbacks;)V"),require=0)
    private static void salts$destroyDescriptorPool(VkDevice device,long handle,VkAllocationCallbacks allocator,Operation<Void> original){
        NativeResourceLifetimes.destroyed("DescriptorPool",handle);original.call(device,handle,allocator);
    }
}
