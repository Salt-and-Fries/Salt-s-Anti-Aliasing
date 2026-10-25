package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssRuntime;

/**
 * Complete method-aware configuration, shared by Video Settings, Mod Menu and Sodium. The modal
 * picker and dependent controls stay synchronized with the live configuration.
 */
public final class AntiAliasingConfigScreen extends OptionsSubScreen {
    private static final String TITLE_KEY = "screen.salts_anti_aliasing.config";

    private final double initialScrollAmount;
    private AntiAliasingVideoSettingsSection.Controls sectionControls;
    private AntiAliasingModeDropdownOverlay dropdownOverlay;
    private AntiAliasingMode displayedMode = AntiAliasingMode.OFF;
    private boolean dropdownExpanded;
    private boolean advanced;
    private Button statusButton;
    private Button allocationButton;
    private Button frameGenerationStatusButton;
    private int statusTicks;
    private Object displayedPreAa;
    private Object displayedMipBias;
    private Object displayedHandAa;
    private boolean displayedControlsAvailable;
    private Button dlssSetupButton;
    private Button dlssMemoryButton;
    private Button motionCoverageButton;

    /**
     * Creates the Mod Menu configuration screen.
     *
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
    protected void init() {
        layout.removeChildren();
        dlssSetupButton = null;
        dlssMemoryButton = motionCoverageButton = null;
        frameGenerationStatusButton = null;
        super.init();
    }

    private Component dlssSetupLabel() {
        return Component.translatable(
                DlssRuntime.instance().hasConfiguredRuntime()
                        ? "screen.salts_anti_aliasing.dlss_setup.change"
                        : "screen.salts_anti_aliasing.dlss_setup");
    }

    @Override
    protected void addOptions() {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }

        sectionControls =
                AntiAliasingVideoSettingsSection.addControlsTo(
                        this.list,
                        runtime,
                        dropdownExpanded,
                        improvedTransparencyEnabled(),
                        this::toggleDropdown,
                        this);
        dropdownOverlay =
                new AntiAliasingModeDropdownOverlay(
                        sectionControls.dropdownButton(),
                        () -> dropdownExpanded,
                        this::improvedTransparencyEnabled,
                        SaltsAntiAliasingClient::runtimeOrNull,
                        this::selectMode,
                        this::dismissDropdown);
        displayedMode = runtime.activeMode();
        displayedControlsAvailable = runtime.canConfigureMode(displayedMode);
        displayedPreAa = runtime.configSnapshot().tuning.spatialAa;
        displayedMipBias = runtime.configSnapshot().tuning.mipBiasMode;
        displayedHandAa = runtime.configSnapshot().tuning.upscalerHandAa;

        MethodTuningControls.add(this.list, runtime, advanced);
        this.list.addBig(
                Button.builder(
                                Component.translatable(
                                        "options.salts_anti_aliasing.advanced",
                                        Component.translatable(
                                                advanced ? "options.on" : "options.off")),
                                b -> {
                                    advanced = !advanced;
                                    rebuildPreservingScroll();
                                })
                        .width(this.list.getRowWidth())
                        .build());
        if (displayedMode == AntiAliasingMode.DLSS_SUPER_RESOLUTION) {
            dlssSetupButton = Button.builder(dlssSetupLabel(),
                    b -> minecraft.setScreenAndShow(new DlssSetupScreen(this)))
                    .width(this.list.getRowWidth()).build();
            this.list.addBig(dlssSetupButton);
        }
        this.list.addHeader(Component.translatable("options.salts_anti_aliasing.diagnostics"));
        statusButton =
                Button.builder(Component.literal(runtime.renderStatusText()), b -> {})
                        .width(this.list.getRowWidth())
                        .build();
        statusButton.active = false;
        this.list.addBig(statusButton);
        allocationButton =
                Button.builder(Component.literal(runtime.allocationStatusText()), b -> {})
                        .width(this.list.getRowWidth())
                        .build();
        allocationButton.active = false;
        allocationButton.setTooltip(
                Tooltip.create(
                        Component.translatable("options.salts_anti_aliasing.allocations.tooltip")));
        this.list.addBig(allocationButton);
        if (advanced && displayedMode == AntiAliasingMode.DLSS_SUPER_RESOLUTION) {
            Button version = Button.builder(Component.literal("DLSS runtime: 310.9.1 / Streamline 2.14.1"), b -> {})
                    .width(this.list.getRowWidth()).build();
            version.active = false;
            this.list.addBig(version);
            dlssMemoryButton = Button.builder(Component.literal(runtime.dlssMemoryStatusText()), b -> {})
                    .width(this.list.getRowWidth()).tooltip(Tooltip.create(Component.literal(
                            "NVIDIA's SDK estimate, separate from the owned AA texture estimate above. Neither includes all driver/game VRAM."))).build();
            dlssMemoryButton.active = false;
            this.list.addBig(dlssMemoryButton);
            motionCoverageButton = Button.builder(Component.literal(runtime.dlssMotionCoverageText()), b -> {})
                    .width(this.list.getRowWidth()).build();
            motionCoverageButton.active = false;
            this.list.addBig(motionCoverageButton);
        }
        if (runtime.activeMode().usesFsrFrameGeneration()) {
            frameGenerationStatusButton =
                    Button.builder(Component.literal(runtime.frameGenerationStatusText()), b -> {})
                            .width(this.list.getRowWidth())
                            .build();
            frameGenerationStatusButton.active = false;
            this.list.addBig(frameGenerationStatusButton);
        }
        this.list.addBig(
                Button.builder(
                                Component.translatable("options.salts_anti_aliasing.retry"),
                                b -> runtime.retryRendering())
                        .width(this.list.getRowWidth())
                        .tooltip(
                                Tooltip.create(
                                        Component.translatable(
                                                "options.salts_anti_aliasing.retry.tooltip")))
                        .build());
        if (advanced
                && (runtime.activeMode().usesFsrQualityControl()
                        || runtime.activeMode() == AntiAliasingMode.DLSS_SUPER_RESOLUTION)) {
            this.list.addBig(
                    Button.builder(
                                    Component.translatable(
                                            "options.salts_anti_aliasing.native_runtime"),
                                    b ->
                                            minecraft.setScreenAndShow(
                                                    new NativeRuntimeSettingsScreen(this)))
                            .width(this.list.getRowWidth())
                            .build());
        }
        this.list.addBig(
                Button.builder(
                                Component.translatable("options.salts_anti_aliasing.reset_method"),
                                b -> {
                                    runtime.resetMethodSettings();
                                    rebuildPreservingScroll();
                                })
                        .width(this.list.getRowWidth())
                        .build());
        this.list.addBig(
                Button.builder(
                                Component.translatable("options.salts_anti_aliasing.reset_all"),
                                b -> {
                                    runtime.resetAllSettings();
                                    rebuildPreservingScroll();
                                })
                        .width(this.list.getRowWidth())
                        .tooltip(
                                Tooltip.create(
                                        Component.translatable(
                                                "options.salts_anti_aliasing.reset_all.tooltip")))
                        .build());
        if (displayedMode == AntiAliasingMode.DLSS_SUPER_RESOLUTION
                && !displayedControlsAvailable) {
            for (ContainerEventHandler entry : this.list.children())
                for (var child : entry.children())
                    if (child instanceof AbstractWidget widget
                            && widget != sectionControls.dropdownButton()
                            && widget != dlssSetupButton) widget.active = false;
        }
        this.list.addBig(
                Button.builder(
                                Component.translatable("screen.salts_anti_aliasing.credits"),
                                b -> minecraft.setScreenAndShow(new CreditsAndLicensesScreen(this)))
                        .width(this.list.getRowWidth())
                        .build());

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
        if (runtime.activeMode() != displayedMode
                || runtime.configSnapshot().tuning.spatialAa != displayedPreAa
                || runtime.configSnapshot().tuning.mipBiasMode != displayedMipBias
                || runtime.configSnapshot().tuning.upscalerHandAa != displayedHandAa
                || (displayedMode == AntiAliasingMode.DLSS_SUPER_RESOLUTION
                        && runtime.canConfigureMode(displayedMode) != displayedControlsAvailable)) {
            rebuildPreservingScroll();
            return;
        }
        if (statusButton != null && ++statusTicks % 10 == 0) {
            statusButton.setMessage(Component.literal(runtime.renderStatusText()));
            allocationButton.setMessage(Component.literal(runtime.allocationStatusText()));
            if (frameGenerationStatusButton != null)
                frameGenerationStatusButton.setMessage(
                        Component.literal(runtime.frameGenerationStatusText()));
            if (dlssSetupButton != null) dlssSetupButton.setMessage(dlssSetupLabel());
            if (dlssMemoryButton != null) dlssMemoryButton.setMessage(Component.literal(runtime.dlssMemoryStatusText()));
            if (motionCoverageButton != null) {
                motionCoverageButton.setMessage(Component.literal(runtime.dlssMotionCoverageText()));
                motionCoverageButton.setTooltip(Tooltip.create(Component.literal(runtime.dlssMotionCoverageDetails())));
            }
        }

        AntiAliasingVideoSettingsSection.refresh(
                sectionControls, runtime, improvedTransparencyEnabled(), dropdownExpanded);
        if (dropdownOverlay != null) {
            dropdownOverlay.refresh();
        }
    }

    @Override
    public void extractRenderState(
            GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!dropdownExpanded || dropdownOverlay == null) {
            super.extractRenderState(graphics, mouseX, mouseY, partialTick);
            return;
        }

        if (this.getFocused() != null) {
            this.clearFocus();
        }
        super.extractRenderState(
                graphics, Integer.MIN_VALUE / 2, Integer.MIN_VALUE / 2, partialTick);
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
            double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (dropdownOverlay != null
                && dropdownOverlay.mouseScrolled(
                        mouseX, mouseY, horizontalAmount, verticalAmount)) {
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
        return this.minecraft != null
                && (Boolean) this.minecraft.options.improvedTransparency().get();
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
                    sectionControls, runtime, improvedTransparencyEnabled(), dropdownExpanded);
        }
    }

    private void selectMode(AntiAliasingMode mode) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime == null) {
            return;
        }
        if (mode != AntiAliasingMode.OFF && !runtime.canSelectMode(mode)) {
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

        AntiAliasingConfigScreen next =
                new AntiAliasingConfigScreen(this.lastScreen, this.list.scrollAmount());
        next.advanced = this.advanced;
        this.minecraft.setScreenAndShow(next);
    }

    @Override
    public void removed() {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        if (runtime != null) runtime.saveSettings();
        super.removed();
    }
}
