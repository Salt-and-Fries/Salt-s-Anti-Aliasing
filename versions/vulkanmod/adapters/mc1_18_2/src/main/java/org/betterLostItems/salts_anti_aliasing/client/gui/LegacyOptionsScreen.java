package org.betterLostItems.salts_anti_aliasing.client.gui;

import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.*;

import net.minecraft.client.Options;
import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyButton;
import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.HeaderAndFooterLayout;
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
        layout.addToHeader(new org.betterLostItems.salts_anti_aliasing.client.gui.legacy.StringWidget(title, font));
        layout.addToFooter(LegacyButton.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build());
        list = addRenderableWidget(new WidgetOptionsList(minecraft, width, Math.max(1, height - 64), 32));
        addOptions();
        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }
    protected abstract void addOptions();
    protected void repositionElements() {
        if (list != null) { list.updateSize(width, height, 32, height - 32); }
        if (layout != null) layout.arrangeElements();
    }
}
