package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.OptionsSubScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;

/** Rebuilds the complete options layout on releases before vanilla supplied it. */
abstract class LegacyOptionsScreen extends OptionsSubScreen {
    protected WidgetOptionsList list;
    protected HeaderAndFooterLayout layout;
    LegacyOptionsScreen(Screen parent, Options options, Component title) { super(parent, options, title); }
    @Override protected void init() {
        layout = new HeaderAndFooterLayout(this);
        layout.addToHeader(new net.minecraft.client.gui.components.StringWidget(title, font));
        layout.addToFooter(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build());
        list = addRenderableWidget(new WidgetOptionsList(minecraft, width, Math.max(1, height - 64), 32));
        addOptions();
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }
    protected abstract void addOptions();
    @Override protected void repositionElements() {
        if (list != null) { list.setSize(width, Math.max(1, height - 64)); list.setY(32); }
        if (layout != null) layout.arrangeElements();
    }
}
