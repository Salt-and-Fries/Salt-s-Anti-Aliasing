package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

/** Optional terrain texture LOD adjustment, applied without replacing resource-pack shaders. */
public final class TextureDetailShaderPatch {
    private static final String PATCH_MARKER = "// Salt's terrain MIP adjustment";

    private TextureDetailShaderPatch() {}

    /** Classify by fragment source, since Sodium's OIT pipeline names use minecraft's namespace. */
    public static boolean supportsTerrainShader(String shader) {
        return "minecraft:core/terrain".equals(shader)
                || "sodium:blocks/block_layer_opaque".equals(shader);
    }

    public static String patch(String source) {
        if (source.contains(PATCH_MARKER)) return source;
        int versionEnd = source.indexOf('\n');
        if (versionEnd < 0) return source;
        String adjustment =
                """
                // Salt's terrain MIP adjustment
                #ifdef SALTS_MIP_BIAS
                #define textureGrad(s,u,dx,dy) textureGrad(s,u,(dx)*exp2(SALTS_MIP_BIAS),(dy)*exp2(SALTS_MIP_BIAS))
                #define textureLod(s,u,l) textureLod(s,u,max(0.0,(l)+SALTS_MIP_BIAS))
                #endif
                """;
        return source.substring(0, versionEnd + 1) + adjustment + source.substring(versionEnd + 1);
    }

    public static int quarterSteps(
            boolean automatic, float custom, float offset, int renderWidth, int displayWidth) {
        return quarterSteps(automatic, custom, offset, renderWidth, displayWidth, true);
    }

    public static int quarterSteps(
            boolean automatic,
            float custom,
            float offset,
            int renderWidth,
            int displayWidth,
            boolean temporal) {
        double bias =
                automatic
                        ? Math.log(Math.max(1, renderWidth) / (double) Math.max(1, displayWidth))
                                        / Math.log(2)
                                - (temporal ? 1 : 0)
                                + offset
                        : custom;
        return (int) Math.round(Math.max(-3, Math.min(1, bias)) * 4);
    }
}
