package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

/**
 * Implements anti aliasing video button factory behavior for Salt's Anti Aliasing. Client-side
 * configuration UI code that turns render settings into controls the player can change safely at
 * runtime.
 */
public final class AntiAliasingVideoButtonFactory {
    private static final int VIDEO_ROW_WIDTH = 150;
    private static final int VIDEO_ROW_HEIGHT = 20;
    private static final String MODE_LABEL_KEY = "options.salts_anti_aliasing.mode";
    private static final String MODE_TOOLTIP_KEY = "options.salts_anti_aliasing.mode.tooltip";
    private static final String MODE_DISABLED_TOOLTIP_KEY = "options.salts_anti_aliasing.mode.tooltip.disabled";

    /**
     * Creates a anti aliasing video button factory instance with the collaborators or initial state
     * supplied by the caller.
     */
    private AntiAliasingVideoButtonFactory() {
    }

    /**
     * Handles create as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param onModeChanged on mode changed value supplied by the caller or Minecraft callback
     * @return a newly created instance configured for the current mod/runtime context
     */
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

    /**
     * Handles refresh as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param button button value supplied by the caller or Minecraft callback
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @param available available value supplied by the caller or Minecraft callback
     */
    public static void refresh(Button button, AntiAliasingMode mode, boolean available) {
        button.setMessage(labelFor(mode));
        button.setTooltip(Tooltip.create(tooltipFor(mode, available)));
    }

    /**
     * Handles current label as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return current label produced by this helper
     */
    private static Component currentLabel() {
        return labelFor(currentMode());
    }

    /**
     * Handles current mode as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @return current mode produced by this helper
     */
    private static AntiAliasingMode currentMode() {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        return runtime == null ? AntiAliasingMode.OFF : runtime.activeMode();
    }

    /**
     * Handles label for as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @return label for produced by this helper
     */
    private static Component labelFor(AntiAliasingMode mode) {
        return Component.translatable(MODE_LABEL_KEY, ClientText.label(mode));
    }

    /**
     * Handles tooltip for as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param mode anti-aliasing mode requested by UI, hotkey, or loaded config
     * @param available available value supplied by the caller or Minecraft callback
     * @return tooltip for produced by this helper
     */
    private static Component tooltipFor(AntiAliasingMode mode, boolean available) {
        return Component.translatable(
                available ? MODE_TOOLTIP_KEY : MODE_DISABLED_TOOLTIP_KEY,
                ClientText.label(mode),
                ClientText.summary(mode)
        );
    }
}
