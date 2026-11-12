package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import java.util.function.Supplier;
import net.vulkanmod.vulkan.shader.Field;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=Field.class,remap=false)
public interface FieldAccessor {
    @Accessor("fieldInfo") Field.FieldInfo salts$info();
    @Accessor("set") void salts$supplier(Supplier<Float> supplier);
}
