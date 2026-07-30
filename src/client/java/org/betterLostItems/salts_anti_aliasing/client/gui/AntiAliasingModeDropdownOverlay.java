package org.betterLostItems.salts_anti_aliasing.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Renders the video settings anti-aliasing mode picker as a compact popover instead of adding
 * temporary rows to Minecraft's options list.
 */
public final class AntiAliasingModeDropdownOverlay extends AbstractWidget {
    private static final int VISIBLE_OPTIONS = 5;
    private static final int OPTION_HEIGHT = 20;
    private static final int BORDER = 1;
    private static final int GAP = 2;
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_SCROLLBAR_THUMB_HEIGHT = 10;
    private static final int FOOTER_MARGIN = 32;
    private static final int TOOLTIP_MAX_WIDTH = 170;
    private static final int TOOLTIP_HORIZONTAL_MARGIN = 8;
    private static final int BACKGROUND_COLOR = 0xF0101010;
    private static final int OUTLINE_COLOR = 0xFF7A7A7A;
    private static final int ROW_HOVER_COLOR = 0x803F6B9D;
    private static final int ROW_SELECTED_COLOR = 0x805B7E36;
    private static final int ROW_DISABLED_HOVER_COLOR = 0x70404040;
    private static final int SCROLLBAR_TRACK_COLOR = 0x80303030;
    private static final int SCROLLBAR_THUMB_COLOR = 0xFFC6C6C6;
    private static final int TEXT_COLOR = 0xFFFFFFFF;
    private static final int DISABLED_TEXT_COLOR = 0xFF888888;
    private static final String MODE_OPTION_TOOLTIP_KEY = "options.salts_anti_aliasing.mode.option.tooltip";
    private static final String MODE_OPTION_SELECTED_TOOLTIP_KEY =
            "options.salts_anti_aliasing.mode.option.tooltip.selected";
    private static final String MODE_OPTION_DISABLED_TOOLTIP_KEY =
            "options.salts_anti_aliasing.mode.option.tooltip.disabled";
    private static final String MODE_OPTION_IMPROVED_TRANSPARENCY_TOOLTIP_KEY =
            "options.salts_anti_aliasing.mode.option.tooltip.improved_transparency";

    private final Button anchorButton;
    private final BooleanSupplier openSupplier;
    private final BooleanSupplier improvedTransparencySupplier;
    private final Supplier<RenderRuntime> runtimeSupplier;
    private final Consumer<AntiAliasingMode> onModeSelected;
    private final Runnable onDismissed;
    private int firstVisibleIndex;

    public AntiAliasingModeDropdownOverlay(
            Button anchorButton,
            BooleanSupplier openSupplier,
            BooleanSupplier improvedTransparencySupplier,
            Supplier<RenderRuntime> runtimeSupplier,
            Consumer<AntiAliasingMode> onModeSelected,
            Runnable onDismissed
    ) {
        super(0, 0, anchorButton.getWidth(), OPTION_HEIGHT * VISIBLE_OPTIONS + BORDER * 2, Component.empty());
        this.anchorButton = anchorButton;
        this.openSupplier = openSupplier;
        this.improvedTransparencySupplier = improvedTransparencySupplier;
        this.runtimeSupplier = runtimeSupplier;
        this.onModeSelected = onModeSelected;
        this.onDismissed = onDismissed;
        this.active = true;
        this.visible = true;
    }

    public void refresh() {
        firstVisibleIndex = clamp(firstVisibleIndex, 0, maxFirstVisibleIndex());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        RenderRuntime runtime = runtimeSupplier.get();
        if (!isOpen() || runtime == null) {
            return;
        }

        List<AntiAliasingMode> modes = AntiAliasingMode.implementedModes();
        if (modes.isEmpty()) {
            return;
        }

        updateBounds(graphics.guiWidth(), graphics.guiHeight(), modes.size());
        graphics.nextStratum();
        graphics.fill(getX(), getY(), getRight(), getBottom(), BACKGROUND_COLOR);
        graphics.outline(getX(), getY(), getWidth(), getHeight(), OUTLINE_COLOR);

        int visibleRows = visibleRows(modes.size());
        int contentX = getX() + BORDER;
        int contentY = getY() + BORDER;
        int contentWidth = getWidth() - BORDER * 2;
        boolean scrollbarVisible = modes.size() > visibleRows;
        int rowTextWidth = contentWidth - (scrollbarVisible ? SCROLLBAR_WIDTH + 4 : 4);
        int rowWidth = contentWidth - (scrollbarVisible ? SCROLLBAR_WIDTH + 2 : 0);
        Font font = Minecraft.getInstance().font;

        graphics.enableScissor(contentX, contentY, contentX + contentWidth, contentY + visibleRows * OPTION_HEIGHT);
        for (int row = 0; row < visibleRows; row++) {
            int modeIndex = firstVisibleIndex + row;
            if (modeIndex >= modes.size()) {
                break;
            }

            AntiAliasingMode mode = modes.get(modeIndex);
            int rowY = contentY + row * OPTION_HEIGHT;
            boolean selectable = canSelectMode(runtime, mode);
            boolean hovered = isInRect(mouseX, mouseY, contentX, rowY, rowWidth, OPTION_HEIGHT);
            if (mode == runtime.activeMode()) {
                graphics.fill(contentX, rowY, contentX + rowWidth, rowY + OPTION_HEIGHT, ROW_SELECTED_COLOR);
            } else if (hovered) {
                graphics.fill(
                        contentX,
                        rowY,
                        contentX + rowWidth,
                        rowY + OPTION_HEIGHT,
                        selectable ? ROW_HOVER_COLOR : ROW_DISABLED_HOVER_COLOR
                );
            }

            Component label = trimmedLabel(font, ClientText.label(mode), rowTextWidth);
            graphics.centeredText(
                    font,
                    label,
                    contentX + rowWidth / 2,
                    rowY + (OPTION_HEIGHT - font.lineHeight) / 2,
                    selectable ? TEXT_COLOR : DISABLED_TEXT_COLOR
            );

            if (hovered) {
                int tooltipWidth = Math.max(
                        1,
                        Math.min(TOOLTIP_MAX_WIDTH, graphics.guiWidth() - TOOLTIP_HORIZONTAL_MARGIN)
                );
                List<FormattedCharSequence> tooltipLines = font.split(modeTooltip(runtime, mode), tooltipWidth);
                graphics.setTooltipForNextFrame(
                        font,
                        tooltipLines,
                        DefaultTooltipPositioner.INSTANCE,
                        mouseX,
                        mouseY,
                        true
                );
            }
        }
        graphics.disableScissor();

        if (scrollbarVisible) {
            drawScrollbar(graphics, modes.size(), visibleRows, contentX + contentWidth - SCROLLBAR_WIDTH, contentY);
        }
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (!isOpen()) {
            return false;
        }

        double mouseX = event.x();
        double mouseY = event.y();
        if (isAnchorClick(mouseX, mouseY)) {
            return false;
        }
        if (!isMouseOver(mouseX, mouseY)) {
            onDismissed.run();
            return true;
        }

        RenderRuntime runtime = runtimeSupplier.get();
        if (runtime == null) {
            return true;
        }

        List<AntiAliasingMode> modes = AntiAliasingMode.implementedModes();
        int visibleRows = visibleRows(modes.size());
        if (isScrollbarClick(mouseX, mouseY, modes.size(), visibleRows)) {
            jumpToScrollbarPosition(mouseY, modes.size(), visibleRows);
            return true;
        }

        int modeIndex = firstVisibleIndex + (int) ((mouseY - getY() - BORDER) / OPTION_HEIGHT);
        if (modeIndex >= 0 && modeIndex < modes.size()) {
            AntiAliasingMode mode = modes.get(modeIndex);
            if (canSelectMode(runtime, mode)) {
                onModeSelected.accept(mode);
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (!isOpen() || !isMouseOver(mouseX, mouseY)) {
            return false;
        }

        if (verticalAmount > 0.0d) {
            scrollBy(-1);
        } else if (verticalAmount < 0.0d) {
            scrollBy(1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (!isOpen()) {
            return false;
        }

        if (event.isEscape()) {
            onDismissed.run();
            return true;
        }
        if (event.isUp()) {
            scrollBy(-1);
            return true;
        }
        if (event.isDown()) {
            scrollBy(1);
            return true;
        }
        return false;
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        return isOpen() && isInRect(mouseX, mouseY, getX(), getY(), getWidth(), getHeight());
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput narrationElementOutput) {
    }

    private boolean isOpen() {
        return openSupplier.getAsBoolean();
    }

    private boolean canSelectMode(RenderRuntime runtime, AntiAliasingMode mode) {
        if (mode == AntiAliasingMode.OFF) {
            return true;
        }

        return !improvedTransparencySupplier.getAsBoolean() && runtime.canSelectMode(mode);
    }

    private Component modeTooltip(RenderRuntime runtime, AntiAliasingMode mode) {
        if (mode != AntiAliasingMode.OFF && improvedTransparencySupplier.getAsBoolean()) {
            return Component.translatable(MODE_OPTION_IMPROVED_TRANSPARENCY_TOOLTIP_KEY, ClientText.label(mode));
        }

        if (!runtime.canSelectMode(mode)) {
            return Component.translatable(
                    MODE_OPTION_DISABLED_TOOLTIP_KEY,
                    ClientText.label(mode),
                    Component.literal(runtime.modeUnavailableReason(mode))
            );
        }

        return Component.translatable(
                runtime.activeMode() == mode ? MODE_OPTION_SELECTED_TOOLTIP_KEY : MODE_OPTION_TOOLTIP_KEY,
                ClientText.label(mode),
                ClientText.summary(mode)
        );
    }

    private void updateBounds(int guiWidth, int guiHeight, int modeCount) {
        int width = anchorButton.getWidth();
        int height = visibleRows(modeCount) * OPTION_HEIGHT + BORDER * 2;
        int x = clamp(anchorButton.getX(), 0, Math.max(0, guiWidth - width));
        int downwardY = anchorButton.getBottom() + GAP;
        int lowerLimit = Math.max(0, guiHeight - FOOTER_MARGIN);
        int y = downwardY + height > lowerLimit ? anchorButton.getY() - height - GAP : downwardY;
        y = clamp(y, 0, Math.max(0, guiHeight - height));
        setRectangle(width, height, x, y);
    }

    private int visibleRows(int modeCount) {
        return Math.min(VISIBLE_OPTIONS, Math.max(1, modeCount));
    }

    private int maxFirstVisibleIndex() {
        int modeCount = AntiAliasingMode.implementedModes().size();
        return Math.max(0, modeCount - visibleRows(modeCount));
    }

    private void scrollBy(int delta) {
        firstVisibleIndex = clamp(firstVisibleIndex + delta, 0, maxFirstVisibleIndex());
    }

    private void drawScrollbar(GuiGraphicsExtractor graphics, int modeCount, int visibleRows, int trackX, int trackY) {
        int trackHeight = visibleRows * OPTION_HEIGHT;
        graphics.fill(trackX, trackY, trackX + SCROLLBAR_WIDTH, trackY + trackHeight, SCROLLBAR_TRACK_COLOR);

        int maxFirst = Math.max(1, modeCount - visibleRows);
        int thumbHeight = Math.max(MIN_SCROLLBAR_THUMB_HEIGHT, trackHeight * visibleRows / modeCount);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int thumbY = trackY + travel * firstVisibleIndex / maxFirst;
        graphics.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbHeight, SCROLLBAR_THUMB_COLOR);
    }

    private boolean isScrollbarClick(double mouseX, double mouseY, int modeCount, int visibleRows) {
        if (modeCount <= visibleRows) {
            return false;
        }

        int scrollbarX = getRight() - BORDER - SCROLLBAR_WIDTH;
        int contentY = getY() + BORDER;
        return isInRect(mouseX, mouseY, scrollbarX, contentY, SCROLLBAR_WIDTH, visibleRows * OPTION_HEIGHT);
    }

    private void jumpToScrollbarPosition(double mouseY, int modeCount, int visibleRows) {
        int trackY = getY() + BORDER;
        int trackHeight = visibleRows * OPTION_HEIGHT;
        int thumbHeight = Math.max(MIN_SCROLLBAR_THUMB_HEIGHT, trackHeight * visibleRows / modeCount);
        int travel = Math.max(1, trackHeight - thumbHeight);
        int maxFirst = Math.max(0, modeCount - visibleRows);
        double ratio = (mouseY - trackY - thumbHeight / 2.0d) / travel;
        firstVisibleIndex = clamp((int) Math.round(ratio * maxFirst), 0, maxFirst);
    }

    private boolean isAnchorClick(double mouseX, double mouseY) {
        return isInRect(
                mouseX,
                mouseY,
                anchorButton.getX(),
                anchorButton.getY(),
                anchorButton.getWidth(),
                anchorButton.getHeight()
        );
    }

    private static Component trimmedLabel(Font font, Component label, int maxWidth) {
        if (font.width(label) <= maxWidth) {
            return label;
        }

        String ellipsis = "...";
        String text = font.plainSubstrByWidth(label.getString(), Math.max(0, maxWidth - font.width(ellipsis)));
        return Component.literal(text + ellipsis);
    }

    private static boolean isInRect(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
