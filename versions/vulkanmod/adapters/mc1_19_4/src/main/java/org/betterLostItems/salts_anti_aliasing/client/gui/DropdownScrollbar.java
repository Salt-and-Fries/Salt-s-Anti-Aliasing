package org.betterLostItems.salts_anti_aliasing.client.gui;

/**
 * Pure scrollbar geometry shared by the anti-aliasing mode popup and its regression tests.
 */
final class DropdownScrollbar {
    private DropdownScrollbar() {
    }

    static Metrics metrics(int itemCount, int visibleItemCount, int trackHeight, int minimumThumbHeight) {
        int safeTrackHeight = Math.max(0, trackHeight);
        int safeItemCount = Math.max(1, itemCount);
        int safeVisibleItemCount = Math.min(safeItemCount, Math.max(1, visibleItemCount));
        int maxFirstIndex = Math.max(0, safeItemCount - safeVisibleItemCount);
        int proportionalThumbHeight = safeTrackHeight * safeVisibleItemCount / safeItemCount;
        int thumbHeight = Math.min(
                safeTrackHeight,
                Math.max(Math.max(0, minimumThumbHeight), proportionalThumbHeight)
        );
        return new Metrics(maxFirstIndex, thumbHeight, Math.max(0, safeTrackHeight - thumbHeight));
    }

    static int thumbTop(int trackY, int firstVisibleIndex, Metrics metrics) {
        if (metrics.maxFirstIndex() == 0 || metrics.travel() == 0) {
            return trackY;
        }

        int clampedIndex = clamp(firstVisibleIndex, 0, metrics.maxFirstIndex());
        return trackY + metrics.travel() * clampedIndex / metrics.maxFirstIndex();
    }

    static double grabOffset(double mouseY, int trackY, int firstVisibleIndex, Metrics metrics) {
        int thumbTop = thumbTop(trackY, firstVisibleIndex, metrics);
        if (mouseY >= thumbTop && mouseY < thumbTop + metrics.thumbHeight()) {
            return mouseY - thumbTop;
        }

        return metrics.thumbHeight() / 2.0d;
    }

    static int indexForDrag(double mouseY, double grabOffset, int trackY, Metrics metrics) {
        if (metrics.maxFirstIndex() == 0 || metrics.travel() == 0) {
            return 0;
        }

        double ratio = (mouseY - trackY - grabOffset) / metrics.travel();
        return clamp((int) Math.round(ratio * metrics.maxFirstIndex()), 0, metrics.maxFirstIndex());
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    record Metrics(int maxFirstIndex, int thumbHeight, int travel) {
    }
}
