package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
public abstract class LegacySlider extends AbstractSliderButton implements LayoutElement {
    protected LegacySlider(int x, int y, int width, int height, Component text, double value) { super(x,y,width,height,text,value); }
    public int getX() { return x; } public int getY() { return y; }
    public void setX(int value) { x = value; } public void setY(int value) { y = value; }
    public void setTooltip(Tooltip tip) { TooltipRegistry.set(this, tip); }
    public void updateNarration(NarrationElementOutput output) { super.updateNarration(output); TooltipRegistry.narrate(this, output); }
}
