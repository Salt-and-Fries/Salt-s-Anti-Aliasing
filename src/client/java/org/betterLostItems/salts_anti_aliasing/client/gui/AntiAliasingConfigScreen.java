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
 * Documents anti aliasing config screen behavior for Salt's Anti Aliasing. Client UI code that turns
 * runtime configuration into player-facing controls.
 */
public final class AntiAliasingConfigScreen extends OptionsSubScreen {
    private static final String TITLE_KEY = "screen.salts_anti_aliasing.config";

    private Button modeButton;
    private SharpnessSliderWidget sharpnessSlider;
    private MsaaSampleSliderWidget msaaSlider;
    private SsaaScaleSliderWidget ssaaSlider;
    private SpatialUpscaleQualitySliderWidget upscaleSlider;

    /**
     * Creates a anti aliasing config screen with the collaborators or initial state supplied by the
     * caller.
     * @param lastScreen last screen supplied by Minecraft or the caller
     */
    public AntiAliasingConfigScreen(Screen lastScreen) {
        super(lastScreen, Minecraft.getInstance().options, Component.translatable(TITLE_KEY));
    }

    /**
     * Coordinates add options within the anti-aliasing render, configuration, or compatibility flow.
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

        list.addSmall(List.of(
                modeButton,
                sharpnessSlider,
                msaaSlider,
                ssaaSlider,
                upscaleSlider
        ));
        refreshControlAvailability(runtime.activeMode());
    }

    /**
     * Coordinates tick within the anti-aliasing render, configuration, or compatibility flow.
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
     * Coordinates refresh control availability within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param activeMode active mode supplied by Minecraft or the caller
     */
    private void refreshControlAvailability(AntiAliasingMode activeMode) {
        boolean antiAliasingAvailable = minecraft != null && !minecraft.useShaderTransparency();

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
    }
}
