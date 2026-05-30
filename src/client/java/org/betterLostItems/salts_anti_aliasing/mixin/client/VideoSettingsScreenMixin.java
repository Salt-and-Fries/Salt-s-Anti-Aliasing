package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.Options;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingVideoButtonFactory;
import org.betterLostItems.salts_anti_aliasing.client.gui.MsaaSampleSliderWidget;
import org.betterLostItems.salts_anti_aliasing.client.gui.SpatialUpscaleQualitySliderWidget;
import org.betterLostItems.salts_anti_aliasing.client.gui.SharpnessSliderWidget;
import org.betterLostItems.salts_anti_aliasing.client.gui.SsaaScaleSliderWidget;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Documents video settings screen mixin behavior for Salt's Anti Aliasing. Mixin bridge code for
 * carefully scoped hooks into Minecraft rendering and options screens.
 */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
    private static double saltsAntiAliasing$pendingScrollAmount = -1.0d;
    private Button saltsAntiAliasing$modeButton;
    private SharpnessSliderWidget saltsAntiAliasing$sharpnessSlider;
    private MsaaSampleSliderWidget saltsAntiAliasing$msaaSlider;
    private SsaaScaleSliderWidget saltsAntiAliasing$ssaaSlider;
    private SpatialUpscaleQualitySliderWidget saltsAntiAliasing$upscaleSlider;

    /**
     * Creates a video settings screen mixin with the collaborators or initial state supplied by the
     * caller.
     * @param lastScreen last screen supplied by Minecraft or the caller
     * @param options options supplied by Minecraft or the caller
     * @param title title supplied by Minecraft or the caller
     */
    protected VideoSettingsScreenMixin(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    /**
     * Adds the video controls after Minecraft has created its normal option rows.
     */
    @Inject(method = "addOptions", at = @At("TAIL"))
    private void saltsAntiAliasing$addVideoModeButton(CallbackInfo callbackInfo) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        saltsAntiAliasing$modeButton = AntiAliasingVideoButtonFactory.create(() -> {
            if (this.minecraft != null) {
                saltsAntiAliasing$pendingScrollAmount = this.list.getScrollAmount();
                this.minecraft.setScreen(new VideoSettingsScreen(this.lastScreen, this.minecraft, this.options));
            }
        });
        saltsAntiAliasing$sharpnessSlider = new SharpnessSliderWidget(runtime);
        saltsAntiAliasing$msaaSlider = new MsaaSampleSliderWidget(runtime);
        saltsAntiAliasing$ssaaSlider = new SsaaScaleSliderWidget(runtime);
        saltsAntiAliasing$upscaleSlider = new SpatialUpscaleQualitySliderWidget(runtime);

        saltsAntiAliasing$addPackedControlRows();
        saltsAntiAliasing$refreshControlAvailability(runtime.activeMode());

        if (saltsAntiAliasing$pendingScrollAmount >= 0.0d) {
            this.list.setScrollAmount(saltsAntiAliasing$pendingScrollAmount);
            saltsAntiAliasing$pendingScrollAmount = -1.0d;
        }
    }

    /**
     * Coordinates refresh control availability within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param activeMode active mode supplied by Minecraft or the caller
     */
    private void saltsAntiAliasing$refreshControlAvailability(AntiAliasingMode activeMode) {
        boolean antiAliasingAvailable = !saltsAntiAliasing$improvedTransparencyEnabled();

        if (saltsAntiAliasing$modeButton != null) {
            saltsAntiAliasing$modeButton.active = antiAliasingAvailable;
            AntiAliasingVideoButtonFactory.refresh(saltsAntiAliasing$modeButton, activeMode, antiAliasingAvailable);
        }

        if (saltsAntiAliasing$sharpnessSlider != null) {
            saltsAntiAliasing$sharpnessSlider.active = antiAliasingAvailable && activeMode.usesSharpenControl();
        }

        if (saltsAntiAliasing$msaaSlider != null) {
            saltsAntiAliasing$msaaSlider.active = antiAliasingAvailable && activeMode.usesMsaaSampleControl();
        }

        if (saltsAntiAliasing$ssaaSlider != null) {
            saltsAntiAliasing$ssaaSlider.active = antiAliasingAvailable && activeMode.usesSsaaScaleControl();
        }

        if (saltsAntiAliasing$upscaleSlider != null) {
            saltsAntiAliasing$upscaleSlider.active = antiAliasingAvailable && activeMode.usesSpatialUpscaleQualityControl();
        }
    }

    /**
     * Coordinates add packed control rows within the anti-aliasing render, configuration, or
     * compatibility flow.
     */
    private void saltsAntiAliasing$addPackedControlRows() {
        List<AbstractWidget> widgets = new ArrayList<>();
        widgets.add(saltsAntiAliasing$sharpnessSlider);
        widgets.add(saltsAntiAliasing$modeButton);
        widgets.add(saltsAntiAliasing$msaaSlider);
        widgets.add(saltsAntiAliasing$ssaaSlider);
        widgets.add(saltsAntiAliasing$upscaleSlider);
        this.list.addSmall(widgets);
    }

    /**
     * Coordinates improved transparency enabled within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @return improved transparency enabled value produced or selected by this code path
     */
    private boolean saltsAntiAliasing$improvedTransparencyEnabled() {
        return this.minecraft != null && this.minecraft.useShaderTransparency();
    }

}
