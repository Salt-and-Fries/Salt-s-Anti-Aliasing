package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import net.minecraft.client.gui.components.AbstractWidget;
import java.util.function.Consumer;
public interface Layout extends LayoutElement {
    void arrangeElements();
    void visitChildren(Consumer<LayoutElement> consumer);
    default void visitWidgets(Consumer<AbstractWidget> consumer) { visitChildren(child -> child.visitWidgets(consumer)); }
}
