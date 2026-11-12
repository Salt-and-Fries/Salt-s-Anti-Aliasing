package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.render.VBO;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.VulkanVboLifetimes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value=VBO.class,remap=false)
abstract class VboLifetimesMixin {
    @Inject(method="<init>",at=@At("RETURN"))
    private void salts$track(CallbackInfo info){VulkanVboLifetimes.created((VBO)(Object)this);}
    @Inject(method="close",at=@At("HEAD"))
    private void salts$release(CallbackInfo info){VulkanVboLifetimes.destroyed((VBO)(Object)this);}
}
