package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import org.betterLostItems.salts_anti_aliasing.client.gui.WidgetOptionsList;

final class OptionRows {
    private OptionRows() {}
    static void add(WidgetOptionsList list, AbstractWidget widget) {
        widget.setWidth(list.getRowWidth());
        list.addSmall(widget, null);
    }
    static void header(WidgetOptionsList list, net.minecraft.network.chat.Component text) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        add(list, new org.betterLostItems.salts_anti_aliasing.client.gui.legacy.StringWidget(list.getRowWidth(), font.lineHeight + 8, text, font));
    }
}
