package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;

import org.betterLostItems.salts_anti_aliasing.client.config.NumericTuningSetting;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

import java.util.Locale;

final class TuningSliderWidget extends AbstractSliderButton {
    private final RenderRuntime runtime;
    private final NumericTuningSetting setting;

    TuningSliderWidget(RenderRuntime runtime, NumericTuningSetting setting) {
        super(
                0,
                0,
                150,
                20,
                Component.empty(),
                (setting.get(runtime.configSnapshot().tuning) - setting.min)
                        / (setting.max - setting.min));
        this.runtime = runtime;
        this.setting = setting;
        setTooltip(Tooltip.create(Component.translatable(setting.key() + ".tooltip")));
        updateMessage();
    }

    @Override
    protected void updateMessage() {
        setMessage(
                Component.translatable(setting.key())
                        .append(": ")
                        .append(
                                String.format(
                                        Locale.ROOT,
                                        "%.3f",
                                        setting.get(runtime.configSnapshot().tuning))));
    }

    @Override
    protected void applyValue() {
        runtime.editTuning(t -> setting.set(t, setting.min + value * (setting.max - setting.min)));
        updateMessage();
    }
}
