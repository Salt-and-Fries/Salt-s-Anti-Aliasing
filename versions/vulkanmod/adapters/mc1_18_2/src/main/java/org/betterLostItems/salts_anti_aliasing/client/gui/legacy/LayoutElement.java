package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import net.minecraft.client.gui.components.AbstractWidget;
import java.util.function.Consumer;
/** Explicit widget forwarding survives Minecraft method-name remapping in distributables. */
public interface LayoutElement {
    default int getWidth() { return ((AbstractWidget) this).getWidth(); }
    default int getHeight() { return ((AbstractWidget) this).getHeight(); }
    int getX(); int getY(); void setX(int value); void setY(int value);
    default void visitWidgets(Consumer<AbstractWidget> consumer) { if (this instanceof AbstractWidget widget) consumer.accept(widget); }
}
