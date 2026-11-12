package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value = VulkanImage.class, remap = false)
abstract class VulkanImageUsageMixin implements org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyImageUsage {
    @Unique private int salts$usage;
    
    
    public int salts$usage() { return salts$usage; }
    @ModifyVariable(method = "createImage", at = @At("HEAD"), argsOnly = true, ordinal = 4)
    private int salts$transferUsage(int usage) {
        return salts$usage = usage | org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_SRC_BIT | org.lwjgl.vulkan.VK10.VK_IMAGE_USAGE_TRANSFER_DST_BIT;
    }
}
