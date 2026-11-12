package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
public abstract class LegacyWidget extends AbstractWidget implements LayoutElement {
    protected LegacyWidget(int x,int y,int width,int height,Component text){super(x,y,width,height,text);}
    public void setFocused(boolean value){super.setFocused(value);}
}
