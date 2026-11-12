package org.betterLostItems.salts_anti_aliasing.client.gui;

import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.*;

import java.util.List;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.vertex.PoseStack;
import org.betterLostItems.salts_anti_aliasing.client.gui.LegacyGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;

/** Vanilla-compatible scrolling, keyboard focus and narration for arbitrary settings controls. */
final class WidgetOptionsList extends ContainerObjectSelectionList<WidgetOptionsList.Row> {
    WidgetOptionsList(Minecraft client, int width, int height, int top) {
        super(client, width, height + 64, top, top + height, 25);
        centerListVertically = false;
    }
    @Override public int getRowWidth() { return Math.min(310, Math.max(100, width - 32)); }
    @Override protected int getScrollbarPosition() { return getRowLeft() + getRowWidth() + 6; }
    void addSmall(AbstractWidget first, AbstractWidget second) {
        addEntry(new Row(second == null ? List.of(first) : List.of(first, second)));
    }
    final class Row extends ContainerObjectSelectionList.Entry<Row> {
        private final List<AbstractWidget> controls;
        Row(List<AbstractWidget> controls) { this.controls = controls; }
        @Override public void render(PoseStack poseStack, int index, int top, int left, int rowWidth, int height,
                                     int mouseX, int mouseY, boolean hovered, float tick) {
        var graphics = new LegacyGraphics(poseStack);
            int x = getRowLeft();
            int width = controls.size() == 1 ? getRowWidth() : (getRowWidth() - 10) / 2;
            for (var control : controls) {
                control.x = x; control.y = top; control.setWidth(width);
                control.render(graphics.pose(), mouseX, mouseY, tick);
                x += width + 10;
            }
        }
        @Override public List<? extends GuiEventListener> children() { return controls; }
        @Override public List<? extends NarratableEntry> narratables() { return controls; }
    }
}
