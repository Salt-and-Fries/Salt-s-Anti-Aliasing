package org.betterLostItems.salts_anti_aliasing.client.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class DropdownScrollbarTest {
    private static final int TRACK_Y = 20;
    private static final DropdownScrollbar.Metrics METRICS =
            DropdownScrollbar.metrics(10, 5, 100, 10);

    @Test
    void calculatesProportionalThumbGeometry() {
        assertEquals(5, METRICS.maxFirstIndex());
        assertEquals(50, METRICS.thumbHeight());
        assertEquals(50, METRICS.travel());
        assertEquals(40, DropdownScrollbar.thumbTop(TRACK_Y, 2, METRICS));
    }

    @Test
    void preservesTheGrabPointWhenClickingTheThumb() {
        assertEquals(7.0d, DropdownScrollbar.grabOffset(47.0d, TRACK_Y, 2, METRICS));
    }

    @Test
    void centersTheThumbWhenClickingTheTrack() {
        assertEquals(25.0d, DropdownScrollbar.grabOffset(95.0d, TRACK_Y, 2, METRICS));
    }

    @Test
    void mapsDraggingAcrossTheWholeTrackAndClampsPastItsEnds() {
        double centeredGrabOffset = METRICS.thumbHeight() / 2.0d;

        assertEquals(0, DropdownScrollbar.indexForDrag(-100.0d, centeredGrabOffset, TRACK_Y, METRICS));
        assertEquals(0, DropdownScrollbar.indexForDrag(45.0d, centeredGrabOffset, TRACK_Y, METRICS));
        assertEquals(3, DropdownScrollbar.indexForDrag(70.0d, centeredGrabOffset, TRACK_Y, METRICS));
        assertEquals(5, DropdownScrollbar.indexForDrag(95.0d, centeredGrabOffset, TRACK_Y, METRICS));
        assertEquals(5, DropdownScrollbar.indexForDrag(1_000.0d, centeredGrabOffset, TRACK_Y, METRICS));
    }

    @Test
    void staysAtTheFirstItemWhenAllItemsFit() {
        DropdownScrollbar.Metrics metrics = DropdownScrollbar.metrics(4, 5, 100, 10);

        assertEquals(0, metrics.maxFirstIndex());
        assertEquals(100, metrics.thumbHeight());
        assertEquals(0, metrics.travel());
        assertEquals(0, DropdownScrollbar.indexForDrag(1_000.0d, 50.0d, TRACK_Y, metrics));
    }
}
