package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.MultiLineTextWidget;
import net.minecraft.client.gui.components.ScrollableLayout;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Presents attribution and the exact licenses included with the release runtime. */
public final class CreditsAndLicensesScreen extends Screen {
    private static final String RUNTIME_ROOT =
            "assets/salts_anti_aliasing/native/dlss/windows-x86_64/";
    private final Screen previous;
    private final String document;
    private final List<MultiLineTextWidget> textWidgets = new ArrayList<>();
    private HeaderAndFooterLayout layout;
    private ScrollableLayout scroll;

    public CreditsAndLicensesScreen(Screen previous) {
        this(previous, Component.translatable("screen.salts_anti_aliasing.credits"), null);
    }

    private CreditsAndLicensesScreen(Screen previous, Component title, String document) {
        super(title);
        this.previous = previous;
        this.document =
                document == null
                        ? null
                        : document.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ");
    }

    @Override
    protected void init() {
        textWidgets.clear();
        layout = new HeaderAndFooterLayout(this);
        layout.addTitleHeader(title, font);
        LinearLayout body = LinearLayout.vertical().spacing(12);
        body.defaultCellSetting().alignHorizontallyCenter();
        if (document != null) {
            addText(body, Component.literal(document));
        } else {
            addText(body, Component.translatable("screen.salts_anti_aliasing.credits.developer"));
            addText(body, Component.translatable("screen.salts_anti_aliasing.credits.nvidia"));
            addText(body, Component.translatable("screen.salts_anti_aliasing.credits.third_party"));
            addLicenseButton(body, "NVIDIA DLSS License", "LICENSE-NVIDIA-DLSS.txt");
            addLicenseButton(body, "NVIDIA Streamline License", "LICENSE-Streamline.txt");
            addLicenseButton(body, "NVIDIA Runtime Terms", "END-USER-TERMS.txt");
        }
        scroll =
                layout.addToContents(
                        new ScrollableLayout(minecraft, body, Math.max(40, height - 80)));
        layout.addToFooter(
                Button.builder(CommonComponents.GUI_BACK, button -> onClose()).width(200).build());
        repositionElements();
        layout.visitWidgets(this::addRenderableWidget);
    }

    private void addText(LinearLayout body, Component text) {
        MultiLineTextWidget widget =
                body.addChild(
                        new MultiLineTextWidget(text, font)
                                .setMaxWidth(Math.max(80, Math.min(500, width - 48))));
        textWidgets.add(widget);
    }

    private void addLicenseButton(LinearLayout body, String label, String file) {
        String resource = RUNTIME_ROOT + file;
        Path downloaded = downloadedLicense(file);
        if (getClass().getClassLoader().getResource(resource) == null && downloaded == null) return;
        body.addChild(
                Button.builder(
                                Component.literal(label),
                                button -> {
                                    String text;
                                    try (var input =
                                            getClass()
                                                    .getClassLoader()
                                                    .getResourceAsStream(resource)) {
                                        text =
                                                input != null
                                                        ? new String(
                                                                input.readAllBytes(),
                                                                StandardCharsets.UTF_8)
                                                        : downloaded == null
                                                                ? "License unavailable."
                                                                : Files.readString(downloaded);
                                    } catch (IOException exception) {
                                        text =
                                                "License unavailable. The complete license is also"
                                                        + " included in the mod jar.";
                                    }
                                    minecraft.setScreenAndShow(
                                            new CreditsAndLicensesScreen(
                                                    this, Component.literal(label), text));
                                })
                        .width(200)
                        .build());
    }

    private Path downloadedLicense(String file) {
        var runtime =
                org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient
                        .runtimeOrNull();
        if (runtime == null || runtime.configSnapshot().dlssPluginPath.isBlank()) return null;
        try {
            Path path = Path.of(runtime.configSnapshot().dlssPluginPath).resolve(file);
            return Files.isRegularFile(path) && Files.size(path) <= 1024 * 1024 ? path : null;
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    @Override
    protected void repositionElements() {
        if (layout == null) return;
        for (MultiLineTextWidget widget : textWidgets)
            widget.setMaxWidth(Math.max(80, Math.min(500, width - 48)));
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
