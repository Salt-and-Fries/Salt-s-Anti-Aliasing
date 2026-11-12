package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import org.betterLostItems.salts_anti_aliasing.client.config.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.shaderc.Shaderc;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class SgsrIntegrationTest {
    @Test void reprojectsConventionalDepthThroughMinecraftReverseZ() {
        Matrix4f flip = new Matrix4f().m22(-1).m32(1);
        Matrix4f current = new Matrix4f(flip).mul(new Matrix4f().perspective((float)Math.toRadians(70),16f/9,.05f,512,true));
        Matrix4f previous = new Matrix4f(current).rotateY(.03f).translate(.2f,0,.05f);
        Vector4f world = new Vector4f(1,.2f,-8,1);
        Vector4f currentClip = new Matrix4f(flip).mul(current).transform(new Vector4f(world));
        Vector4f expected = new Matrix4f(flip).mul(previous).transform(new Vector4f(world));
        Vector4f actual = SgsrTemporalParameters.clipToPrevious(current.get(new float[16]),previous.get(new float[16])).transform(currentClip);
        assertEquals(expected.x/expected.w,actual.x/actual.w,1e-5);
        assertEquals(expected.y/expected.w,actual.y/actual.w,1e-5);
        assertEquals(expected.z/expected.w,actual.z/actual.w,1e-5);
    }
    @Test void staticCameraHasIdentityReprojection() {
        var matrix = new Matrix4f().perspective(1.2f,1.7f,512,.05f,true).rotateX(.3f);
        assertTrue(SgsrTemporalParameters.clipToPrevious(matrix.get(new float[16]),matrix.get(new float[16])).equals(new Matrix4f(),1e-5f));
        assertTrue(SgsrTemporalParameters.cameraStill(matrix.get(new float[16]),matrix.get(new float[16])));
    }
    @Test void sampleVarianceCurveIsCappedAtTwenty() {
        assertEquals(1,SgsrTemporalParameters.varianceScale(1920,1080,1920,1080));
        assertEquals(11.390625f,SgsrTemporalParameters.varianceScale(1280,720,1920,1080),1e-5);
        assertEquals(20,SgsrTemporalParameters.varianceScale(960,540,1920,1080));
        assertEquals(32,SgsrTemporalParameters.JITTER_PHASES);
    }
    @Test void independentVersionSettingsSurviveCopyAndSanitization() {
        var config = new AntiAliasingConfig();
        config.sgsr1QualityPreset = SgsrQualityPreset.PERFORMANCE;
        config.sgsr2QualityPreset = SgsrQualityPreset.NATIVE;
        config.mode = AntiAliasingMode.SGSR2_SUPER_RESOLUTION;
        var copy = config.copy();copy.sanitize();
        assertEquals(1,copy.sceneRenderScale());assertTrue(copy.mode.usesHistoryBuffers());
        copy.mode = AntiAliasingMode.SGSR1_UPSCALE;
        assertEquals(.5,copy.sceneRenderScale());assertFalse(copy.mode.usesHistoryBuffers());
        copy.sgsr2QualityPreset = null;copy.tuning.sgsr1EdgeThreshold=Float.NaN;copy.tuning.sgsr1EdgeSharpness=10;copy.sanitize();
        assertEquals(SgsrQualityPreset.QUALITY,copy.sgsr2QualityPreset);
        assertEquals(8f/255,copy.tuning.sgsr1EdgeThreshold);assertEquals(2,copy.tuning.sgsr1EdgeSharpness);
        assertEquals(SgsrQualityPreset.NATIVE,config.sgsr2QualityPreset);
    }
    @Test void smallAndOddOutputSizesRemainValid() {
        for(var quality:SgsrQualityPreset.values()) {
            assertTrue(quality.width(1279)>=1);assertTrue(quality.width(1279)<=1279);
            assertEquals(1,quality.width(1));
        }
    }
    @Test void portableFragmentShadersCompileForVulkan12() throws Exception {
        long compiler=Shaderc.shaderc_compiler_initialize(), options=Shaderc.shaderc_compile_options_initialize();
        try {
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options,true);
            Shaderc.shaderc_compile_options_set_auto_map_locations(options,true);
            Shaderc.shaderc_compile_options_set_target_env(options,Shaderc.shaderc_target_env_vulkan,Shaderc.shaderc_env_version_vulkan_1_2);
            for(String name:new String[]{"sgsr1_standard","sgsr1_edge","sgsr2_convert","sgsr2_upscale","sgsr2_entity_motion","sgsr2_particle_motion"}) {
                String source=resource("shaders/post/"+name+".fsh");
                var include=Pattern.compile("#moj_import <salts_anti_aliasing:([^>]+)>").matcher(source);
                while(include.find()) source=source.replace(include.group(),resource("shaders/include/"+include.group(1)));
                long result=Shaderc.shaderc_compile_into_spv(compiler,source,Shaderc.shaderc_fragment_shader,name,"main",options);
                try { assertEquals(Shaderc.shaderc_compilation_status_success,Shaderc.shaderc_result_get_compilation_status(result),name+": "+Shaderc.shaderc_result_get_error_message(result)); }
                finally { Shaderc.shaderc_result_release(result); }
            }
        } finally { Shaderc.shaderc_compile_options_release(options);Shaderc.shaderc_compiler_release(compiler); }
    }
    private String resource(String path) throws IOException {
        try(var in=getClass().getClassLoader().getResourceAsStream("assets/salts_anti_aliasing/"+path)) {
            assertNotNull(in,path);return new String(in.readAllBytes(),StandardCharsets.UTF_8);
        }
    }
}
