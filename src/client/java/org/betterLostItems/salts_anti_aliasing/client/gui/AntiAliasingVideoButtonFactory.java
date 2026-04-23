package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

public final class AntiAliasingVideoButtonFactory {
    private static final int VIDEO_ROW_WIDTH = 150;
    private static final int VIDEO_ROW_HEIGHT = 20;
    private static final String MODE_LABEL_KEY = "options.salts_anti_aliasing.mode";
    private static final String MODE_TOOLTIP_KEY = "options.salts_anti_aliasing.mode.tooltip";
    private static final String MODE_DISABLED_TOOLTIP_KEY = "options.salts_anti_aliasing.mode.tooltip.disabled";

    private AntiAliasingVideoButtonFactory() {
    }

    public static Button create(Runnable onModeChanged) {
        return Button.builder(currentLabel(), button -> {
                    RenderRuntime runtime = SaltsAntiAliasingClient.runtime();
                    runtime.cycleMode();
                    onModeChanged.run();
                })
                .size(VIDEO_ROW_WIDTH, VIDEO_ROW_HEIGHT)
                .tooltip(Tooltip.create(tooltipFor(currentMode(), true)))
                .build();
    }

    public static void refresh(Button button, AntiAliasingMode mode, boolean available) {
        button.setMessage(labelFor(mode));
        button.setTooltip(Tooltip.create(tooltipFor(mode, available)));
    }

    private static Component currentLabel() {
        return labelFor(currentMode());
    }

    private static AntiAliasingMode currentMode() {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        return runtime == null ? AntiAliasingMode.OFF : runtime.activeMode();
    }

    private static Component labelFor(AntiAliasingMode mode) {
        return Component.translatable(MODE_LABEL_KEY, mode.label());
    }

    private static Component tooltipFor(AntiAliasingMode mode, boolean available) {
        return Component.translatable(
                available ? MODE_TOOLTIP_KEY : MODE_DISABLED_TOOLTIP_KEY,
                mode.label(),
                mode.tooltipSummary()
        );
    }
}
