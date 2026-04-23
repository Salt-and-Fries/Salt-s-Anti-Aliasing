package org.betterLostItems.salts_anti_aliasing.client.render.api;

public record HistoryState(boolean valid, int accumulatedFrames) {
    public static HistoryState invalid() {
        return new HistoryState(false, 0);
    }
}
