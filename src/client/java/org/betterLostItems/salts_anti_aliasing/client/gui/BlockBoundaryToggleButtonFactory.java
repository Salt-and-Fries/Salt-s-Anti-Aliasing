package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

public final class BlockBoundaryToggleButtonFactory {
    private static final int VIDEO_ROW_WIDTH = 150;
    private static final int VIDEO_ROW_HEIGHT = 20;
    private static final String LABEL_KEY = "options.salts_anti_aliasing.block_boundaries";
    private static final String TOOLTIP_KEY = "options.salts_anti_aliasing.block_boundaries.tooltip";

    private BlockBoundaryToggleButtonFactory() {
    }

    public static Button create(RenderRuntime runtime) {
        return Button.builder(label(runtime), button -> {
                    runtime.toggleAccountForBlockBoundaries();
                    refresh(button, runtime);
                })
                .size(VIDEO_ROW_WIDTH, VIDEO_ROW_HEIGHT)
                .tooltip(Tooltip.create(Component.translatable(TOOLTIP_KEY)))
                .build();
    }

    public static void refresh(Button button, RenderRuntime runtime) {
        button.setMessage(label(runtime));
        button.setTooltip(Tooltip.create(Component.translatable(TOOLTIP_KEY)));
    }

    private static Component label(RenderRuntime runtime) {
        return Component.translatable(
                LABEL_KEY,
                Component.translatable(runtime.accountForBlockBoundaries() ? "options.on" : "options.off")
        );
    }
}
