package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Particle.class)
public interface ParticleAccessor {
    @Accessor("alpha") float salts$alpha();
    @Accessor("x") double salts$x();
    @Accessor("y") double salts$y();
    @Accessor("z") double salts$z();
    @Accessor("xo") double salts$xo();
    @Accessor("yo") double salts$yo();
    @Accessor("zo") double salts$zo();
    @Accessor("roll") float salts$roll();
    @Accessor("oRoll") float salts$previousRoll();
}
