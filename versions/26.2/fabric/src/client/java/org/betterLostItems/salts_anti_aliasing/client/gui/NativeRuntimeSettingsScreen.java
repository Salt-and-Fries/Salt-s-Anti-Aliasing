package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;

import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.*;

import java.util.function.*;

/** Optional runtime overrides are isolated from normal image-quality controls. */
final class NativeRuntimeSettingsScreen extends OptionsSubScreen {
    NativeRuntimeSettingsScreen(Screen parent) {
        super(
                parent,
                Minecraft.getInstance().options,
                Component.translatable("options.salts_anti_aliasing.native_runtime"));
    }

    @Override
    protected void addOptions() {
        list.addHeader(
                Component.translatable("options.salts_anti_aliasing.native_runtime.restart"));
        var config = SaltsAntiAliasingClient.runtime().configSnapshot();
        if (config.mode == AntiAliasingMode.DLSS_SUPER_RESOLUTION) {
            field("bridge_path", config.dlssBridgePath, (c, v) -> c.dlssBridgePath = v);
            field("plugin_path", config.dlssPluginPath, (c, v) -> c.dlssPluginPath = v);
            field("log_path", config.dlssLogPath, (c, v) -> c.dlssLogPath = v);
            field(
                    "application_id",
                    Integer.toString(config.dlssApplicationId),
                    (c, v) -> {
                        try {
                            c.dlssApplicationId = Math.max(0, Integer.parseInt(v));
                        } catch (NumberFormatException ignored) {
                        }
                    });
        } else {
            field("bridge_path", config.fsrBridgePath, (c, v) -> c.fsrBridgePath = v);
            field("runtime_path", config.fsrRuntimePath, (c, v) -> c.fsrRuntimePath = v);
            field("log_path", config.fsrLogPath, (c, v) -> c.fsrLogPath = v);
        }
    }

    private void field(String key, String initial, BiConsumer<AntiAliasingConfig, String> setter) {
        Component label =
                Component.translatable("options.salts_anti_aliasing.native_runtime." + key);
        list.addHeader(label);
        var edit = new EditBox(minecraft.font, 0, 0, list.getRowWidth(), 20, label);
        edit.setMaxLength(4096);
        edit.setValue(initial);
        edit.setHint(Component.translatable("options.salts_anti_aliasing.native_runtime.bundled"));
        edit.setTooltip(
                Tooltip.create(
                        label.copy()
                                .append(" - ")
                                .append(
                                        Component.translatable(
                                                "options.salts_anti_aliasing.native_runtime.restart"))));
        edit.setResponder(
                value ->
                        SaltsAntiAliasingClient.runtime()
                                .editSettingsLive(c -> setter.accept(c, value)));
        list.addBig(edit);
    }

    @Override
    public void removed() {
        SaltsAntiAliasingClient.runtime().saveSettings();
        super.removed();
    }
}
