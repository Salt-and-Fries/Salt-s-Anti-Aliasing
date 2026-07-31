package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingConfig;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackend;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderBackendType;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderCapability;
import org.betterLostItems.salts_anti_aliasing.client.render.api.RenderTargetDescriptor;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class RenderPipelinePlannerTest {
    private static final RenderBackend BACKEND = new FullCapabilityBackend();
    private final RenderPipelinePlanner planner = new RenderPipelinePlanner();

    @Test
    void omitsOptionalSharpeningAtZero() {
        PipelinePlan plan = planner.plan(BACKEND, config(AntiAliasingMode.FXAA, 0.0f));

        assertFalse(passIds(plan).contains("nis_sharpen"));
    }

    @Test
    void appendsOneFinalSharpenPassForNonFsrModes() {
        PipelinePlan plan = planner.plan(BACKEND, config(AntiAliasingMode.SMAA, 0.5f));
        List<String> passIds = passIds(plan);

        assertEquals(1, passIds.stream().filter("nis_sharpen"::equals).count());
        assertEquals("nis_sharpen", passIds.getLast());
    }

    @Test
    void keepsFsrSharpeningInsideTheNativeUpscaler() {
        PipelinePlan plan = planner.plan(BACKEND, config(AntiAliasingMode.FSR3_SUPER_RESOLUTION, 0.5f));
        List<String> passIds = passIds(plan);

        assertTrue(passIds.contains("fsr_super_resolution"));
        assertFalse(passIds.contains("nis_sharpen"));
    }

    private static AntiAliasingConfig config(AntiAliasingMode mode, float sharpenStrength) {
        AntiAliasingConfig config = new AntiAliasingConfig();
        config.sanitize();
        config.mode = mode;
        config.sharpenStrength = sharpenStrength;
        config.sanitize();
        return config;
    }

    private static List<String> passIds(PipelinePlan plan) {
        return plan.passes().stream().map(pass -> pass.id()).toList();
    }

    private static final class FullCapabilityBackend implements RenderBackend {
        @Override
        public RenderBackendType type() {
            return RenderBackendType.VULKAN;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public Set<RenderCapability> capabilities() {
            return EnumSet.allOf(RenderCapability.class);
        }

        @Override
        public void declareTargets(Collection<RenderTargetDescriptor> targets) {
        }

        @Override
        public List<RenderTargetDescriptor> declaredTargets() {
            return List.of();
        }
    }
}
