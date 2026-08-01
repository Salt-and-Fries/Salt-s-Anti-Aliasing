package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

/**
 * Mod Menu's complete anti-aliasing configuration screen. It intentionally uses the same control
 * section and modal mode picker as Minecraft's integrated Video Settings so the two surfaces stay
 * synchronized whenever modes or mode-specific options change.
 */
public final class AntiAliasingConfigScreen extends OptionsSubScreen {
    private static final String TITLE_KEY = "screen.salts_anti_aliasing.config";

    private final double initialScrollAmount;
    private AntiAliasingVideoSettingsSection.Controls sectionControls;
    private AntiAliasingModeDropdownOverlay dropdownOverlay;
    private AntiAliasingMode displayedMode = AntiAliasingMode.OFF;
    private boolean dropdownExpanded;

    /**
     * Creates the Mod Menu configuration screen.
     * @param lastScreen screen to restore when the player selects Done
     */
    public AntiAliasingConfigScreen(Screen lastScreen) {
        this(lastScreen, -1.0d);
    }

    private AntiAliasingConfigScreen(Screen lastScreen, double initialScrollAmount) {
        super(lastScreen, Minecraft.getInstance().options, Component.translatable(TITLE_KEY));
        this.initialScrollAmount = initialScrollAmount;
    }

    @Override
    protected void addOptions() {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        sectionControls = AntiAliasingVideoSettingsSection.addControlsTo(
                this.list,
                runtime,
                dropdownExpanded,
                improvedTransparencyEnabled(),
                this::toggleDropdown
        );
        dropdownOverlay = new AntiAliasingModeDropdownOverlay(
                sectionControls.dropdownButton(),
                () -> dropdownExpanded,
                this::improvedTransparencyEnabled,
                SaltsAntiAliasingClient::runtimeOrNull,
                this::selectMode,
                this::dismissDropdown
        );
        displayedMode = runtime.activeMode();

        if (initialScrollAmount >= 0.0d) {
            this.list.setScrollAmount(initialScrollAmount);
        }
    }

    @Override
    public void tick() {
        super.tick();

        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }
        if (runtime.activeMode() != displayedMode) {
            rebuildPreservingScroll();
            return;
        }

        AntiAliasingVideoSettingsSection.refresh(
                sectionControls,
                runtime,
                improvedTransparencyEnabled(),
                dropdownExpanded
        );
        if (dropdownOverlay != null) {
            dropdownOverlay.refresh();
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!dropdownExpanded || dropdownOverlay == null) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }

        if (this.getFocused() != null) {
            this.clearFocus();
        }
        super.extractRenderState(graphics, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, partialTick);
        dropdownOverlay.extractRenderState(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (dropdownOverlay != null && dropdownOverlay.mouseClicked(event, doubleClick)) {
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(
            double mouseX,
            double mouseY,
            double horizontalAmount,
            double verticalAmount
    ) {
        if (dropdownOverlay != null
                && dropdownOverlay.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount)) {
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (dropdownOverlay != null && dropdownOverlay.mouseDragged(event, dragX, dragY)) {
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean overlayHandled = dropdownOverlay != null && dropdownOverlay.mouseReleased(event);
        boolean screenHandled = super.mouseReleased(event);
        return overlayHandled || screenHandled;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (dropdownOverlay != null && dropdownOverlay.keyPressed(event)) {
            return true;
        }
        return super.keyPressed(event);
    }

    private boolean improvedTransparencyEnabled() {
        return this.minecraft != null && (Boolean) this.minecraft.options.improvedTransparency().get();
    }

    private void toggleDropdown() {
        dropdownExpanded = !dropdownExpanded;
        refreshDropdownState();
    }

    private void dismissDropdown() {
        dropdownExpanded = false;
        refreshDropdownState();
    }

    private void refreshDropdownState() {
        if (dropdownOverlay != null) {
            dropdownOverlay.refresh();
        }

        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null) {
            AntiAliasingVideoSettingsSection.refresh(
                    sectionControls,
                    runtime,
                    improvedTransparencyEnabled(),
                    dropdownExpanded
            );
        }
    }

    private void selectMode(AntiAliasingMode mode) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }
        if (mode != AntiAliasingMode.OFF
                && (improvedTransparencyEnabled() || !runtime.canSelectMode(mode))) {
            return;
        }

        runtime.setMode(mode);
        dropdownExpanded = false;
        rebuildPreservingScroll();
    }

    private void rebuildPreservingScroll() {
        if (this.minecraft == null) {
            return;
        }

        this.minecraft.setScreenAndShow(new AntiAliasingConfigScreen(this.lastScreen, this.list.scrollAmount()));
    }
}
