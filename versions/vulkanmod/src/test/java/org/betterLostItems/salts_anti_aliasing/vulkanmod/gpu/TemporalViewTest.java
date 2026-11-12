package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemporalViewTest {
    private final Object world = new Object(), camera = new Object();
    private final AntiAliasingConfig config = new AntiAliasingConfig();
    private final Matrix4f projection = new Matrix4f().setPerspective((float) Math.toRadians(70), 4f / 3, .05f, 256, true);
    private final TemporalView view = new TemporalView();

    TemporalViewTest() { config.mode = AntiAliasingMode.TAA; }

    private void prepare() { view.prepare(world, camera, camera, config, 800, 600, 800); }
    private void capture(double x) { view.capture(projection, new Matrix4f(), x, 0, 0, 0, 0, (float) Math.toRadians(70)); }

    @Test void matchesTheActualNegativeViewportAndForwardDepthConvention() {
        prepare(); capture(0);
        var near = view.current().transformProject(new Vector3f(0, 0, -.05f));
        var far = view.current().transformProject(new Vector3f(0, 0, -256));
        assertEquals(1, near.z, 0.00001);
        assertEquals(0, far.z, 0.00001);
        assertTrue(view.current().transformProject(new Vector3f(0, 1, -5)).y < 0,
                "World-up must project toward the top of a positive-height fullscreen viewport");
    }

    @Test void subtractsLargeWorldPositionsBeforeConvertingToFloat() {
        prepare(); capture(30_000_000); view.resolved();
        prepare(); capture(30_000_000.01);
        assertFalse(view.resetHistory());
        var current = view.current().transformProject(new Vector3f(0, 0, -5));
        var previous = view.previous().transformProject(new Vector3f(0, 0, -5));
        assertEquals(projection.m00() * .01 / 5, previous.x - current.x, 0.000001);
    }

    @Test void discardsHistoryAfterACameraCutAndWritesTheRealStd140Layout() {
        prepare(); capture(0); view.resolved();
        prepare(); capture(8);
        assertTrue(view.resetHistory());
        var uniforms = view.taaUniforms(config);
        assertEquals(208, uniforms.remaining());
        assertEquals(0, uniforms.getFloat(128));
        assertEquals(config.tuning.taaHistoryFilter.ordinal(), uniforms.getFloat(192));
        assertEquals(224, view.motionUniforms().remaining());
    }

    @Test void maintainsHistoryOnlyForTheSameWorldCameraModeAndResolution() {
        prepare(); capture(0); view.resolved();
        prepare(); capture(0);
        assertFalse(view.resetHistory());
        view.prepare(new Object(), camera, camera, config, 800, 600, 800); capture(0);
        assertTrue(view.resetHistory());
    }
}
