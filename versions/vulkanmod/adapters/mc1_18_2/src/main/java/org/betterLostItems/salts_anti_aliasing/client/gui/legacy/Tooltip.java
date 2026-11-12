package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import net.minecraft.network.chat.Component;
public record Tooltip(Component text) { public static Tooltip create(Component text) { return new Tooltip(text); } }
