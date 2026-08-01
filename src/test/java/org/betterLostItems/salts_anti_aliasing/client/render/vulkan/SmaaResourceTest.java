package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

final class SmaaResourceTest {
    private static final String RESOURCE_ROOT = "/assets/salts_anti_aliasing/";
    private static final String PINNED_REFERENCE_COMMIT = "71c806a838bdd7d517df19192a20f0c61b3ca29d";
    private static final String NORMALIZED_REFERENCE_SHADER_SHA256 =
            "3fc01a11882f634d1b7d0cb08de4376c993b1360a367d6148ba6827c14afdcd3";

    @Test
    void referenceShaderContainsTheCompleteSmaaOneXPipeline() throws IOException {
        byte[] shaderBytes = readResourceBytes(RESOURCE_ROOT + "shaders/include/smaa.glsl");
        String shader = new String(shaderBytes, StandardCharsets.UTF_8);

        assertEquals(
                NORMALIZED_REFERENCE_SHADER_SHA256,
                sha256(shader.replace("\r\n", "\n").getBytes(StandardCharsets.UTF_8))
        );
        assertTrue(shader.contains(PINNED_REFERENCE_COMMIT));
        assertTrue(shader.contains("SMAAColorEdgeDetectionPS"));
        assertTrue(shader.contains("SMAACalculateDiagWeights"));
        assertTrue(shader.contains("SMAASearchXLeft"));
        assertTrue(shader.contains("SMAASearchXRight"));
        assertTrue(shader.contains("SMAASearchYUp"));
        assertTrue(shader.contains("SMAASearchYDown"));
        assertTrue(shader.contains("SMAADetectHorizontalCornerPattern"));
        assertTrue(shader.contains("SMAADetectVerticalCornerPattern"));
        assertTrue(shader.contains("SMAANeighborhoodBlendingPS"));
    }

    @Test
    void edgeDetectionWritesZeroInsteadOfDependingOnTargetClears() throws IOException {
        String shader = readUtf8(RESOURCE_ROOT + "shaders/include/smaa.glsl");

        assertFalse(shader.contains("discard;"));
        assertEquals(3, occurrences(shader, "return float2(0.0, 0.0);"));
    }

    @Test
    void passWrappersUseTheOfficialUltraContracts() throws IOException {
        String edge = readUtf8(RESOURCE_ROOT + "shaders/post/smaa_edge_detect.fsh");
        String weights = readUtf8(RESOURCE_ROOT + "shaders/post/smaa_weight_blend.fsh");
        String neighborhood = readUtf8(RESOURCE_ROOT + "shaders/post/smaa_neighborhood_blend.fsh");

        assertTrue(edge.contains("#define SMAA_PRESET_ULTRA 1"));
        assertTrue(edge.contains("SMAAColorEdgeDetectionPS"));
        assertTrue(weights.contains("uniform sampler2D AreaSampler;"));
        assertTrue(weights.contains("uniform sampler2D SearchSampler;"));
        assertTrue(weights.contains("SMAABlendingWeightCalculationPS"));
        assertTrue(weights.contains("vec4(0.0)"));
        assertTrue(neighborhood.contains("SMAANeighborhoodBlendingPS"));
        assertFalse(weights.contains("SearchStrength"));
        assertFalse(weights.contains("MaxBlend"));
    }

    @Test
    void effectBindsReferenceLookupsWithRequiredFiltering() throws IOException {
        String effect = readUtf8(RESOURCE_ROOT + "post_effect/smaa.json").replaceAll("\\s+", "");

        assertTrue(effect.contains(
                "{\"sampler_name\":\"Area\",\"location\":\"salts_anti_aliasing:smaa_area\"," +
                        "\"width\":160,\"height\":560,\"bilinear\":true}"
        ));
        assertTrue(effect.contains(
                "{\"sampler_name\":\"Search\",\"location\":\"salts_anti_aliasing:smaa_search\"," +
                        "\"width\":64,\"height\":16,\"bilinear\":true}"
        ));
        assertTrue(effect.contains(
                "{\"sampler_name\":\"Edges\",\"target\":\"edges\",\"bilinear\":true}"
        ));
        assertTrue(effect.contains(
                "{\"sampler_name\":\"Color\",\"target\":\"minecraft:main\",\"bilinear\":true}"
        ));
        assertTrue(effect.contains(
                "{\"sampler_name\":\"Weights\",\"target\":\"weights\",\"bilinear\":true}"
        ));
        assertFalse(effect.contains("SmaaEdgeConfig"));
        assertFalse(effect.contains("SmaaWeightConfig"));
    }

    @Test
    void areaTextureIsALosslessCopyOfThePinnedRgData() throws IOException {
        assertLookupTexture(
                "textures/effect/smaa_area.png",
                160,
                560,
                2,
                "8b0e5942a0bc8e6e77f196dc4b73ecf8e0fe7b572398f663e4c0a53038b4ee5a",
                "35065cef2a02cabcad711d6bf430239ae64e27d71c4e4fa06f29cce2c992f0d2"
        );
    }

    @Test
    void searchTextureIsALosslessCopyOfThePinnedRData() throws IOException {
        assertLookupTexture(
                "textures/effect/smaa_search.png",
                64,
                16,
                1,
                "8a84b0197ce1ca827ac59f7370ac16abea8627a203b4ca2e76f3ec07c6cfdf67",
                "3694eae5e9d44b8ebb4415a13f8c7b94dc08a2fc86658434d771c4610fe5744d"
        );
    }

    private static void assertLookupTexture(
            String relativePath,
            int expectedWidth,
            int expectedHeight,
            int sourceChannels,
            String expectedPngHash,
            String expectedSourceHash
    ) throws IOException {
        byte[] pngBytes = readResourceBytes(RESOURCE_ROOT + relativePath);
        assertEquals(expectedPngHash, sha256(pngBytes));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(pngBytes));
        assertNotNull(image, () -> "Unreadable lookup texture: " + relativePath);
        assertEquals(expectedWidth, image.getWidth());
        assertEquals(expectedHeight, image.getHeight());

        ByteArrayOutputStream sourceBytes = new ByteArrayOutputStream(
                expectedWidth * expectedHeight * sourceChannels
        );
        for (int y = 0; y < expectedHeight; y++) {
            for (int x = 0; x < expectedWidth; x++) {
                int argb = image.getRGB(x, y);
                int red = argb >>> 16 & 0xff;
                int green = argb >>> 8 & 0xff;
                int blue = argb & 0xff;
                int alpha = argb >>> 24 & 0xff;
                if (blue != 0 || alpha != 255 || (sourceChannels == 1 && green != 0)) {
                    fail("Unexpected expanded channel at " + x + "," + y + " in " + relativePath);
                }
                sourceBytes.write(red);
                if (sourceChannels == 2) {
                    sourceBytes.write(green);
                }
            }
        }

        assertEquals(expectedSourceHash, sha256(sourceBytes.toByteArray()));
    }

    private static String readUtf8(String path) throws IOException {
        return new String(readResourceBytes(path), StandardCharsets.UTF_8);
    }

    private static byte[] readResourceBytes(String path) throws IOException {
        try (InputStream input = SmaaResourceTest.class.getResourceAsStream(path)) {
            assertNotNull(input, () -> "Missing test resource: " + path);
            return input.readAllBytes();
        }
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static int occurrences(String value, String needle) {
        int count = 0;
        int index = 0;
        while ((index = value.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
