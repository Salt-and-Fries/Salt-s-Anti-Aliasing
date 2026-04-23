package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.config.MsaaSampleLevel;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

public final class MsaaSampleSliderWidget extends AbstractSliderButton {
    private static final int VIDEO_ROW_WIDTH = 150;
    private static final int VIDEO_ROW_HEIGHT = 20;
    private static final String LABEL_KEY = "options.salts_anti_aliasing.msaa_samples";
    private static final String TOOLTIP_KEY = "options.salts_anti_aliasing.msaa_samples.tooltip";

    private final RenderRuntime runtime;

    public MsaaSampleSliderWidget(RenderRuntime runtime) {
        super(
                0,
                0,
                VIDEO_ROW_WIDTH,
                VIDEO_ROW_HEIGHT,
                Component.empty(),
                normalize(runtime.msaaSampleLevel())
        );
        this.runtime = runtime;
        this.setTooltip(Tooltip.create(Component.translatable(TOOLTIP_KEY)));
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        MsaaSampleLevel level = runtime.msaaSampleLevel();
        this.setMessage(Component.translatable(LABEL_KEY, Component.literal(level.label())));
    }

    @Override
    protected void applyValue() {
        MsaaSampleLevel selectedLevel = selectedLevel(this.value);
        this.value = normalize(runtime.setMsaaSampleLevel(selectedLevel));
        updateMessage();
    }

    private static double normalize(MsaaSampleLevel level) {
        MsaaSampleLevel[] values = MsaaSampleLevel.values();
        if (values.length <= 1) {
            return 0.0d;
        }

        return (double) level.ordinal() / (values.length - 1);
    }

    private static MsaaSampleLevel selectedLevel(double sliderValue) {
        MsaaSampleLevel[] values = MsaaSampleLevel.values();
        if (values.length == 0) {
            return MsaaSampleLevel.defaultLevel();
        }

        int maxIndex = values.length - 1;
        int index = (int) Math.round(Math.max(0.0d, Math.min(1.0d, sliderValue)) * maxIndex);
        return values[Math.max(0, Math.min(maxIndex, index))];
    }
}
