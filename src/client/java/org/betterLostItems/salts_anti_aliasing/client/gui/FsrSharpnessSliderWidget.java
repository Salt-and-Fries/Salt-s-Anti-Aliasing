package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

import java.util.Locale;

/**
 * Client-side slider for AMD FSR RCAS sharpening.
 */
public final class FsrSharpnessSliderWidget extends AbstractSliderButton {
    private static final int VIDEO_ROW_WIDTH = 150;
    private static final int VIDEO_ROW_HEIGHT = 20;
    private static final String LABEL_KEY = "options.salts_anti_aliasing.fsr_sharpness";
    private static final String TOOLTIP_KEY = "options.salts_anti_aliasing.fsr_sharpness.tooltip";

    private final RenderRuntime runtime;

    public FsrSharpnessSliderWidget(RenderRuntime runtime) {
        super(
                0,
                0,
                VIDEO_ROW_WIDTH,
                VIDEO_ROW_HEIGHT,
                Component.empty(),
                normalize(runtime.fsrSharpness())
        );
        this.runtime = runtime;
        this.setTooltip(Tooltip.create(Component.translatable(TOOLTIP_KEY)));
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        String percentage = String.format(Locale.ROOT, "%.0f%%", runtime.fsrSharpness() * 100.0f);
        this.setMessage(Component.translatable(LABEL_KEY, Component.literal(percentage)));
    }

    @Override
    protected void applyValue() {
        float sharpness = denormalize(this.value);
        this.value = normalize(runtime.setFsrSharpness(sharpness));
        updateMessage();
    }

    private static double normalize(float sharpness) {
        float range = AntiAliasingConfig.MAX_FSR_SHARPEN_STRENGTH - AntiAliasingConfig.MIN_FSR_SHARPEN_STRENGTH;
        if (range <= 0.0f) {
            return 0.0d;
        }

        return (sharpness - AntiAliasingConfig.MIN_FSR_SHARPEN_STRENGTH) / range;
    }

    private static float denormalize(double sliderValue) {
        double clampedValue = Math.max(0.0d, Math.min(1.0d, sliderValue));
        float range = AntiAliasingConfig.MAX_FSR_SHARPEN_STRENGTH - AntiAliasingConfig.MIN_FSR_SHARPEN_STRENGTH;
        return (float) (AntiAliasingConfig.MIN_FSR_SHARPEN_STRENGTH + clampedValue * range);
    }
}
