package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.screens.Screen;
import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.TooltipRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(Screen.class)
abstract class LegacyScreenTooltipMixin {
    @Inject(method="render",at=@At("HEAD"))
    private void salts$begin(PoseStack pose,int x,int y,float tick,CallbackInfo ci){TooltipRegistry.begin();}
    @Inject(method="render",at=@At("TAIL"))
    private void salts$tip(PoseStack pose,int x,int y,float tick,CallbackInfo ci){TooltipRegistry.render((Screen)(Object)this,pose,x,y);}
}
