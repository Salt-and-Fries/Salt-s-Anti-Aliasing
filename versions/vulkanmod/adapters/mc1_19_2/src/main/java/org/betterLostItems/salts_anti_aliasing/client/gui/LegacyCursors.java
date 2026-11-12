package org.betterLostItems.salts_anti_aliasing.client.gui;

import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.*;

import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;
import java.util.*;

/** Older GuiGraphics has no cursor requests; retain the same hover/resize feedback through GLFW. */
public final class LegacyCursors {
    private static final Map<Integer, Long> cursors = new HashMap<>();
    private static int current = -1;
    private LegacyCursors() {}
    public static void reset() { request(CursorTypes.ARROW); }
    public static void request(int shape) {
        if (current == shape) return;
        long cursor = cursors.computeIfAbsent(shape, GLFW::glfwCreateStandardCursor);
        if (cursor != 0) {
            GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), cursor);
            current = shape;
        }
    }
    public static void close() {
        for (long cursor : cursors.values()) if (cursor != 0) GLFW.glfwDestroyCursor(cursor);
        cursors.clear(); current = -1;
    }
}
final class CursorTypes {
    static final int ARROW = GLFW.GLFW_ARROW_CURSOR;
    static final int RESIZE_NS = GLFW.GLFW_VRESIZE_CURSOR;
    static final int POINTING_HAND = GLFW.GLFW_HAND_CURSOR;
    static final int NOT_ALLOWED = GLFW.GLFW_ARROW_CURSOR;
    private CursorTypes() {}
}
