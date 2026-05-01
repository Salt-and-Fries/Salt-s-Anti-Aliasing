package org.betterLostItems.salts_anti_aliasing.client.debug;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import org.betterLostItems.salts_anti_aliasing.client.SaltsAntiAliasingClient;
import org.betterLostItems.salts_anti_aliasing.client.render.common.RenderRuntime;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Documents edge debug hud behavior for Salt's Anti Aliasing. Debug instrumentation for visualizing
 * and measuring aliasing behavior.
 */
public final class EdgeDebugHud {
    private static final int BOX_PADDING = 6;
    private static final int RIGHT_MARGIN = 8;
    private static final int BOTTOM_MARGIN = 52;
    private static final int BACKGROUND_COLOR = 0x90000000;
    private static final int TITLE_COLOR = 0xFFFFFFFF;
    private static final int TEXT_COLOR = 0xFFD0D0D0;

    /**
     * Creates a edge debug hud with the collaborators or initial state supplied by the caller.
     */
    private EdgeDebugHud() {
    }

    /**
     * Coordinates render within the anti-aliasing render, configuration, or compatibility flow.
     * @param graphics graphics supplied by Minecraft or the caller
     * @param deltaTracker Minecraft frame delta supplied by the render callback
     */
    public static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        RenderRuntime runtime = SaltsAntiAliasingClient.runtimeOrNull();
        Minecraft minecraft = Minecraft.getInstance();
        if (runtime == null || !runtime.debugViewsEnabled() || minecraft.level == null) {
            return;
        }

        Font font = minecraft.font;
        EdgeDebugStats stats = runtime.edgeDebugStats();
        List<String> lines = new ArrayList<>();
        lines.add("Edge Debug");
        lines.add("Mode: " + stats.mode().displayName());
        if (stats.available()) {
            lines.add("Quality: " + stats.qualityScore() + "/100 (" + stats.verdict() + ")");
            lines.add("Coverage: " + percent(stats.edgeCoverage()));
            lines.add("Harsh: " + percent(stats.harshEdgeRatio()));
            lines.add("Smooth: " + percent(stats.smoothTransitionRatio()));
        } else {
            lines.add("Analyzing...");
        }

        int lineHeight = font.lineHeight + 2;
        int maxWidth = 0;
        for (String line : lines) {
            maxWidth = Math.max(maxWidth, font.width(line));
        }

        int boxWidth = maxWidth + BOX_PADDING * 2;
        int boxHeight = lines.size() * lineHeight + BOX_PADDING * 2 - 2;
        int x = graphics.guiWidth() - boxWidth - RIGHT_MARGIN;
        int y = graphics.guiHeight() - boxHeight - BOTTOM_MARGIN;
        graphics.fill(x, y, x + boxWidth, y + boxHeight, BACKGROUND_COLOR);

        for (int i = 0; i < lines.size(); i++) {
            int color = i == 0 ? TITLE_COLOR : TEXT_COLOR;
            graphics.drawString(font, lines.get(i), x + BOX_PADDING, y + BOX_PADDING + i * lineHeight, color, true);
        }
    }

    /**
     * Coordinates percent within the anti-aliasing render, configuration, or compatibility flow.
     * @param value value being transformed or clamped
     * @return percent value produced or selected by this code path
     */
    private static String percent(float value) {
        return String.format(Locale.US, "%.1f%%", value * 100.0f);
    }
}
