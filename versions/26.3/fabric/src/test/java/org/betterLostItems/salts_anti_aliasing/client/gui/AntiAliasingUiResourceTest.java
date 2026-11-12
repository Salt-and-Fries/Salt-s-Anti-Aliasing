package org.betterLostItems.salts_anti_aliasing.client.gui;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.betterLostItems.salts_anti_aliasing.client.config.AntiAliasingMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class AntiAliasingUiResourceTest {
    private static final String LANGUAGE_RESOURCE =
            "/assets/salts_anti_aliasing/lang/en_us.json";

    @Test
    void definesLabelsAndSummariesForEveryImplementedMode() throws IOException {
        JsonObject translations = translations();

        for (AntiAliasingMode mode : AntiAliasingMode.implementedModes()) {
            assertHasKey(translations, mode.translationKey());
            assertHasKey(translations, mode.translationKey() + ".summary");
        }
    }

    @Test
    void definesEverySharedAntiAliasingControlTranslation() throws IOException {
        JsonObject translations = translations();
        for (String key : List.of(
                "options.salts_anti_aliasing.dropdown",
                "options.salts_anti_aliasing.dropdown.tooltip",
                "options.salts_anti_aliasing.mode.option.tooltip",
                "options.salts_anti_aliasing.mode.option.tooltip.selected",
                "options.salts_anti_aliasing.mode.option.tooltip.disabled",
                "options.salts_anti_aliasing.mode.option.tooltip.improved_transparency",
                "options.salts_anti_aliasing.sharpness",
                "options.salts_anti_aliasing.sharpness.tooltip",
                "options.salts_anti_aliasing.msaa_samples",
                "options.salts_anti_aliasing.msaa_samples.tooltip",
                "options.salts_anti_aliasing.msaa_alpha_to_coverage",
                "options.salts_anti_aliasing.msaa_alpha_to_coverage.tooltip",
                "options.salts_anti_aliasing.ssaa_scale",
                "options.salts_anti_aliasing.ssaa_scale.tooltip",
                "options.salts_anti_aliasing.ssaa_scale.warning_label",
                "options.salts_anti_aliasing.ssaa_scale.warning",
                "options.salts_anti_aliasing.upscale_quality",
                "options.salts_anti_aliasing.upscale_quality.tooltip",
                "options.salts_anti_aliasing.dlss_quality",
                "options.salts_anti_aliasing.dlss_quality.tooltip",
                "options.salts_anti_aliasing.fsr_quality",
                "options.salts_anti_aliasing.fsr_quality.tooltip"
        )) {
            assertHasKey(translations, key);
        }
    }

    @Test
    void keepsRemovedCombinedModesOutOfThePlayerFacingMenu() throws IOException {
        JsonObject translations = translations();

        assertFalse(translations.has("options.salts_anti_aliasing.mode.nis_sharpen"));
        assertFalse(translations.has("options.salts_anti_aliasing.mode.smaa_nis_sharpen"));
        assertFalse(translations.has("options.salts_anti_aliasing.mode.fsr1_rcas"));
    }

    private static JsonObject translations() throws IOException {
        try (InputStream stream = AntiAliasingUiResourceTest.class.getResourceAsStream(LANGUAGE_RESOURCE)) {
            assertNotNull(stream, "Missing " + LANGUAGE_RESOURCE);
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    private static void assertHasKey(JsonObject translations, String key) {
        assertTrue(translations.has(key), () -> "Missing translation key: " + key);
    }
}
