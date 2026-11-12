package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.render.engine.VkFbo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
@Mixin(value = VkFbo.class, remap = false)
public interface VkFboAccessor { @Invoker("bind") void salts$bind(); }
