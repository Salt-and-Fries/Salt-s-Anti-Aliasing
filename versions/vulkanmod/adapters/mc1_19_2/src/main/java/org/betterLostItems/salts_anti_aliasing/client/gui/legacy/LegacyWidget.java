package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
public abstract class LegacyWidget extends AbstractWidget implements LayoutElement {
    protected LegacyWidget(int x, int y, int width, int height, Component text) { super(x, y, width, height, text); }
    public int getX() { return x; } public int getY() { return y; }
    public void setX(int value) { x = value; } public void setY(int value) { y = value; }
    public void setTooltip(Tooltip tip) { TooltipRegistry.set(this, tip); }
    public void setFocused(boolean value) { super.setFocused(value); }
    public final void updateNarration(NarrationElementOutput output) { updateWidgetNarration(output); TooltipRegistry.narrate(this, output); }
    protected abstract void updateWidgetNarration(NarrationElementOutput output);
}
