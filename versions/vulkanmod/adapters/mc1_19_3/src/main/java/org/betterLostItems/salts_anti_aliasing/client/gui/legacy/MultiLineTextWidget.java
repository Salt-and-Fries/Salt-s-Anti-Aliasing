package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.Font;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Component;
import java.util.List;
public final class MultiLineTextWidget extends StringWidget {
    private List<FormattedCharSequence> lines;
    public MultiLineTextWidget(Component text,Font font) { super(text,font); setMaxWidth(300); }
    public MultiLineTextWidget setMaxWidth(int value) {
        lines=font.split(getMessage(),Math.max(1,value)); width=Math.min(value,lines.stream().mapToInt(font::width).max().orElse(0));height=lines.size()*font.lineHeight;return this;
    }
    public void renderButton(PoseStack pose,int mouseX,int mouseY,float tick) { for(int i=0;i<lines.size();i++)font.drawShadow(pose,lines.get(i),getX(),getY()+i*font.lineHeight,0xffffff); }
}
