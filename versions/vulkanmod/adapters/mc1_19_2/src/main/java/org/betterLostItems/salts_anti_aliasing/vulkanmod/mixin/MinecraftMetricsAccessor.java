package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(Minecraft.class)
public interface MinecraftMetricsAccessor {
    @Accessor("fps") static int salts$fps() { throw new AssertionError("Mixin accessor"); }
}
