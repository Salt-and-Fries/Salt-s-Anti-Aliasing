package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.OptionsList;

final class OptionRows {
    private OptionRows() {}
    static void add(OptionsList list, AbstractWidget widget) {
        widget.setWidth(list.getRowWidth());
        list.addSmall(widget, null);
    }
    static void header(OptionsList list, net.minecraft.network.chat.Component text) {
        var font = net.minecraft.client.Minecraft.getInstance().font;
        add(list, new net.minecraft.client.gui.components.StringWidget(list.getRowWidth(), font.lineHeight + 8, text, font));
    }
}
