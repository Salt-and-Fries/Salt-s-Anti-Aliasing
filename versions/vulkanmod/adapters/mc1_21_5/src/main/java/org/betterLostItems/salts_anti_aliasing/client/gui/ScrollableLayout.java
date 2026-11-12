package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.layouts.*;
import net.minecraft.client.gui.narration.*;
import net.minecraft.network.chat.Component;
import java.util.*;
import java.util.function.Consumer;

/** A scrolling viewport for the runtime setup and complete license documents on older clients. */
final class ScrollableLayout implements Layout {
    private final Layout content;
    private final Viewport viewport;
    private int maxHeight, minWidth;

    ScrollableLayout(Minecraft minecraft, Layout content, int height) {
        this.content = content; maxHeight = height;
        viewport = new Viewport();
        content.visitWidgets(viewport.widgets::add);
    }
    public void setMaxHeight(int height) { maxHeight = height; }
    public void setMinWidth(int width) { minWidth = width; }
    public void arrangeElements() {
        content.arrangeElements();
        viewport.setWidth(Math.max(minWidth, content.getWidth() + 18));
        viewport.setHeight(Math.min(maxHeight, content.getHeight()));
        viewport.refreshScrollAmount();
        positionContent();
    }
    private void positionContent() {
        content.setX(viewport.getX() + Math.max(0, (viewport.getWidth() - 18 - content.getWidth()) / 2));
        content.setY(viewport.getY() - (int) viewport.scrollAmount());
    }
    public void visitChildren(Consumer<LayoutElement> consumer) { consumer.accept(viewport); }
    public int getX() { return viewport.getX(); }
    public int getY() { return viewport.getY(); }
    public int getWidth() { return viewport.getWidth(); }
    public int getHeight() { return viewport.getHeight(); }
    public void setX(int x) { viewport.setX(x); positionContent(); }
    public void setY(int y) { viewport.setY(y); positionContent(); }

    private final class Viewport extends AbstractContainerWidget {
        final List<AbstractWidget> widgets = new ArrayList<>();
        Viewport() { super(0, 0, 1, 1, Component.empty()); }
        public List<? extends GuiEventListener> children() { return widgets; }
        protected int contentHeight() { return content.getHeight(); }
        protected double scrollRate() { return 20; }
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float tick) {
            positionContent();
            graphics.enableScissor(getX(), getY(), getRight(), getBottom());
            for (var widget : widgets) widget.render(graphics, mouseX, mouseY, tick);
            graphics.disableScissor();
            renderScrollbar(graphics);
        }
        public boolean mouseClicked(double x, double y, int button) {
            return isMouseOver(x, y) && super.mouseClicked(x, y, button);
        }
        public void setFocused(GuiEventListener listener) {
            super.setFocused(listener);
            if (listener instanceof AbstractWidget widget) {
                if (widget.getY() < getY()) setScrollAmount(scrollAmount() + widget.getY() - getY());
                else if (widget.getBottom() > getBottom()) setScrollAmount(scrollAmount() + widget.getBottom() - getBottom());
                positionContent();
            }
        }
        protected void updateWidgetNarration(NarrationElementOutput output) {
            if (getFocused() instanceof NarratableEntry entry) entry.updateNarration(output);
        }
    }
}
