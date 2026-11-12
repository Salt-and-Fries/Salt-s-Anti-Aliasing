package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.*;
import net.minecraft.network.chat.Component;

/** Container event routing for releases before vanilla's AbstractContainerWidget. */
abstract class LegacyContainerWidget extends AbstractWidget implements ContainerEventHandler {
    private GuiEventListener child;
    private boolean dragging;
    LegacyContainerWidget(int x, int y, int width, int height, Component text) { super(x, y, width, height, text); }
    public int getBottom() { return getY() + getHeight(); }
    public int getRight() { return getX() + getWidth(); }
    public void setHeight(int height) { this.height = height; }
    public GuiEventListener getFocused() { return child; }
    public void setFocused(GuiEventListener next) {
        if (child != null) child.setFocused(false);
        child = next;
        if (child != null) child.setFocused(true);
    }
    public boolean isDragging() { return dragging; }
    public void setDragging(boolean value) { dragging = value; }
    public boolean mouseClicked(double x, double y, int button) { return ContainerEventHandler.super.mouseClicked(x, y, button); }
    public boolean mouseReleased(double x, double y, int button) { return ContainerEventHandler.super.mouseReleased(x, y, button); }
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) { return ContainerEventHandler.super.mouseDragged(x, y, button, dx, dy); }
    public boolean mouseScrolled(double x, double y, double amount) { return ContainerEventHandler.super.mouseScrolled(x, y, amount); }
    public boolean keyPressed(int key, int scan, int modifiers) { return ContainerEventHandler.super.keyPressed(key, scan, modifiers); }
    public boolean keyReleased(int key, int scan, int modifiers) { return ContainerEventHandler.super.keyReleased(key, scan, modifiers); }
    public boolean charTyped(char value, int modifiers) { return ContainerEventHandler.super.charTyped(value, modifiers); }
    public net.minecraft.client.gui.ComponentPath nextFocusPath(net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
        return ContainerEventHandler.super.nextFocusPath(event);
    }
    public net.minecraft.client.gui.ComponentPath getCurrentFocusPath() { return ContainerEventHandler.super.getCurrentFocusPath(); }
}
