package org.betterLostItems.salts_anti_aliasing.client.gui;

import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.*;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.network.chat.Component;

/** Container event routing for releases before vanilla's AbstractContainerWidget. */
abstract class LegacyContainerWidget extends LegacyWidget implements ContainerEventHandler {
    private GuiEventListener child;
    private boolean dragging;
    LegacyContainerWidget(int x, int y, int width, int height, Component text) { super(x, y, width, height, text); }
    public int getBottom() { return getY() + getHeight(); }
    public int getRight() { return getX() + getWidth(); }
    public void setHeight(int height) { this.height = height; }
    public GuiEventListener getFocused() { return child; }
    public void setFocused(GuiEventListener next) {
        if (child == next) return;
        if (child instanceof AbstractWidget widget && widget.isFocused()) child.changeFocus(true);
        child = next;
        if (child instanceof AbstractWidget widget && !widget.isFocused()) child.changeFocus(true);
    }
    public boolean isDragging() { return dragging; }
    public void setDragging(boolean value) { dragging = value; }
    public boolean mouseClicked(double x, double y, int button) { return ContainerEventHandler.super.mouseClicked(x, y, button); }
    public boolean mouseReleased(double x, double y, int button) { return ContainerEventHandler.super.mouseReleased(x, y, button); }
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return ContainerEventHandler.super.mouseDragged(x, y, button, dx, dy); }
    public boolean mouseScrolled(double x, double y, double amount) { return ContainerEventHandler.super.mouseScrolled(x, y, amount); }
    public boolean changeFocus(boolean direction) { return ContainerEventHandler.super.changeFocus(direction); }
    public boolean keyPressed(int key, int scan, int modifiers) { return ContainerEventHandler.super.keyPressed(key, scan, modifiers); }
    public boolean keyReleased(int key, int scan, int modifiers) { return ContainerEventHandler.super.keyReleased(key, scan, modifiers); }
    public boolean charTyped(char value, int modifiers) { return ContainerEventHandler.super.charTyped(value, modifiers); }
}
