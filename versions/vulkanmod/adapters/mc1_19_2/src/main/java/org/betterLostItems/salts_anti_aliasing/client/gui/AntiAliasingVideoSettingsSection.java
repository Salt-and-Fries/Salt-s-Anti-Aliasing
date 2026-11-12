package org.betterLostItems.salts_anti_aliasing.client.gui;

import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.*;

import net.minecraft.client.gui.components.AbstractWidget;
import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyButton;
import org.betterLostItems.salts_anti_aliasing.client.gui.WidgetOptionsList;
import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

import java.util.ArrayList;
import java.util.List;

/** Builds the common mode picker and basic controls inside the dedicated settings screen. */
public final class AntiAliasingVideoSettingsSection {
    private static final int MIN_WIDE_ROW_WIDTH = 150;
    private static final int ROW_HEIGHT = 20;
    private static final String SECTION_HEADER_KEY = "options.salts_anti_aliasing.section";
    private static final String DROPDOWN_LABEL_KEY = "options.salts_anti_aliasing.dropdown";
    private static final String DROPDOWN_TOOLTIP_KEY =
            "options.salts_anti_aliasing.dropdown.tooltip";

    private AntiAliasingVideoSettingsSection() {}

    public static Controls addTo(
            WidgetOptionsList list,
            RenderRuntime runtime,
            boolean dropdownExpanded,
            boolean improvedTransparencyEnabled,
            Runnable onDropdownToggled,
            Screen parent) {
        OptionRows.header(list, Component.translatable(SECTION_HEADER_KEY));

        return addControlsTo(
                list,
                runtime,
                dropdownExpanded,
                improvedTransparencyEnabled,
                onDropdownToggled,
                parent);
    }

    /**
     * Adds the shared mode picker and its current mode-specific controls without a section header.
     * Mod Menu uses this form because the containing screen already carries the mod title.
     */
    public static Controls addControlsTo(
            WidgetOptionsList list,
            RenderRuntime runtime,
            boolean dropdownExpanded,
            boolean improvedTransparencyEnabled,
            Runnable onDropdownToggled,
            Screen parent) {
        int wideRowWidth = Math.max(MIN_WIDE_ROW_WIDTH, list.getRowWidth());
        LegacyButton dropdownButton =
                LegacyButton.builder(
                                dropdownLabel(runtime.activeMode()),
                                button -> onDropdownToggled.run())
                        .size(wideRowWidth, ROW_HEIGHT)
                        .tooltip(dropdownTooltip(runtime.activeMode(), dropdownExpanded))
                        .build();
        OptionRows.add(list, dropdownButton);

        List<AbstractWidget> secondaryControls = secondaryControls(runtime, runtime.activeMode());
        for (AbstractWidget secondaryControl : secondaryControls) {
            secondaryControl.setWidth(wideRowWidth);
            OptionRows.add(list, secondaryControl);
        }

        Controls controls = new Controls(dropdownButton, List.copyOf(secondaryControls));
        refresh(controls, runtime, improvedTransparencyEnabled, dropdownExpanded);
        return controls;
    }

    public static void refresh(
            Controls controls,
            RenderRuntime runtime,
            boolean improvedTransparencyEnabled,
            boolean dropdownExpanded) {
        if (controls == null) {
            return;
        }

        AntiAliasingMode activeMode = runtime.activeMode();
        controls.dropdownButton().setMessage(dropdownLabel(activeMode));
        controls.dropdownButton().setTooltip(dropdownTooltip(activeMode, dropdownExpanded));
        controls.dropdownButton().active = true;

        boolean controlsActive = runtime.canConfigureMode(activeMode);
        for (AbstractWidget secondaryControl : controls.secondaryControls()) {
            secondaryControl.active = controlsActive;
        }
    }

    private static List<AbstractWidget> secondaryControls(
            RenderRuntime runtime, AntiAliasingMode activeMode) {
        List<AbstractWidget> controls = new ArrayList<>();
        for (ControlKind controlKind : controlLayout(activeMode)) {
            controls.add(createControl(runtime, controlKind));
        }
        return controls;
    }

    static List<ControlKind> controlLayout(AntiAliasingMode activeMode) {
        return switch (activeMode) {
            case MSAA ->
                    List.of(
                            ControlKind.SHARPNESS,
                            ControlKind.MSAA_SAMPLES,
                            ControlKind.MSAA_ALPHA_TO_COVERAGE);
            case SSAA -> List.of(ControlKind.SHARPNESS, ControlKind.SSAA_SCALE);
            case NIS_UPSCALE, FSR1_UPSCALE ->
                    List.of(ControlKind.SHARPNESS, ControlKind.SPATIAL_UPSCALE_QUALITY);
            case SGSR1_UPSCALE, SGSR2_SUPER_RESOLUTION -> List.of(ControlKind.SHARPNESS,ControlKind.SGSR_QUALITY);
            case XESS_SUPER_RESOLUTION -> List.of(ControlKind.SHARPNESS, ControlKind.XESS_QUALITY);
            case DLSS_SUPER_RESOLUTION -> List.of(ControlKind.SHARPNESS, ControlKind.DLSS_QUALITY);
            case FSR2_SUPER_RESOLUTION,
                            FSR3_SUPER_RESOLUTION,
                            FSR3_SUPER_RESOLUTION_FRAME_GENERATION ->
                    List.of(ControlKind.SHARPNESS, ControlKind.FSR_QUALITY);
            default -> List.of(ControlKind.SHARPNESS);
        };
    }

    private static AbstractWidget createControl(RenderRuntime runtime, ControlKind controlKind) {
        return switch (controlKind) {
            case SHARPNESS -> new SharpnessSliderWidget(runtime);
            case MSAA_SAMPLES -> new MsaaSampleSliderWidget(runtime);
            case MSAA_ALPHA_TO_COVERAGE -> MsaaAlphaToCoverageButton.create(runtime);
            case SSAA_SCALE -> new SsaaScaleSliderWidget(runtime);
            case SPATIAL_UPSCALE_QUALITY -> new SpatialUpscaleQualitySliderWidget(runtime);
            case SGSR_QUALITY -> new SgsrQualitySliderWidget(runtime);
            case XESS_QUALITY -> new XessQualitySliderWidget(runtime);
            case DLSS_QUALITY -> new DlssQualitySliderWidget(runtime);
            case FSR_QUALITY -> new FsrQualitySliderWidget(runtime);
        };
    }

    private static Component dropdownLabel(AntiAliasingMode activeMode) {
        return Component.translatable(DROPDOWN_LABEL_KEY, ClientText.label(activeMode));
    }

    private static Tooltip dropdownTooltip(AntiAliasingMode activeMode, boolean dropdownExpanded) {
        return Tooltip.create(
                Component.translatable(
                        DROPDOWN_TOOLTIP_KEY,
                        ClientText.label(activeMode),
                        ClientText.summary(activeMode)));
    }

    public record Controls(LegacyButton dropdownButton, List<AbstractWidget> secondaryControls) {}

    enum ControlKind {
        SHARPNESS,
        MSAA_SAMPLES,
        MSAA_ALPHA_TO_COVERAGE,
        SSAA_SCALE,
        SPATIAL_UPSCALE_QUALITY,
        DLSS_QUALITY,
        XESS_QUALITY,
        SGSR_QUALITY,
        FSR_QUALITY
    }
}
