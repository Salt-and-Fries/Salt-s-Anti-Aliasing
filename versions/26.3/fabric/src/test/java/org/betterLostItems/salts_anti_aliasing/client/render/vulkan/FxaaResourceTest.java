package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class FxaaResourceTest {
    private static final Pattern SEARCH_STEPS = Pattern.compile(
            "FXAA_SEARCH_STEPS\\[FXAA_SEARCH_STEP_COUNT]\\s*=\\s*float\\[]\\((.*?)\\);",
            Pattern.DOTALL
    );
    private static final String EFFECT_RESOURCE =
            "/assets/salts_anti_aliasing/post_effect/fxaa.json";
    private static final String SHADER_RESOURCE =
            "/assets/salts_anti_aliasing/shaders/post/fxaa.fsh";

    @Test
    void effectUsesReferenceQualityDefaultsWithoutDepthBlur() throws IOException {
        String effect = readResource(EFFECT_RESOURCE);

        assertTrue(effect.contains("\"name\": \"SubpixelBlend\""));
        assertTrue(effect.contains("\"value\": 0.75"));
        assertTrue(effect.contains("\"name\": \"EdgeThreshold\""));
        assertTrue(effect.contains("\"value\": 0.125"));
        assertTrue(effect.contains("\"name\": \"EdgeThresholdMin\""));
        assertTrue(effect.contains("\"value\": 0.0312"));
        assertFalse(effect.contains("\"sampler_name\": \"Depth\""));
        assertFalse(effect.contains("SearchRadius"));
        assertFalse(effect.contains("DepthWeight"));
    }

    @Test
    void shaderUsesFxaaQualityEndpointSearch() throws IOException {
        String shader = readResource(SHADER_RESOURCE);

        assertTrue(shader.contains("Copyright (c) 2014-2015, NVIDIA CORPORATION"));
        assertTrue(shader.contains("Redistribution and use in source and binary forms"));
        assertTrue(shader.contains("FXAA_SEARCH_STEP_COUNT = 11"));
        assertTrue(shader.contains("1.0, 1.5"));
        assertTrue(shader.contains("4.0, 8.0"));
        assertTrue(shader.contains("i < FXAA_SEARCH_STEP_COUNT - 1"));
        assertTrue(shader.contains("edgeHorz"));
        assertTrue(shader.contains("edgeVert"));
        assertTrue(shader.contains("pairAverage"));
        assertTrue(shader.contains("gradientThreshold = gradient * 0.25"));
        assertTrue(shader.contains("pixelOffset = max(edgeOffset, subpixelOffset)"));
        assertTrue(shader.contains("vec4(resolved.rgb, colorM.a)"));
        assertFalse(shader.contains("DepthSampler"));
        assertFalse(shader.contains("directionalBlend"));
    }

    @Test
    void presetTwentyEightStopsSamplingBeforeItsFinalFallbackAdvance() throws IOException {
        String shader = readResource(SHADER_RESOURCE);
        Matcher matcher = SEARCH_STEPS.matcher(shader);
        assertTrue(matcher.find(), "Missing FXAA preset-28 search schedule");

        double[] steps = Arrays.stream(matcher.group(1).split(","))
                .map(String::trim)
                .mapToDouble(Double::parseDouble)
                .toArray();
        assertEquals(11, steps.length);

        double lastSampledDistance = Arrays.stream(steps, 0, steps.length - 1).sum();
        double finalUnresolvedDistance = lastSampledDistance + steps[steps.length - 1];
        assertEquals(20.5d, lastSampledDistance);
        assertEquals(28.5d, finalUnresolvedDistance);
        assertTrue(shader.contains("i < FXAA_SEARCH_STEP_COUNT - 1"));
    }

    private static String readResource(String path) throws IOException {
        try (InputStream input = FxaaResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(input, () -> "Missing test resource: " + path);
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
