package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

/**
 * Creates the optional MSAA alpha-to-coverage toggle used by both settings screens.
 */
public final class MsaaAlphaToCoverageButton {
    private static final int VIDEO_ROW_WIDTH = 150;
    private static final int VIDEO_ROW_HEIGHT = 20;
    private static final String LABEL_KEY = "options.salts_anti_aliasing.msaa_alpha_to_coverage";
    private static final String TOOLTIP_KEY = "options.salts_anti_aliasing.msaa_alpha_to_coverage.tooltip";

    private MsaaAlphaToCoverageButton() {
    }

    public static Button create(RenderRuntime runtime) {
        return Button.builder(message(runtime.msaaAlphaToCoverage()), button -> {
                    boolean enabled = runtime.setMsaaAlphaToCoverage(!runtime.msaaAlphaToCoverage());
                    button.setMessage(message(enabled));
                })
                .size(VIDEO_ROW_WIDTH, VIDEO_ROW_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(TOOLTIP_KEY)))
                .build();
    }

    public static void refresh(Button button, RenderRuntime runtime) {
        button.setMessage(message(runtime.msaaAlphaToCoverage()));
    }

    private static Component message(boolean enabled) {
        return Component.translatable(
                LABEL_KEY,
                Component.translatable(enabled ? "options.on" : "options.off")
        );
    }
}
