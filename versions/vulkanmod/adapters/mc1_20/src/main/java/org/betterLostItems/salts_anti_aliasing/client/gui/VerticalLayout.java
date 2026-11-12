package org.betterLostItems.salts_anti_aliasing.client.gui;
import net.minecraft.client.gui.layouts.*;
final class VerticalLayout extends GridLayout {
    private int row;
    VerticalLayout(int gap) { rowSpacing(gap); }
    <T extends LayoutElement> T addChild(T child) { return super.addChild(child, row++, 0); }
}
