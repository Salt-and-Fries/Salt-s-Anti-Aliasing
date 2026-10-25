package org.betterLostItems.salts_anti_aliasing.client.config;

import static org.junit.jupiter.api.Assertions.*;

import com.google.gson.Gson;

import org.junit.jupiter.api.Test;

final class RenderTuningTest {
    @Test
    void resetRestoresExactDefaultsEvenWhenTheyAreBetweenSliderSteps() {
        var tuning = new RenderTuning();
        var defaults = new RenderTuning();
        tuning.fxaaEdgeThreshold = .3f;
        tuning.fxaaMinContrast = .1f;
        for (var setting : NumericTuningSetting.forMode(AntiAliasingMode.FXAA, true))
            setting.reset(tuning, defaults);
        assertEquals(.125f, tuning.fxaaEdgeThreshold);
        assertEquals(.0312f, tuning.fxaaMinContrast);
    }

    @Test
    void upgradingVersionTwoPreservesExplicitTwentyFivePercentSharpening() {
        var c =
                new Gson()
                        .fromJson(
                                "{\"configVersion\":2,\"mode\":\"FXAA\",\"sharpenStrength\":0.25}",
                                AntiAliasingConfig.class);
        c.sanitize();
        assertEquals(0.25f, c.sharpenStrength);
        assertEquals(3, c.configVersion);
        assertEquals(RenderTuning.SpatialAa.SMAA, c.tuning.spatialAa);
    }

    @Test
    void corruptTuningCannotReachShaderOrAllocationControls() {
        var c =
                new Gson()
                        .fromJson(
                                """
                {"configVersion":3,"tuning":{"taaHistoryWeight":14,"taaDepthRejection":-1,
                "taaJitterPhases":900,"spatialAa":"unknown","smaaQuality":null,"reactiveMaximum":3}}
                """,
                                AntiAliasingConfig.class);
        c.tuning.fxaaSubpixel = Float.NaN;
        c.tuning.sharpenLimit = Float.POSITIVE_INFINITY;
        c.sanitize();
        assertEquals(.98f, c.tuning.taaHistoryWeight);
        assertEquals(.1f, c.tuning.taaDepthRejection);
        assertEquals(32, c.tuning.taaJitterPhases);
        assertEquals(.5f, c.tuning.fxaaSubpixel);
        assertEquals(.03f, c.tuning.sharpenLimit);
        assertEquals(1, c.tuning.reactiveMaximum);
        assertEquals(RenderTuning.SpatialAa.SMAA, c.tuning.spatialAa);
        assertEquals(RenderTuning.SmaaQuality.ULTRA, c.tuning.smaaQuality);
    }

    @Test
    void editingASnapshotNeverChangesTheLiveTuning() {
        var original = new AntiAliasingConfig();
        original.sanitize();
        var copy = original.copy();
        copy.tuning.taaHistoryWeight = .1f;
        copy.tuning.ssaaLinearLight = false;
        assertEquals(.9f, original.tuning.taaHistoryWeight);
        assertTrue(original.tuning.ssaaLinearLight);
    }

    @Test
    void currentSettingsRoundTripWithoutLosingMethodChoices() {
        var c = new AntiAliasingConfig();
        c.sanitize();
        c.tuning.smaaQuality = RenderTuning.SmaaQuality.HIGH;
        c.tuning.ssaaFilter = RenderTuning.SsaaFilter.TENT;
        c.tuning.taaHistoryFilter = RenderTuning.HistoryFilter.BILINEAR;
        c.tuning.entityMotion = false;
        c.dlssQualityPreset = DlssQualityPreset.DLAA;
        c.tuning.dlssModel = DlssModelPreset.L;
        var gson = new Gson();
        var saved = gson.fromJson(gson.toJson(c), AntiAliasingConfig.class);
        saved.sanitize();
        assertEquals(c.tuning.smaaQuality, saved.tuning.smaaQuality);
        assertEquals(c.tuning.ssaaFilter, saved.tuning.ssaaFilter);
        assertEquals(c.tuning.taaHistoryFilter, saved.tuning.taaHistoryFilter);
        assertFalse(saved.tuning.entityMotion);
        assertEquals(DlssQualityPreset.DLAA, saved.dlssQualityPreset);
        assertEquals(DlssModelPreset.L, saved.tuning.dlssModel);
    }

    @Test
    void missingTuningFromAnExistingConfigUsesSafeDefaults() {
        var c =
                new Gson()
                        .fromJson(
                                "{\"configVersion\":2,\"mode\":\"SSAA\",\"tuning\":null}",
                                AntiAliasingConfig.class);
        c.sanitize();
        assertTrue(c.tuning.ssaaLinearLight);
        assertEquals(RenderTuning.SsaaFilter.AREA, c.tuning.ssaaFilter);
        assertTrue(c.tuning.entityMotion);
        assertEquals(DlssModelPreset.AUTO, c.tuning.dlssModel);
    }

    @Test
    void unknownDlssModelFallsBackToRuntimeDefaultWithoutChangingQuality() {
        var c = new Gson().fromJson(
                "{\"configVersion\":3,\"dlssQualityPreset\":\"DLAA\",\"tuning\":{\"dlssModel\":\"future\"}}",
                AntiAliasingConfig.class);
        c.sanitize();
        assertEquals(DlssModelPreset.AUTO, c.tuning.dlssModel);
        assertEquals(DlssQualityPreset.DLAA, c.dlssQualityPreset);
        assertEquals(1.0f, c.sceneRenderScale());
    }

    @Test void dlssRetiresLegacyReactiveControlsAndBoundsParticleCoverage() {
        var c = new AntiAliasingConfig();
        c.mode = AntiAliasingMode.DLSS_SUPER_RESOLUTION;
        c.tuning.debugView = RenderTuning.DebugView.REACTIVE_MASK;
        c.tuning.dlssParticleAlphaThreshold = Float.NaN;
        c.tuning.motionVertexBudget = null;
        c.sanitize();
        assertEquals(RenderTuning.DebugView.NONE, c.tuning.debugView);
        assertEquals(.5f, c.tuning.dlssParticleAlphaThreshold);
        assertEquals(RenderTuning.MotionVertexBudget.VERTICES_1M, c.tuning.motionVertexBudget);
        var settings = NumericTuningSetting.forMode(c.mode, true);
        assertFalse(settings.contains(NumericTuningSetting.REACTIVE_STRENGTH));
        assertFalse(settings.contains(NumericTuningSetting.REACTIVE_THRESHOLD));
        assertFalse(settings.contains(NumericTuningSetting.REACTIVE_MAXIMUM));
        assertTrue(settings.contains(NumericTuningSetting.DLSS_PARTICLE_ALPHA_THRESHOLD));
    }
}
