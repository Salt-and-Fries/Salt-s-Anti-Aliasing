package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import net.minecraft.client.gui.components.AbstractWidget;
import java.util.function.Consumer;
/** Explicit widget forwarding survives Minecraft method-name remapping in distributables. */
public interface LayoutElement {
    default int getWidth() { return ((AbstractWidget) this).getWidth(); }
    default int getHeight() { return ((AbstractWidget) this).getHeight(); }
    default int getX() { return ((AbstractWidget) this).getX(); }
    default int getY() { return ((AbstractWidget) this).getY(); }
    default void setX(int value) { ((AbstractWidget) this).setX(value); }
    default void setY(int value) { ((AbstractWidget) this).setY(value); }
    default void visitWidgets(Consumer<AbstractWidget> consumer) { if (this instanceof AbstractWidget widget) consumer.accept(widget); }
}
