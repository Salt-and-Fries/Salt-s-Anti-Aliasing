package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.Options;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Supplies the option-list behavior introduced in 1.21 while retaining 1.20's layout/events. */
abstract class LegacyOptionsScreen extends OptionsSubScreen {
    protected OptionsList list;
    LegacyOptionsScreen(Screen parent, Options options, Component title) { super(parent, options, title); }
    @Override protected void init() {
        addTitle();
        addFooter();
        list = addRenderableWidget(new OptionsList(minecraft, width, height, this));
        addOptions();
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }
    protected abstract void addOptions();
    @Override protected void repositionElements() {
        if (list != null) list.updateSize(width, layout);
        layout.arrangeElements();
    }
}
