package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;

import java.util.concurrent.ConcurrentHashMap;

/** Actual pass results, deliberately separate from the user's persisted requested mode. */
public final class RenderingHealth {
    private record Result(boolean success, String message, int width, int height) {}

    private static final ConcurrentHashMap<AntiAliasingMode, Result> RESULTS =
            new ConcurrentHashMap<>();

    private RenderingHealth() {}

    public static void success(AntiAliasingMode mode, int width, int height) {
        Result previous = RESULTS.get(mode);
        if (previous != null
                && previous.success()
                && previous.width() == width
                && previous.height() == height) return;
        RESULTS.put(mode, new Result(true, "Rendering", width, height));
    }

    public static void failure(AntiAliasingMode mode, String message) {
        RESULTS.put(mode, new Result(false, message, 0, 0));
    }

    public static void clear() {
        RESULTS.clear();
    }

    public static String describe(AntiAliasingMode mode) {
        if (mode == AntiAliasingMode.OFF) return "Off - native scene";
        Result r = RESULTS.get(mode);
        if (r == null) return mode.displayName() + " - preparing";
        return mode.displayName()
                + " - "
                + (r.success() ? r.width() + " x " + r.height() : "fallback: " + r.message());
    }
}
