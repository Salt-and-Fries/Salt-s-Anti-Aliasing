package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.layouts.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import org.betterLostItems.salts_anti_aliasing.SaltsAntiAliasing;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.*;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/** User-supplied runtime setup remains reachable even when DLSS cannot be selected yet. */
public final class DlssSetupScreen extends Screen {
    private final Screen previous;
    private final List<MultiLineTextWidget> text = new ArrayList<>();
    private HeaderAndFooterLayout layout;
    private ScrollableLayout scroll;
    private boolean busy;
    private Component feedback;

    public DlssSetupScreen(Screen previous) {
        super(Component.translatable("screen.salts_anti_aliasing.dlss_setup"));
        this.previous = previous;
    }

    @Override
    protected void init() {
        text.clear();
        layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(title, font);
        LinearLayout body = LinearLayout.vertical().spacing(12);
        body.defaultCellSetting().alignHorizontallyCenter();
        addText(
                body,
                Component.translatable(
                        "screen.salts_anti_aliasing.dlss_setup.intro",
                        DlssRuntimeFiles.SUPPORTED_VERSION));
        addText(
                body,
                Component.translatable(
                        "screen.salts_anti_aliasing.dlss_setup.status",
                        Component.literal(DlssRuntime.instance().status().message())));
        if (!DlssRuntimeFiles.supportedPlatform())
            addText(body, Component.translatable("screen.salts_anti_aliasing.dlss_setup.platform"));
        Button download =
                body.addChild(
                        Button.builder(
                                        Component.translatable(
                                                "screen.salts_anti_aliasing.dlss_setup.download"),
                                        b -> {
                                            if (!org.lwjgl.sdl.SDLMisc.SDL_OpenURL(
                                                    DlssRuntimeFiles.DOWNLOAD_URL)) {
                                                feedback =
                                                        Component.translatable(
                                                                "screen.salts_anti_aliasing.dlss_setup.failed",
                                                                org.lwjgl.sdl.SDLError
                                                                        .SDL_GetError());
                                                refresh();
                                            }
                                        })
                                .width(260)
                                .build());
        download.active = !busy;
        addPicker(body, false, "file");
        addPicker(body, true, "folder");
        addText(body, Component.translatable("screen.salts_anti_aliasing.dlss_setup.instructions"));
        if (feedback != null) addText(body, feedback);
        var config = SaltsAntiAliasingClient.runtime().configSnapshot();
        if (!config.dlssPluginPath.isBlank()) {
            addText(
                    body,
                    Component.translatable("screen.salts_anti_aliasing.dlss_setup.connected"));
            addText(body, Component.literal(config.dlssPluginPath));
        }
        if (System.getProperty("salts.dlss.pluginPath") != null
                || System.getenv("SALTS_DLSS_PLUGIN_PATH") != null)
            addText(body, Component.translatable("screen.salts_anti_aliasing.dlss_setup.override"));
        scroll =
                layout.addToContents(
                        new ScrollableLayout(minecraft, body, Math.max(40, height - 80)));
        layout.addToFooter(
                Button.builder(CommonComponents.GUI_DONE, b -> onClose()).width(200).build());
        repositionElements();
        layout.visitWidgets(this::addRenderableWidget);
    }

    private void addPicker(LinearLayout body, boolean folder, String key) {
        Button button =
                body.addChild(
                        Button.builder(
                                        Component.translatable(
                                                "screen.salts_anti_aliasing.dlss_setup." + key),
                                        b -> choose(folder))
                                .width(260)
                                .build());
        button.active = !busy && DlssRuntimeFiles.supportedPlatform();
    }

    private void addText(LinearLayout body, Component value) {
        text.add(
                body.addChild(
                        new MultiLineTextWidget(value, font)
                                .setMaxWidth(Math.max(80, Math.min(500, width - 48)))));
    }

    private void choose(boolean folder) {
        if (busy) return;
        busy = true;
        feedback = Component.translatable("screen.salts_anti_aliasing.dlss_setup.picking");
        refresh();
        NativeRuntimeFilePicker.choose(minecraft, folder, this::picked);
    }

    private void picked(NativeRuntimeFilePicker.Selection selection) {
        if (selection.error() != null) {
            finishError(selection.error());
            return;
        }
        if (selection.path() == null) {
            busy = false;
            feedback = Component.translatable("screen.salts_anti_aliasing.dlss_setup.cancelled");
            refresh();
            return;
        }
        feedback = Component.translatable("screen.salts_anti_aliasing.dlss_setup.importing");
        refresh();
        Path gameDirectory = minecraft.gameDirectory.toPath();
        CompletableFuture.supplyAsync(
                        () -> {
                            try {
                                return DlssRuntimeFiles.install(selection.path(), gameDirectory);
                            } catch (Exception exception) {
                                throw new java.util.concurrent.CompletionException(exception);
                            }
                        },
                        task -> Thread.ofVirtual().name("Salt's DLSS runtime import").start(task))
                .whenComplete((path, failure) -> minecraft.schedule(() -> imported(path, failure)));
    }

    private void imported(Path path, Throwable failure) {
        if (failure != null) {
            Throwable cause = failure.getCause() == null ? failure : failure.getCause();
            SaltsAntiAliasing.LOGGER.warn("DLSS runtime import failed", cause);
            finishError(cause.getMessage());
            return;
        }
        DlssRuntime.instance().requireRestart();
        SaltsAntiAliasingClient.runtime()
                .editSettings(
                        config -> {
                            // The mod supplies its matching bridge.
                            config.dlssBridgePath = "";
                            config.dlssPluginPath = path.toString();
                        });
        busy = false;
        feedback = Component.translatable("screen.salts_anti_aliasing.dlss_setup.success");
        refresh();
    }

    private void finishError(String detail) {
        busy = false;
        feedback =
                Component.translatable(
                        "screen.salts_anti_aliasing.dlss_setup.failed",
                        detail == null ? "Unknown error" : detail);
        refresh();
    }

    private void refresh() {
        if (minecraft.gui.screen() == this) {
            clearWidgets();
            init();
        }
    }

    @Override
    protected void repositionElements() {
        if (layout == null) return;
        for (var widget : text) widget.setMaxWidth(Math.max(80, Math.min(500, width - 48)));
        int contentHeight = Math.max(40, layout.getContentHeight() - 8);
        scroll.setMaxHeight(contentHeight);
        scroll.setMinHeight(contentHeight);
        layout.arrangeElements();
    }

    @Override
    public void onClose() {
        minecraft.setScreenAndShow(previous);
    }
}
