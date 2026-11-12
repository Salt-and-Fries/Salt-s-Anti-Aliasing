package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.Drawer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value=Drawer.class,remap=false)
public interface DrawerAccessor {
    @org.spongepowered.asm.mixin.gen.Accessor("commandBuffers") static java.util.List<org.lwjgl.vulkan.VkCommandBuffer> salts$commands(){throw new AssertionError("Mixin accessor");}
    @Invoker("recreateSwapChain") void salts$recreateSwapChain();
}
