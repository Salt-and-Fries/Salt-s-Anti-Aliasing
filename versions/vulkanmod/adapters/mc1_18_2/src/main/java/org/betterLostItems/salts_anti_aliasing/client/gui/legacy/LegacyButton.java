package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.narration.NarrationElementOutput;
public final class LegacyButton extends net.minecraft.client.gui.components.Button implements LayoutElement {
    public interface OnPress { void onPress(LegacyButton button); }
    private LegacyButton(int x, int y, int width, int height, Component text, OnPress action) { super(x,y,width,height,text,b -> action.onPress((LegacyButton)b)); }
    public int getX() { return x; } public int getY() { return y; }
    public void setX(int value) { x=value; } public void setY(int value) { y=value; }
    public void setTooltip(Tooltip tip) { TooltipRegistry.set(this, tip); }
    public void updateNarration(NarrationElementOutput output) { super.updateNarration(output); TooltipRegistry.narrate(this, output); }
    public static Builder builder(Component text, OnPress action) { return new Builder(text, action); }
    public static final class Builder {
        private final Component text; private final OnPress action;
        private int x,y,width=150,height=20; private Tooltip tooltip;
        private Builder(Component text, OnPress action) { this.text=text;this.action=action; }
        public Builder bounds(int x,int y,int width,int height) { this.x=x;this.y=y;this.width=width;this.height=height;return this; }
        public Builder width(int value) { width=value;return this; }
        public Builder pos(int x,int y) { this.x=x;this.y=y;return this; }
        public Builder size(int width,int height) { this.width=width;this.height=height;return this; }
        public Builder tooltip(Tooltip value) { tooltip=value;return this; }
        public LegacyButton build() { var button=new LegacyButton(x,y,width,height,text,action); button.setTooltip(tooltip); return button; }
    }
}
