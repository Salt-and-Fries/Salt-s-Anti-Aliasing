package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import net.minecraft.client.gui.screens.Screen;
import java.util.*;
import java.util.function.Consumer;
public final class HeaderAndFooterLayout implements Layout {
    private final Screen screen;
    private final List<LayoutElement> header = new ArrayList<>(), footer = new ArrayList<>(), contents = new ArrayList<>();
    private int headerHeight = 32, footerHeight = 32;
    public HeaderAndFooterLayout(Screen screen) { this.screen = screen; }
    public <T> T addToHeader(T value) { header.add(WidgetElement.of(value)); return value; }
    public <T> T addToFooter(T value) { footer.add(WidgetElement.of(value)); return value; }
    public <T> T addToContents(T value) { contents.add(WidgetElement.of(value)); return value; }
    public int getHeaderHeight() { return headerHeight; } public int getFooterHeight() { return footerHeight; }
    public void arrangeElements() {
        for (var child : header) place(child, (headerHeight - child.getHeight()) / 2);
        for (var child : footer) place(child, screen.height - footerHeight + (footerHeight - child.getHeight()) / 2);
        for (var child : contents) {
            if (child instanceof Layout layout) layout.arrangeElements();
            place(child, headerHeight + Math.max(0, (screen.height - headerHeight - footerHeight - child.getHeight()) / 2));
        }
    }
    private void place(LayoutElement child, int y) { child.setX((screen.width - child.getWidth()) / 2); child.setY(y); }
    public void visitChildren(Consumer<LayoutElement> consumer) { header.forEach(consumer); contents.forEach(consumer); footer.forEach(consumer); }
    public int getX() { return 0; } public int getY() { return 0; }
    public int getWidth() { return screen.width; } public int getHeight() { return screen.height; }
    public void setX(int value) { throw new UnsupportedOperationException("Layout is anchored to its screen"); }
    public void setY(int value) { throw new UnsupportedOperationException("Layout is anchored to its screen"); }
}
