package org.betterLostItems.salts_anti_aliasing.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import java.util.List;

/** Immediate PoseStack drawing with the same coordinates and scissor behavior as the newer UI. */
public final class LegacyGraphics {
    private final PoseStack pose;
    public LegacyGraphics(PoseStack pose) { this.pose = pose; }
    public PoseStack pose() { return pose; }
    public int guiWidth() { return Minecraft.getInstance().getWindow().getGuiScaledWidth(); }
    public int guiHeight() { return Minecraft.getInstance().getWindow().getGuiScaledHeight(); }
    public void fill(int x0, int y0, int x1, int y1, int color) { GuiComponent.fill(pose, x0, y0, x1, y1, color); }
    public void renderOutline(int x, int y, int width, int height, int color) { GuiComponent.renderOutline(pose, x, y, width, height, color); }
    public void enableScissor(int x0, int y0, int x1, int y1) { GuiComponent.enableScissor(x0, y0, x1, y1); }
    public void disableScissor() { GuiComponent.disableScissor(); }
    public void drawCenteredString(Font font, Component text, int x, int y, int color) { GuiComponent.drawCenteredString(pose, font, text, x, y, color); }
    public void drawCenteredString(Font font, String text, int x, int y, int color) { GuiComponent.drawCenteredString(pose, font, text, x, y, color); }
    public void drawString(Font font, String text, int x, int y, int color, boolean shadow) {
        if (shadow) font.drawShadow(pose, text, x, y, color); else font.draw(pose, text, x, y, color);
    }
    public void drawString(Font font, Component text, int x, int y, int color, boolean shadow) {
        if (shadow) font.drawShadow(pose, text, x, y, color); else font.draw(pose, text, x, y, color);
    }
    public void renderTooltip(Font font, List<FormattedCharSequence> lines, int x, int y) {
        var screen = Minecraft.getInstance().screen;
        if (screen != null) screen.renderTooltip(pose, lines, x, y);
    }
    public void flush() { /* The old GUI submits each draw immediately. */ }
}
