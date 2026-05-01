package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.config.SsaaScaleLevel;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

/**
 * Documents ssaa scale slider widget behavior for Salt's Anti Aliasing. Client UI code that turns
 * runtime configuration into player-facing controls.
 */
public final class SsaaScaleSliderWidget extends AbstractSliderButton {
    private static final int VIDEO_ROW_WIDTH = 150;
    private static final int VIDEO_ROW_HEIGHT = 20;
    private static final String LABEL_KEY = "options.salts_anti_aliasing.ssaa_scale";
    private static final String TOOLTIP_KEY = "options.salts_anti_aliasing.ssaa_scale.tooltip";

    private final RenderRuntime runtime;

    /**
     * Creates a ssaa scale slider widget with the collaborators or initial state supplied by the
     * caller.
     * @param runtime runtime supplied by Minecraft or the caller
     */
    public SsaaScaleSliderWidget(RenderRuntime runtime) {
        super(
                0,
                0,
                VIDEO_ROW_WIDTH,
                VIDEO_ROW_HEIGHT,
                Component.empty(),
                normalize(runtime.ssaaScaleLevel())
        );
        this.runtime = runtime;
        this.setTooltip(Tooltip.create(Component.translatable(TOOLTIP_KEY)));
        updateMessage();
    }

    /**
     * Coordinates update message within the anti-aliasing render, configuration, or compatibility
     * flow.
     */
    @Override
    protected void updateMessage() {
        SsaaScaleLevel level = runtime.ssaaScaleLevel();
        this.setMessage(Component.translatable(LABEL_KEY, Component.literal(level.label())));
    }

    /**
     * Applies value at the renderer phase where it can affect the scene without touching HUD layers.
     */
    @Override
    protected void applyValue() {
        SsaaScaleLevel level = selectedLevel(this.value);
        this.value = normalize(runtime.setSsaaScaleLevel(level));
        updateMessage();
    }

    /**
     * Coordinates normalize within the anti-aliasing render, configuration, or compatibility flow.
     * @param level level supplied by Minecraft or the caller
     * @return normalize value produced or selected by this code path
     */
    private static double normalize(SsaaScaleLevel level) {
        SsaaScaleLevel[] values = SsaaScaleLevel.values();
        if (values.length <= 1) {
            return 0.0d;
        }

        return (double) level.ordinal() / (values.length - 1);
    }

    /**
     * Coordinates selected level within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param sliderValue slider value supplied by Minecraft or the caller
     * @return selected level value produced or selected by this code path
     */
    private static SsaaScaleLevel selectedLevel(double sliderValue) {
        SsaaScaleLevel[] values = SsaaScaleLevel.values();
        if (values.length == 0) {
            return SsaaScaleLevel.defaultLevel();
        }

        int maxIndex = values.length - 1;
        int index = (int) Math.round(Math.max(0.0d, Math.min(1.0d, sliderValue)) * maxIndex);
        return values[Math.max(0, Math.min(maxIndex, index))];
    }
}
