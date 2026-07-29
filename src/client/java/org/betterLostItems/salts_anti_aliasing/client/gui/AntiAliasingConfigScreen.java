package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

import java.util.List;

/**
 * Implements anti aliasing config screen behavior for Salt's Anti Aliasing. Client-side
 * configuration UI code that turns render settings into controls the player can change safely at
 * runtime.
 */
public final class AntiAliasingConfigScreen extends OptionsSubScreen {
    private static final String TITLE_KEY = "screen.salts_anti_aliasing.config";

    private Button modeButton;
    private SharpnessSliderWidget sharpnessSlider;
    private MsaaSampleSliderWidget msaaSlider;
    private SsaaScaleSliderWidget ssaaSlider;
    private SpatialUpscaleQualitySliderWidget upscaleSlider;
    private DlssQualitySliderWidget dlssQualitySlider;
    private FsrQualitySliderWidget fsrQualitySlider;
    private FsrSharpnessSliderWidget fsrSharpnessSlider;

    /**
     * Creates a anti aliasing config screen instance with the collaborators or initial state
     * supplied by the caller.
     * @param lastScreen last screen value supplied by the caller or Minecraft callback
     */
    public AntiAliasingConfigScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, Component.translatable(TITLE_KEY));
    }

    /**
     * Handles add options as part of the anti-aliasing render, configuration, or compatibility
     * flow.
     */
    @Override
    protected void addOptions() {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        modeButton = AntiAliasingVideoButtonFactory.create(() -> refreshControlAvailability(runtime.activeMode()));
        sharpnessSlider = new SharpnessSliderWidget(runtime);
        msaaSlider = new MsaaSampleSliderWidget(runtime);
        ssaaSlider = new SsaaScaleSliderWidget(runtime);
        upscaleSlider = new SpatialUpscaleQualitySliderWidget(runtime);
        dlssQualitySlider = new DlssQualitySliderWidget(runtime);
        fsrQualitySlider = new FsrQualitySliderWidget(runtime);
        fsrSharpnessSlider = new FsrSharpnessSliderWidget(runtime);

        list.addSmall(List.of(
                modeButton,
                sharpnessSlider,
                msaaSlider,
                ssaaSlider,
                upscaleSlider,
                dlssQualitySlider,
                fsrQualitySlider,
                fsrSharpnessSlider
        ));
        refreshControlAvailability(runtime.activeMode());
    }

    /**
     * Handles tick as part of the anti-aliasing render, configuration, or compatibility flow.
     */
    @Override
    public void tick() {
        super.tick();

        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null) {
            refreshControlAvailability(runtime.activeMode());
        }
    }

    /**
     * Coordinates refresh control availability within the anti-aliasing render, configuration, or compatibility flow.
     * @param activeMode active mode value supplied by the caller or Minecraft callback
     */
    private void refreshControlAvailability(AntiAliasingMode activeMode) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        boolean antiAliasingAvailable = runtime != null
                && runtime.canUseAntiAliasing()
                && minecraft != null
                && !(Boolean) minecraft.options.improvedTransparency().get();

        if (modeButton != null) {
            modeButton.active = antiAliasingAvailable;
            AntiAliasingVideoButtonFactory.refresh(modeButton, activeMode, antiAliasingAvailable);
        }

        if (sharpnessSlider != null) {
            sharpnessSlider.active = antiAliasingAvailable && activeMode.usesSharpenControl();
        }

        if (msaaSlider != null) {
            msaaSlider.active = antiAliasingAvailable && activeMode.usesMsaaSampleControl();
        }

        if (ssaaSlider != null) {
            ssaaSlider.active = antiAliasingAvailable && activeMode.usesSsaaScaleControl();
        }

        if (upscaleSlider != null) {
            upscaleSlider.active = antiAliasingAvailable && activeMode.usesSpatialUpscaleQualityControl();
        }

        if (dlssQualitySlider != null) {
            dlssQualitySlider.active = antiAliasingAvailable && activeMode.usesDlssQualityControl();
        }

        if (fsrQualitySlider != null) {
            fsrQualitySlider.active = antiAliasingAvailable && activeMode.usesFsrQualityControl();
        }

        if (fsrSharpnessSlider != null) {
            fsrSharpnessSlider.active = antiAliasingAvailable && activeMode.usesFsrSharpenControl();
        }
    }
}
