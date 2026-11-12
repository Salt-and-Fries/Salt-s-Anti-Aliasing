package org.betterLostItems.salts_anti_aliasing.client.gui;

import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.*;

import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacySlider;
import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.Tooltip;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.config.XessQualityPreset;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

/**
 * Client-side slider for Intel XeSS temporal upscaling quality modes.
 */
public final class XessQualitySliderWidget extends LegacySlider {
    private static final int VIDEO_ROW_WIDTH = 150;
    private static final int VIDEO_ROW_HEIGHT = 20;
    private static final String LABEL_KEY = "options.salts_anti_aliasing.xess_quality";
    private static final String TOOLTIP_KEY = "options.salts_anti_aliasing.xess_quality.tooltip";

    private final RenderRuntime runtime;

    public XessQualitySliderWidget(RenderRuntime runtime) {
        super(
                0,
                0,
                VIDEO_ROW_WIDTH,
                VIDEO_ROW_HEIGHT,
                org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.empty(),
                normalize(runtime.xessQualityPreset())
        );
        this.runtime = runtime;
        this.setTooltip(Tooltip.create(org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(TOOLTIP_KEY)));
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        this.setMessage(org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyComponents.translatable(LABEL_KEY, ClientText.label(runtime.xessQualityPreset())));
    }

    @Override
    protected void applyValue() {
        XessQualityPreset preset = selectedPreset(this.value);
        this.value = normalize(runtime.setXessQualityPreset(preset));
        updateMessage();
    }

    private static double normalize(XessQualityPreset preset) {
        XessQualityPreset[] values = XessQualityPreset.values();
        if (values.length <= 1) {
            return 0.0d;
        }

        return (double) preset.ordinal() / (values.length - 1);
    }

    private static XessQualityPreset selectedPreset(double sliderValue) {
        XessQualityPreset[] values = XessQualityPreset.values();
        if (values.length == 0) {
            return XessQualityPreset.defaultPreset();
        }

        int maxIndex = values.length - 1;
        int index = (int) Math.round(Math.max(0.0d, Math.min(1.0d, sliderValue)) * maxIndex);
        return values[Math.max(0, Math.min(maxIndex, index))];
    }
}
