package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import java.util.function.Supplier;
import net.vulkanmod.vulkan.shader.layout.*;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.SaltsVulkanModClient;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=Vec1f.class,remap=false)
abstract class TerrainMipMixin {
    @Shadow private Supplier<Float> floatSupplier;
    @Inject(method="setSupplier()V",at=@At("RETURN"))
    private void salts$uniform(CallbackInfo info) {
        if(((Field)(Object)this).getName().equals("SaltsMipBias"))
            floatSupplier=()->SaltsVulkanModClient.mipBiasQuarterSteps()*.25f;
    }
}
