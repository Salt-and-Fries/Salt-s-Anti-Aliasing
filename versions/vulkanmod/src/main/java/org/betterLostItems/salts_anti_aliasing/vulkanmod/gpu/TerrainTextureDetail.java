package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.betterLostItems.salts_anti_aliasing.client.config.*;

/** Uses the spare high bits of VulkanMod's terrain-only RGSS uniform without changing its ABI. */
public final class TerrainTextureDetail {
    private static final String MARKER = "// Salt's terrain MIP adjustment";
    private TerrainTextureDetail() {}
    public static int quarterSteps(AntiAliasingConfig config, int renderWidth, int displayWidth) {
        var mode = config.mode;
        if (!mode.usesDedicatedUpscalePass() || config.tuning.mipBiasMode == RenderTuning.MipBiasMode.OFF) return 0;
        var tuning = config.tuning;
        boolean temporal = mode.usesDlssQualityControl() || mode.usesFsrQualityControl();
        double bias = tuning.mipBiasMode == RenderTuning.MipBiasMode.AUTOMATIC
                ? Math.log(Math.max(1, renderWidth) / (double) Math.max(1, displayWidth)) / Math.log(2)
                    - (temporal ? 1 : 0) + tuning.mipBiasOffset
                : tuning.mipBias;
        return (int) Math.round(Math.max(-3, Math.min(1, bias)) * 4);
    }
    public static String patch(String source) {
        if (source == null || source.contains(MARKER) || !source.contains("int UseRgss;") || !source.contains("TexelSize")) return source;
        int end = source.indexOf('\n');
        if (end < 0) return source;
        String macros = """
                // Salt's terrain MIP adjustment
                #define SALTS_MIP_BIAS (float(UseRgss >> 8) * 0.25)
                #define textureGrad(s,u,dx,dy) textureGrad(s,u,(dx)*exp2(SALTS_MIP_BIAS),(dy)*exp2(SALTS_MIP_BIAS))
                #define textureLod(s,u,l) textureLod(s,u,max(0.0,(l)+SALTS_MIP_BIAS))
                """;
        return source.substring(0, end + 1) + macros + source.substring(end + 1).replace("UseRgss == 1", "(UseRgss & 1) == 1");
    }

    /** Older terrain shaders have no RGSS uniform. Their terrain-only cutoff leaves integer lanes free. */
    public static String patchLegacy(String source) {
        if (source == null || source.contains(MARKER) || !source.contains("float AlphaCutout;")
                || !source.contains("color.a < AlphaCutout")) return source;
        int end = source.indexOf('\n');
        if (end < 0) return source;
        String macros = """
                // Salt's terrain MIP adjustment
                #define SALTS_MIP_INDEX floor(AlphaCutout * 0.5)
                #define SALTS_MIP_BIAS (SALTS_MIP_INDEX * 0.25)
                #define SALTS_ALPHA_CUTOUT (AlphaCutout - SALTS_MIP_INDEX * 2.0)
                #define texture(s,u) texture(s,u,SALTS_MIP_BIAS)
                """;
        return source.substring(0, end + 1) + macros
                + source.substring(end + 1).replace("color.a < AlphaCutout", "color.a < SALTS_ALPHA_CUTOUT");
    }

    /** The original terrain UBO is extended together with its native JSON field layout. */
    public static String patchClassic(String source) {
        if (source == null || source.contains(MARKER) || !source.contains("float FogEnd;")
                || !source.contains("texture(Sampler0, texCoord0)")) return source;
        return source.replace("float FogEnd;", "float FogEnd;\n    float SaltsMipBias;\n    " + MARKER)
                .replace("texture(Sampler0, texCoord0)", "texture(Sampler0, texCoord0, SaltsMipBias)");
    }
}
