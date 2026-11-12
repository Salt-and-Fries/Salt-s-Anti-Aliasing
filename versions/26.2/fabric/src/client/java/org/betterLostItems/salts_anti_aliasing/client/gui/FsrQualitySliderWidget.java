package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.config.FsrQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

/**
 * Client-side slider for AMD FSR temporal upscaling quality modes.
 */
public final class FsrQualitySliderWidget extends AbstractSliderButton {
    private static final int VIDEO_ROW_WIDTH = 150;
    private static final int VIDEO_ROW_HEIGHT = 20;
    private static final String LABEL_KEY = "options.salts_anti_aliasing.fsr_quality";
    private static final String TOOLTIP_KEY = "options.salts_anti_aliasing.fsr_quality.tooltip";

    private final RenderRuntime runtime;

    public FsrQualitySliderWidget(RenderRuntime runtime) {
        super(
                0,
                0,
                VIDEO_ROW_WIDTH,
                VIDEO_ROW_HEIGHT,
                Component.empty(),
                normalize(runtime.fsrQualityPreset())
        );
        this.runtime = runtime;
        this.setTooltip(Tooltip.create(Component.translatable(TOOLTIP_KEY)));
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(Component.translatable(LABEL_KEY, ClientText.label(runtime.fsrQualityPreset())));
    }

    @Override
    protected void applyValue() {
        FsrQualityPreset preset = selectedPreset(this.value);
        this.value = normalize(runtime.setFsrQualityPreset(preset));
        updateMessage();
    }

    private static double normalize(FsrQualityPreset preset) {
        FsrQualityPreset[] values = FsrQualityPreset.values();
        if (values.length <= 1) {
            return 0.0d;
        }

        return (double) preset.ordinal() / (values.length - 1);
    }

    private static FsrQualityPreset selectedPreset(double sliderValue) {
        FsrQualityPreset[] values = FsrQualityPreset.values();
        if (values.length == 0) {
            return FsrQualityPreset.defaultPreset();
        }

        int maxIndex = values.length - 1;
        int index = (int) Math.round(Math.max(0.0d, Math.min(1.0d, sliderValue)) * maxIndex);
        return values[Math.max(0, Math.min(maxIndex, index))];
    }
}
