package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import java.util.function.Supplier;
import net.vulkanmod.vulkan.shader.*;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Vec1f.class,remap=false)
abstract class TerrainMipMixin {
    @Inject(method="setFunction",at=@At("RETURN"))
    private void salts$uniform(CallbackInfo info) {
        var field=(FieldAccessor)this;
        if(((FieldInfoAccessor)field.salts$info()).salts$name().equals("SaltsMipBias"))
            field.salts$supplier(()->SaltsVulkanModClient.mipBiasQuarterSteps()*.25f);
    }
}
