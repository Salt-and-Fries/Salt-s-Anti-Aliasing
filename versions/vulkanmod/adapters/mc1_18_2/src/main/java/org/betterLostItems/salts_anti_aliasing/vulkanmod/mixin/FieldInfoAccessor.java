package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.shader.layout.Field;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=Field.FieldInfo.class,remap=false)
public interface FieldInfoAccessor {
    @Accessor("name") String salts$name();
}
