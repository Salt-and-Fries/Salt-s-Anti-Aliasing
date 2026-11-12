package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.gl.GlTexture;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(value = GlTexture.class, remap = false)
public interface GlTextureAccessor { @Accessor("id") int salts$id(); }
