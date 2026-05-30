package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.Options;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
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

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
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
     * Adds the modern-branch video controls after Minecraft has created its normal option rows.
     */
    @Inject(method = "addOptions", at = @At("TAIL"))
    private void saltsAntiAliasing$addVideoModeButton(CallbackInfo callbackInfo) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        AbstractWidget weatherWidget = saltsAntiAliasing$detachOptionWidget(
                saltsAntiAliasing$optionalOption("weatherRadius", "method_75333")
        );
        int initialEntryCount = this.list.children().size();

        saltsAntiAliasing$modeButton = AntiAliasingVideoButtonFactory.create(() -> {
            if (this.minecraft != null) {
                saltsAntiAliasing$pendingScrollAmount = this.list.scrollAmount();
                this.minecraft.setScreen(new VideoSettingsScreen(this.lastScreen, this.minecraft, this.options));
            }
        });
        saltsAntiAliasing$sharpnessSlider = new SharpnessSliderWidget(runtime);
        saltsAntiAliasing$msaaSlider = new MsaaSampleSliderWidget(runtime);
        saltsAntiAliasing$ssaaSlider = new SsaaScaleSliderWidget(runtime);
        saltsAntiAliasing$upscaleSlider = new SpatialUpscaleQualitySliderWidget(runtime);

        saltsAntiAliasing$addPackedControlRows(weatherWidget);

        saltsAntiAliasing$moveInsertedControlsBelowAnisotropy(initialEntryCount);
        saltsAntiAliasing$refreshControlAvailability(runtime.activeMode());

        if (saltsAntiAliasing$pendingScrollAmount >= 0.0d) {
            this.list.setScrollAmount(saltsAntiAliasing$pendingScrollAmount);
            saltsAntiAliasing$pendingScrollAmount = -1.0d;
        }
    }

    /**
     * Keeps sliders enabled only when their active mode can actually consume the value.
     */
    @Inject(method = "tick", at = @At("TAIL"), require = 0)
    private void saltsAntiAliasing$refreshDisabledState(CallbackInfo callbackInfo) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        saltsAntiAliasing$refreshControlAvailability(runtime.activeMode());
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
     * Coordinates move inserted controls below anisotropy within the anti-aliasing render,
     * configuration, or compatibility flow.
     * @param initialEntryCount initial entry count supplied by Minecraft or the caller
     */
    private void saltsAntiAliasing$moveInsertedControlsBelowAnisotropy(int initialEntryCount) {
        OptionInstance<?> anisotropyOption = saltsAntiAliasing$optionalOption("maxAnisotropyBit", "method_76247");
        int anisotropyEntryIndex = saltsAntiAliasing$findEntryIndex(anisotropyOption);
        if (anisotropyEntryIndex < 0) {
            return;
        }

        List<Object> entries = saltsAntiAliasing$entries();
        if (entries.size() <= initialEntryCount) {
            return;
        }

        List<Object> insertedEntries = new ArrayList<>(entries.subList(initialEntryCount, entries.size()));
        entries.subList(initialEntryCount, entries.size()).clear();
        entries.addAll(anisotropyEntryIndex + 1, insertedEntries);
    }

    /**
     * Coordinates find entry index within the anti-aliasing render, configuration, or compatibility
     * flow.
     * @param optionInstance option instance supplied by Minecraft or the caller
     * @return find entry index value produced or selected by this code path
     */
    private int saltsAntiAliasing$findEntryIndex(OptionInstance<?> optionInstance) {
        if (optionInstance == null) {
            return -1;
        }

        AbstractWidget widget = this.list.findOption(optionInstance);
        if (widget == null) {
            return -1;
        }

        List<?> entries = this.list.children();
        for (int index = 0; index < entries.size(); index++) {
            Object entry = entries.get(index);
            if (entry instanceof ContainerEventHandler handler && handler.children().contains(widget)) {
                return index;
            }
        }

        return -1;
    }

    /**
     * Coordinates add packed control rows within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param weatherWidget weather widget supplied by Minecraft or the caller
     */
    private void saltsAntiAliasing$addPackedControlRows(AbstractWidget weatherWidget) {
        List<AbstractWidget> widgets = new ArrayList<>();
        widgets.add(saltsAntiAliasing$sharpnessSlider);
        widgets.add(saltsAntiAliasing$modeButton);
        widgets.add(saltsAntiAliasing$msaaSlider);
        widgets.add(saltsAntiAliasing$ssaaSlider);

        if (weatherWidget != null) {
            widgets.add(weatherWidget);
        }

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

    /**
     * Coordinates detach option widget within the anti-aliasing render, configuration, or
     * compatibility flow.
     * @param optionInstance option instance supplied by Minecraft or the caller
     * @return detach option widget value produced or selected by this code path
     */
    private AbstractWidget saltsAntiAliasing$detachOptionWidget(OptionInstance<?> optionInstance) {
        if (optionInstance == null) {
            return null;
        }

        int entryIndex = saltsAntiAliasing$findEntryIndex(optionInstance);
        if (entryIndex < 0) {
            return null;
        }

        AbstractWidget widget = this.list.findOption(optionInstance);
        if (widget == null) {
            return null;
        }

        List<Object> entries = saltsAntiAliasing$entries();
        entries.remove(entryIndex);
        return widget;
    }

    /**
     * Coordinates entries within the anti-aliasing render, configuration, or compatibility flow.
     * @return entries value produced or selected by this code path
     */
    private List<Object> saltsAntiAliasing$entries() {
        return ((AbstractSelectionListAccessor) this.list).saltsAntiAliasing$children();
    }

    private OptionInstance<?> saltsAntiAliasing$optionalOption(String namedMethodName, String intermediaryMethodName) {
        Method method = saltsAntiAliasing$optionsMethod(namedMethodName);
        if (method == null) {
            method = saltsAntiAliasing$optionsMethod(intermediaryMethodName);
        }

        if (method == null) {
            return null;
        }

        try {
            Object result = method.invoke(this.options);
            if (result instanceof OptionInstance<?> optionInstance) {
                return optionInstance;
            }
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }

        return null;
    }

    private static Method saltsAntiAliasing$optionsMethod(String methodName) {
        try {
            return Options.class.getMethod(methodName);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }
}
