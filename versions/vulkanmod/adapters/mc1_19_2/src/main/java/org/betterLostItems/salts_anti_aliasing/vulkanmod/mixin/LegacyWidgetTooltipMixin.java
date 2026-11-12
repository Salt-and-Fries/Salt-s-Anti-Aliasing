package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.TooltipRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(AbstractWidget.class)
abstract class LegacyWidgetTooltipMixin {
    @Inject(method="render",at=@At("TAIL"))
    private void salts$hover(PoseStack pose,int mouseX,int mouseY,float tick,CallbackInfo ci){TooltipRegistry.rendered((AbstractWidget)(Object)this,mouseX,mouseY);}
}
