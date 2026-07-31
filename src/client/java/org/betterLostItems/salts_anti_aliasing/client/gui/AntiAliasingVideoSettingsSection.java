package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

import java.util.ArrayList;
import java.util.List;

/**
 * Appends Salt's Anti Aliasing controls as a dedicated section in Minecraft's video settings list.
 */
public final class AntiAliasingVideoSettingsSection {
    private static final int MIN_WIDE_ROW_WIDTH = 150;
    private static final int ROW_HEIGHT = 20;
    private static final String SECTION_HEADER_KEY = "options.salts_anti_aliasing.section";
    private static final String DROPDOWN_LABEL_KEY = "options.salts_anti_aliasing.dropdown";
    private static final String DROPDOWN_TOOLTIP_KEY = "options.salts_anti_aliasing.dropdown.tooltip";

    private AntiAliasingVideoSettingsSection() {
    }

    public static Controls addTo(
            OptionsList list,
            RenderRuntime runtime,
            boolean dropdownExpanded,
            boolean improvedTransparencyEnabled,
            Runnable onDropdownToggled
    ) {
        list.addHeader(Component.translatable(SECTION_HEADER_KEY));

        int wideRowWidth = Math.max(MIN_WIDE_ROW_WIDTH, list.getRowWidth());
        Button dropdownButton = Button.builder(dropdownLabel(runtime.activeMode()), button -> onDropdownToggled.run())
                .size(wideRowWidth, ROW_HEIGHT)
                .tooltip(dropdownTooltip(runtime.activeMode(), dropdownExpanded))
                .build();
        list.addBig(dropdownButton);

        List<AbstractWidget> secondaryControls = secondaryControls(runtime, runtime.activeMode());
        for (AbstractWidget secondaryControl : secondaryControls) {
            secondaryControl.setWidth(wideRowWidth);
            list.addBig(secondaryControl);
        }

        Controls controls = new Controls(dropdownButton, List.copyOf(secondaryControls));
        refresh(controls, runtime, improvedTransparencyEnabled, dropdownExpanded);
        return controls;
    }

    public static void refresh(
            Controls controls,
            RenderRuntime runtime,
            boolean improvedTransparencyEnabled,
            boolean dropdownExpanded
    ) {
        if (controls == null) {
            return;
        }

        AntiAliasingMode activeMode = runtime.activeMode();
        controls.dropdownButton().setMessage(dropdownLabel(activeMode));
        controls.dropdownButton().setTooltip(dropdownTooltip(activeMode, dropdownExpanded));
        controls.dropdownButton().active = true;

        boolean controlsActive = !improvedTransparencyEnabled
                && runtime.canUseAntiAliasing()
                && runtime.canSelectMode(activeMode);
        for (AbstractWidget secondaryControl : controls.secondaryControls()) {
            secondaryControl.active = controlsActive;
        }
    }

    private static List<AbstractWidget> secondaryControls(RenderRuntime runtime, AntiAliasingMode activeMode) {
        List<AbstractWidget> controls = new ArrayList<>();
        controls.add(new SharpnessSliderWidget(runtime));
        switch (activeMode) {
            case MSAA -> controls.add(new MsaaSampleSliderWidget(runtime));
            case SSAA -> controls.add(new SsaaScaleSliderWidget(runtime));
            case NIS_UPSCALE, FSR1_UPSCALE -> controls.add(new SpatialUpscaleQualitySliderWidget(runtime));
            case DLSS_SUPER_RESOLUTION -> controls.add(new DlssQualitySliderWidget(runtime));
            case FSR2_SUPER_RESOLUTION, FSR3_SUPER_RESOLUTION, FSR3_SUPER_RESOLUTION_FRAME_GENERATION ->
                    controls.add(new FsrQualitySliderWidget(runtime));
            default -> {
            }
        }
        return controls;
    }

    private static Component dropdownLabel(AntiAliasingMode activeMode) {
        return Component.translatable(DROPDOWN_LABEL_KEY, ClientText.label(activeMode));
    }

    private static Tooltip dropdownTooltip(AntiAliasingMode activeMode, boolean dropdownExpanded) {
        return Tooltip.create(Component.translatable(
                DROPDOWN_TOOLTIP_KEY,
                ClientText.label(activeMode),
                ClientText.summary(activeMode)
        ));
    }

    public record Controls(
            Button dropdownButton,
            List<AbstractWidget> secondaryControls
    ) {
    }
}
