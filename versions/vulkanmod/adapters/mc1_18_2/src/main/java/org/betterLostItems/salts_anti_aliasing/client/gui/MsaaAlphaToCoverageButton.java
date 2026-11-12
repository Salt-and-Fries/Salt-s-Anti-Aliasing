package org.betterLostItems.salts_anti_aliasing.client.gui;

import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.*;

import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyButton;
import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.Tooltip;
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

    public static LegacyButton create(RenderRuntime runtime) {
        return LegacyButton.builder(message(runtime.msaaAlphaToCoverage()), button -> {
                    boolean enabled = runtime.setMsaaAlphaToCoverage(!runtime.msaaAlphaToCoverage());
                    button.setMessage(message(enabled));
                })
                .size(VIDEO_ROW_WIDTH, VIDEO_ROW_HEIGHT)
                .tooltip(Tooltip.create(org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(TOOLTIP_KEY)))
                .build();
    }

    public static void refresh(LegacyButton button, RenderRuntime runtime) {
        button.setMessage(message(runtime.msaaAlphaToCoverage()));
    }

    private static Component message(boolean enabled) {
        return org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(
                LABEL_KEY,
                org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(enabled ? "options.on" : "options.off")
        );
    }
}
