package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

import java.util.Locale;

/**
 * Implements sharpness slider widget behavior for Salt's Anti Aliasing. Client-side configuration
 * UI code that turns render settings into controls the player can change safely at runtime.
 */
public final class SharpnessSliderWidget extends AbstractSliderButton {
    private static final int VIDEO_ROW_WIDTH = 150;
    private static final int VIDEO_ROW_HEIGHT = 20;
    private static final String LABEL_KEY = "options.salts_anti_aliasing.sharpness";
    private static final String TOOLTIP_KEY = "options.salts_anti_aliasing.sharpness.tooltip";

    private final RenderRuntime runtime;

    /**
     * Creates a sharpness slider widget instance with the collaborators or initial state supplied
     * by the caller.
     * @param runtime runtime value supplied by the caller or Minecraft callback
     */
    public SharpnessSliderWidget(RenderRuntime runtime) {
        super(
                0,
                0,
                VIDEO_ROW_WIDTH,
                VIDEO_ROW_HEIGHT,
                Component.empty(),
                normalize(runtime.sharpenStrength())
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
        float sharpenStrength = runtime.sharpenStrength();
        String percentage = String.format(Locale.ROOT, "%.0f%%", sharpenStrength * 100.0f);
        this.setMessage(Component.translatable(LABEL_KEY, Component.literal(percentage)));
    }

    /**
     * Handles apply value as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     */
    @Override
    protected void applyValue() {
        float sharpenStrength = denormalize(this.value);
        this.value = normalize(runtime.setSharpenStrength(sharpenStrength));
        updateMessage();
    }

    /**
     * Handles normalize as part of the anti-aliasing render, configuration, or compatibility flow.
     * @param sharpenStrength normalized sharpening amount requested by the user interface
     * @return slider position normalized to Minecraft's 0..1 widget range
     */
    private static double normalize(float sharpenStrength) {
        float range = AntiAliasingConfig.MAX_SHARPEN_STRENGTH - AntiAliasingConfig.MIN_SHARPEN_STRENGTH;
        if (range <= 0.0f) {
            return 0.0d;
        }

        return (sharpenStrength - AntiAliasingConfig.MIN_SHARPEN_STRENGTH) / range;
    }

    /**
     * Handles denormalize as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param sliderValue slider value supplied by the caller or Minecraft callback
     * @return configured value reconstructed from the slider position
     */
    private static float denormalize(double sliderValue) {
        double clampedValue = Math.max(0.0d, Math.min(1.0d, sliderValue));
        float range = AntiAliasingConfig.MAX_SHARPEN_STRENGTH - AntiAliasingConfig.MIN_SHARPEN_STRENGTH;
        return (float) (AntiAliasingConfig.MIN_SHARPEN_STRENGTH + clampedValue * range);
    }
}
