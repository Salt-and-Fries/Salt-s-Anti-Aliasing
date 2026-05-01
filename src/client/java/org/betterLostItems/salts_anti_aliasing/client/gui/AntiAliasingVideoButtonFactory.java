package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

/**
 * Documents anti aliasing video button factory behavior for Salt's Anti Aliasing. Client UI code that
 * turns runtime configuration into player-facing controls.
 */
public final class AntiAliasingVideoButtonFactory {
    private static final int VIDEO_ROW_WIDTH = 150;
    private static final int VIDEO_ROW_HEIGHT = 20;
    private static final String MODE_LABEL_KEY = "options.salts_anti_aliasing.mode";
    private static final String MODE_TOOLTIP_KEY = "options.salts_anti_aliasing.mode.tooltip";
    private static final String MODE_DISABLED_TOOLTIP_KEY = "options.salts_anti_aliasing.mode.tooltip.disabled";

    /**
     * Creates a anti aliasing video button factory with the collaborators or initial state supplied by
     * the caller.
     */
    private AntiAliasingVideoButtonFactory() {
    }

    /**
     * Coordinates create within the anti-aliasing render, configuration, or compatibility flow.
     * @param onModeChanged on mode changed supplied by Minecraft or the caller
     * @return create value produced or selected by this code path
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
     * Coordinates refresh within the anti-aliasing render, configuration, or compatibility flow.
     * @param button button supplied by Minecraft or the caller
     * @param mode requested anti-aliasing mode
     * @param available available supplied by Minecraft or the caller
     */
    public static void refresh(Button button, AntiAliasingMode mode, boolean available) {
        button.setMessage(labelFor(mode));
        button.setTooltip(Tooltip.create(tooltipFor(mode, available)));
    }

    /**
     * Coordinates current label within the anti-aliasing render, configuration, or compatibility flow.
     * @return current label value produced or selected by this code path
     */
    private static Component currentLabel() {
        return labelFor(currentMode());
    }

    /**
     * Coordinates current mode within the anti-aliasing render, configuration, or compatibility flow.
     * @return current mode value produced or selected by this code path
     */
    private static AntiAliasingMode currentMode() {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        return runtime == null ? AntiAliasingMode.OFF : runtime.activeMode();
    }

    /**
     * Coordinates label for within the anti-aliasing render, configuration, or compatibility flow.
     * @param mode requested anti-aliasing mode
     * @return label for value produced or selected by this code path
     */
    private static Component labelFor(AntiAliasingMode mode) {
        return Component.translatable(MODE_LABEL_KEY, ClientText.label(mode));
    }

    /**
     * Coordinates tooltip for within the anti-aliasing render, configuration, or compatibility flow.
     * @param mode requested anti-aliasing mode
     * @param available available supplied by Minecraft or the caller
     * @return tooltip for value produced or selected by this code path
     */
    private static Component tooltipFor(AntiAliasingMode mode, boolean available) {
        return Component.translatable(
                available ? MODE_TOOLTIP_KEY : MODE_DISABLED_TOOLTIP_KEY,
                ClientText.label(mode),
                ClientText.summary(mode)
        );
    }
}
