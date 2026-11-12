package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
public final class LegacyEditBox extends EditBox {
    private Component hint;
    public LegacyEditBox(Font font,int x,int y,int width,int height,Component label){super(font,x,y,width,height,label);}
    public void setHint(Component value){hint=value;}
    public void setTooltip(Tooltip value){TooltipRegistry.set(this,value);}
    public void renderButton(PoseStack pose,int mouseX,int mouseY,float tick){setSuggestion(getValue().isEmpty()&&hint!=null?hint.getString():null);super.renderButton(pose,mouseX,mouseY,tick);}
}
