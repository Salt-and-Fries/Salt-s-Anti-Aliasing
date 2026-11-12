package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.narration.*;
import java.util.*;
/** Render the last hovered control's tooltip after the screen's complete widget pass. */
public final class TooltipRegistry {
    private static final Map<AbstractWidget, Tooltip> tips = new WeakHashMap<>();
    private static AbstractWidget hovered;
    private static boolean mouseCandidate;
    public static void set(AbstractWidget widget, Tooltip tooltip) { if (tooltip == null) tips.remove(widget); else tips.put(widget, tooltip); }
    public static void begin() { hovered = null; mouseCandidate = false; }
    public static void rendered(AbstractWidget widget, int mouseX, int mouseY) {
        if (!widget.visible || !tips.containsKey(widget)) return;
        boolean inside = mouseX >= widget.x && mouseY >= widget.y && mouseX < widget.x + widget.getWidth() && mouseY < widget.y + widget.getHeight();
        if (inside) { hovered = widget; mouseCandidate = true; }
        else if (hovered == null && widget.isFocused()) hovered = widget;
    }
    public static void render(Screen screen, PoseStack pose, int x, int y) {
        var tip = tips.get(hovered); if (tip != null) screen.renderTooltip(pose, tip.text(), mouseCandidate ? x : hovered.x + hovered.getWidth() / 2, mouseCandidate ? y : hovered.y + hovered.getHeight());
    }
    public static void narrate(AbstractWidget widget, NarrationElementOutput output) {
        var tip = tips.get(widget); if (tip != null) output.add(NarratedElementType.HINT, tip.text());
    }
}
