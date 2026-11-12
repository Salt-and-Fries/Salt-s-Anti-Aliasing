package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(targets="net.vulkanmod.vulkan.Vulkan$QueueFamilyIndices",remap=false)
public interface QueueFamiliesAccessor {
    @Accessor("graphicsFamily") Integer salts$graphics();
}
