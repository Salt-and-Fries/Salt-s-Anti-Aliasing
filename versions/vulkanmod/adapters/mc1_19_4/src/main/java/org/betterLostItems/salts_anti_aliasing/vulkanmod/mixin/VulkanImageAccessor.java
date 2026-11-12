package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value = VulkanImage.class, remap = false)
public interface VulkanImageAccessor {
    @Accessor("currentLayout") int salts$layout();
    @Accessor("currentLayout") void salts$layout(int value);

}
