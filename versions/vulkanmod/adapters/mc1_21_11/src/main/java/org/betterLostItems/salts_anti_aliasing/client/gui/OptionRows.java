package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;

final class OptionRows {
    private OptionRows() {}
    static void add(OptionsList list, AbstractWidget widget) {
        widget.setWidth(list.getRowWidth());
        list.addSmall(widget, null);
    }
}
