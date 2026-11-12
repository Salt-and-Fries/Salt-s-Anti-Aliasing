package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.joml.Quaternionf;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class ParticleMotionQuadTest {
    @Test void identityBillboardKeepsVanillaCornerAndWindingOrder() {
        assertArrayEquals(new float[]{3, 1, 4, 3, 5, 4, -1, 5, 4, -1, 1, 4},
                ParticleMotionQuad.positions(1, 3, 4, new Quaternionf(), 2), .00001f);
    }
    @Test void rollAndSizeChangesMoveCornersWithoutMovingTheParticleCenter() {
        float[] old = ParticleMotionQuad.positions(10, 20, 30, new Quaternionf(), 1);
        float[] now = ParticleMotionQuad.positions(10, 20, 30, new Quaternionf().rotationZ((float)Math.PI/2), 2);
        assertEquals(12, now[0], .00001f);
        assertEquals(22, now[1], .00001f);
        assertEquals(10, (now[0]+now[3]+now[6]+now[9])/4, .00001f);
        assertEquals(20, (now[1]+now[4]+now[7]+now[10])/4, .00001f);
        assertEquals(11, old[0]);
        assertNotEquals(old[0], now[0]);
    }
}
