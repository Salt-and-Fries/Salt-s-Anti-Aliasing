package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.Options;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingModeDropdownOverlay;
import org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingVideoSettingsSection;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Implements video settings screen mixin behavior for Salt's Anti Aliasing. Mixin bridge code that
 * hooks Minecraft internals at narrowly chosen call sites so the renderer can be redirected without
 * forking vanilla classes.
 */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
    private static double saltsAntiAliasing$pendingScrollAmount = -1.0d;
    private static boolean saltsAntiAliasing$dropdownExpanded;
    private AntiAliasingVideoSettingsSection.Controls saltsAntiAliasing$sectionControls;
    private AntiAliasingModeDropdownOverlay saltsAntiAliasing$dropdownOverlay;
    private AntiAliasingMode saltsAntiAliasing$displayedMode = AntiAliasingMode.OFF;

    /**
     * Creates a video settings screen mixin instance with the collaborators or initial state
     * supplied by the caller.
     * @param lastScreen last screen value supplied by the caller or Minecraft callback
     * @param options options value supplied by the caller or Minecraft callback
     * @param title title value supplied by the caller or Minecraft callback
     */
    protected VideoSettingsScreenMixin(Screen lastScreen, Options options, Component title) {
        super(lastScreen, options, title);
    }

    /**
     * Adds the Fabric 26.2 video controls after Minecraft has created its normal option rows.
     */
    @Inject(method = "addOptions", at = @At("TAIL"))
    private void saltsAntiAliasing$addVideoModeButton(CallbackInfo callbackInfo) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        saltsAntiAliasing$sectionControls = AntiAliasingVideoSettingsSection.addTo(
                this.list,
                runtime,
                saltsAntiAliasing$dropdownExpanded,
                saltsAntiAliasing$improvedTransparencyEnabled(),
                this::saltsAntiAliasing$toggleDropdown
        );
        saltsAntiAliasing$dropdownOverlay = new AntiAliasingModeDropdownOverlay(
                saltsAntiAliasing$sectionControls.dropdownButton(),
                () -> saltsAntiAliasing$dropdownExpanded,
                this::saltsAntiAliasing$improvedTransparencyEnabled,
                SaltsAntiAliasingClient::runtimeOrNull,
                this::saltsAntiAliasing$selectMode,
                this::saltsAntiAliasing$dismissDropdown
        );
        this.addRenderableOnly(saltsAntiAliasing$dropdownOverlay);
        saltsAntiAliasing$displayedMode = runtime.activeMode();

        if (saltsAntiAliasing$pendingScrollAmount >= 0.0d) {
            this.list.setScrollAmount(saltsAntiAliasing$pendingScrollAmount);
            saltsAntiAliasing$pendingScrollAmount = -1.0d;
        }
    }

    /**
     * Keeps sliders enabled only when their active mode can actually consume the value.
     */
    @Inject(method = "tick", at = @At("TAIL"))
    private void saltsAntiAliasing$refreshDisabledState(CallbackInfo callbackInfo) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        saltsAntiAliasing$refreshControlAvailability(runtime.activeMode());
    }

    /**
     * Coordinates refresh control availability within the anti-aliasing render, configuration, or compatibility flow.
     * @param activeMode active mode value supplied by the caller or Minecraft callback
     */
    private void saltsAntiAliasing$refreshControlAvailability(AntiAliasingMode activeMode) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }
        if (runtime.activeMode() != saltsAntiAliasing$displayedMode) {
            saltsAntiAliasing$rebuildPreservingScroll();
            return;
        }

        AntiAliasingVideoSettingsSection.refresh(
                saltsAntiAliasing$sectionControls,
                runtime,
                saltsAntiAliasing$improvedTransparencyEnabled(),
                saltsAntiAliasing$dropdownExpanded
        );
        if (saltsAntiAliasing$dropdownOverlay != null) {
            saltsAntiAliasing$dropdownOverlay.refresh();
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (saltsAntiAliasing$dropdownOverlay != null
                && saltsAntiAliasing$dropdownOverlay.mouseClicked(event, doubleClick)) {
            return true;
        }

        return saltsAntiAliasing$mouseClickedVanillaChildren(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (saltsAntiAliasing$dropdownOverlay != null
                && saltsAntiAliasing$dropdownOverlay.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }

        return this.getChildAt(mouseX, mouseY)
                .filter(listener -> listener.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount))
                .isPresent();
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (saltsAntiAliasing$dropdownOverlay != null && saltsAntiAliasing$dropdownOverlay.keyPressed(event)) {
            return true;
        }

        return super.keyPressed(event);
    }

    /**
     * Coordinates improved transparency enabled within the anti-aliasing render, configuration, or compatibility flow.
     * @return whether the operation or state is enabled
     */
    private boolean saltsAntiAliasing$improvedTransparencyEnabled() {
        return this.minecraft != null && (Boolean) this.minecraft.options.improvedTransparency().get();
    }

    private void saltsAntiAliasing$toggleDropdown() {
        saltsAntiAliasing$dropdownExpanded = !saltsAntiAliasing$dropdownExpanded;
        if (saltsAntiAliasing$dropdownOverlay != null) {
            saltsAntiAliasing$dropdownOverlay.refresh();
        }
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null) {
            AntiAliasingVideoSettingsSection.refresh(
                    saltsAntiAliasing$sectionControls,
                    runtime,
                    saltsAntiAliasing$improvedTransparencyEnabled(),
                    saltsAntiAliasing$dropdownExpanded
            );
        }
    }

    private void saltsAntiAliasing$dismissDropdown() {
        saltsAntiAliasing$dropdownExpanded = false;
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null) {
            AntiAliasingVideoSettingsSection.refresh(
                    saltsAntiAliasing$sectionControls,
                    runtime,
                    saltsAntiAliasing$improvedTransparencyEnabled(),
                    false
            );
        }
    }

    private boolean saltsAntiAliasing$mouseClickedVanillaChildren(MouseButtonEvent event, boolean doubleClick) {
        GuiEventListener listener = this.getChildAt(event.x(), event.y()).orElse(null);
        if (listener == null || !listener.mouseClicked(event, doubleClick)) {
            return false;
        }

        this.setFocused(listener);
        if (event.button() == 0) {
            this.setDragging(true);
        }
        return true;
    }

    private void saltsAntiAliasing$selectMode(AntiAliasingMode mode) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }
        if (mode != AntiAliasingMode.OFF
                && (saltsAntiAliasing$improvedTransparencyEnabled() || !runtime.canSelectMode(mode))) {
            return;
        }

        runtime.setMode(mode);
        saltsAntiAliasing$dropdownExpanded = false;
        saltsAntiAliasing$rebuildPreservingScroll();
    }

    private void saltsAntiAliasing$rebuildPreservingScroll() {
        if (this.minecraft == null) {
            return;
        }

        saltsAntiAliasing$pendingScrollAmount = this.list.scrollAmount();
        this.minecraft.setScreenAndShow(new VideoSettingsScreen(this.lastScreen, this.minecraft, this.options));
    }
}
