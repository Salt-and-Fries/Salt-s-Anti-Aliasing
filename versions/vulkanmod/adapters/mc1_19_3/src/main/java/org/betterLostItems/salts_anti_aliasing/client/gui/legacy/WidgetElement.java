package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import net.minecraft.client.gui.components.AbstractWidget;
import java.util.function.Consumer;
public final class WidgetElement implements LayoutElement {
    private final AbstractWidget widget;
    private WidgetElement(AbstractWidget widget) { this.widget = widget; }
    public static LayoutElement of(Object value) {
        if (value instanceof LayoutElement element) return element;
        if (value instanceof AbstractWidget widget) return new WidgetElement(widget);
        throw new IllegalArgumentException("Unsupported layout child: " + value);
    }
    public int getX() { return widget.getX(); } public int getY() { return widget.getY(); }
    public void setX(int value) { widget.setX(value); } public void setY(int value) { widget.setY(value); }
    public int getWidth() { return widget.getWidth(); } public int getHeight() { return widget.getHeight(); }
    public void visitWidgets(Consumer<AbstractWidget> consumer) { consumer.accept(widget); }
}
