package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.narration.*;
public class StringWidget extends LegacyWidget {
    protected final Font font;
    public StringWidget(Component text, Font font) { this(font.width(text),font.lineHeight,text,font); }
    public StringWidget(int width,int height,Component text,Font font) { super(0,0,width,height,text); this.font=font;active=false; }
    public void renderButton(PoseStack pose,int mouseX,int mouseY,float tick) { GuiComponent.drawCenteredString(pose,font,getMessage(),x+width/2,y+(height-font.lineHeight)/2,0xffffff); }
    protected void updateWidgetNarration(NarrationElementOutput output) { output.add(NarratedElementType.TITLE,getMessage()); }
}
