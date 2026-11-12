package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.texture.SamplerManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value=SamplerManager.class,remap=false)
public interface SamplerManagerAccessor {
    @Accessor("SAMPLERS") static it.unimi.dsi.fastutil.objects.Object2LongMap salts$nativeSamplers() { throw new AssertionError(); }
}
