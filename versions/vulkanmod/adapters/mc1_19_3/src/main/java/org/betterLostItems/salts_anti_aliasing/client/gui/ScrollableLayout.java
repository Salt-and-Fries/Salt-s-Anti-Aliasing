package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import org.betterLostItems.salts_anti_aliasing.client.gui.LegacyGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.components.events.GuiEventListener;
import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.*;
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

    private final class Viewport extends LegacyContainerWidget {
        final List<AbstractWidget> widgets = new ArrayList<>();
        private double scroll;
        private boolean draggingScrollbar;
        Viewport() { super(0, 0, 1, 1, Component.empty()); }
        public List<? extends GuiEventListener> children() { return widgets; }
        protected int contentHeight() { return content.getHeight(); }
        protected double scrollRate() { return 20; }
        double scrollAmount() { return scroll; }
        void refreshScrollAmount() { setScrollAmount(scroll); }
        void setScrollAmount(double value) {
            scroll = Math.max(0, Math.min(Math.max(0, contentHeight() - getHeight()), value));
            positionContent();
        }
        private int thumbHeight() {
            return Math.min(getHeight(), Math.max(20, getHeight() * getHeight() / Math.max(1, contentHeight())));
        }
        private void renderScrollbar(LegacyGraphics graphics) {
            if (contentHeight() <= getHeight()) return;
            int top = getY() + (int) (scroll / (contentHeight() - getHeight()) * (getHeight() - thumbHeight()));
            graphics.fill(getRight() - 8, getY(), getRight(), getBottom(), 0x80000000);
            graphics.fill(getRight() - 7, top, getRight() - 1, top + thumbHeight(), 0xffaaaaaa);
        }
        public boolean mouseScrolled(double x, double y, double vertical) {
            if (!isMouseOver(x, y)) return false;
            setScrollAmount(scroll - vertical * scrollRate());
            return true;
        }
        public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
            if (!draggingScrollbar) return super.mouseDragged(x, y, button, dx, dy);
            setScrollAmount(scroll + dy * Math.max(0, contentHeight() - getHeight()) / Math.max(1, getHeight() - thumbHeight()));
            return true;
        }
        public boolean mouseReleased(double x, double y, int button) {
            boolean wasDragging = draggingScrollbar;
            draggingScrollbar = false;
            return super.mouseReleased(x, y, button) || wasDragging;
        }
        public boolean keyPressed(int key, int scanCode, int modifiers) {
            if (key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP || key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_DOWN) {
                setScrollAmount(scroll + (key == org.lwjgl.glfw.GLFW.GLFW_KEY_PAGE_UP ? -getHeight() : getHeight()));
                return true;
            }
            return super.keyPressed(key, scanCode, modifiers);
        }
        public void renderButton(PoseStack poseStack, int mouseX, int mouseY, float tick) {
        var graphics = new LegacyGraphics(poseStack);
            positionContent();
            graphics.enableScissor(getX(), getY(), getRight(), getBottom());
            for (var widget : widgets) widget.render(graphics.pose(), mouseX, mouseY, tick);
            graphics.disableScissor();
            renderScrollbar(graphics);
        }
        public boolean mouseClicked(double x, double y, int button) {
            if (button == 0 && contentHeight() > getHeight() && isMouseOver(x, y) && x >= getRight() - 8) {
                draggingScrollbar = true;
                return true;
            }
            return isMouseOver(x, y) && super.mouseClicked(x, y, button);
        }
        public void setFocused(GuiEventListener listener) {
            super.setFocused(listener);
            if (listener instanceof AbstractWidget widget) {
                if (widget.getY() < getY()) setScrollAmount(scrollAmount() + widget.getY() - getY());
                else if ((widget.getY() + widget.getHeight()) > getBottom()) setScrollAmount(scrollAmount() + (widget.getY() + widget.getHeight()) - getBottom());
                positionContent();
            }
        }
        protected void updateWidgetNarration(NarrationElementOutput output) {
            if (getFocused() instanceof NarratableEntry entry) entry.updateNarration(output);
        }
    }
}
