package org.betterLostItems.salts_anti_aliasing.mixin.client;

import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!saltsAntiAliasing$dropdownExpanded || saltsAntiAliasing$dropdownOverlay == null) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }

        if (this.getFocused() != null) {
            this.clearFocus();
        }
        super.extractRenderState(graphics, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, partialTick);
        saltsAntiAliasing$dropdownOverlay.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$handleDropdownClick(
            MouseButtonEvent event,
            boolean doubleClick,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (saltsAntiAliasing$dropdownOverlay != null
                && saltsAntiAliasing$dropdownOverlay.mouseClicked(event, doubleClick)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "mouseScrolled", at = @At("HEAD"), cancellable = true)
    private void saltsAntiAliasing$handleDropdownScroll(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount,
            CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        if (saltsAntiAliasing$dropdownOverlay != null
                && saltsAntiAliasing$dropdownOverlay.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (saltsAntiAliasing$dropdownOverlay != null
                && saltsAntiAliasing$dropdownOverlay.mouseDragged(event, dragX, dragY)) {
            return true;
        }

        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean overlayHandled = saltsAntiAliasing$dropdownOverlay != null
                && saltsAntiAliasing$dropdownOverlay.mouseReleased(event);
        boolean screenHandled = super.mouseReleased(event);
        return overlayHandled || screenHandled;
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
