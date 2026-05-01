package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.config.SsaaScaleLevel;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

/**
 * Implements ssaa scale slider widget behavior for Salt's Anti Aliasing. Client-side configuration
 * UI code that turns render settings into controls the player can change safely at runtime.
 */
public final class SsaaScaleSliderWidget extends AbstractSliderButton {
    private static final int VIDEO_ROW_WIDTH = 150;
    private static final int VIDEO_ROW_HEIGHT = 20;
    private static final String LABEL_KEY = "options.salts_anti_aliasing.ssaa_scale";
    private static final String TOOLTIP_KEY = "options.salts_anti_aliasing.ssaa_scale.tooltip";

    private final RenderRuntime runtime;

    /**
     * Creates a ssaa scale slider widget instance with the collaborators or initial state supplied
     * by the caller.
     * @param runtime runtime value supplied by the caller or Minecraft callback
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
     * Handles update message as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     */
    @Override
    protected void updateMessage() {
        SsaaScaleLevel level = runtime.ssaaScaleLevel();
        this.setMessage(Component.translatable(LABEL_KEY, Component.literal(level.label())));
    }

    /**
     * Handles apply value as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     */
    @Override
    protected void applyValue() {
        SsaaScaleLevel level = selectedLevel(this.value);
        this.value = normalize(runtime.setSsaaScaleLevel(level));
        updateMessage();
    }

    /**
     * Handles normalize as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param level level value supplied by the caller or Minecraft callback
     * @return slider position normalized to Minecraft's 0..1 widget range
     */
    private static double normalize(SsaaScaleLevel level) {
        SsaaScaleLevel[] values = SsaaScaleLevel.values();
        if (values.length <= 1) {
            return 0.0d;
        }

        return (double) level.ordinal() / (values.length - 1);
    }

    /**
     * Handles selected level as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param sliderValue slider value supplied by the caller or Minecraft callback
     * @return preset selected by the current slider position
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
