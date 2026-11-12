package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.lwjgl.util.shaderc.Shaderc;
import org.betterLostItems.salts_anti_aliasing.client.render.common.ShaderCompilationExecutor;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/** Compiles the production algorithms without depending on a Minecraft shader JSON schema. */
public final class VulkanShaderCompiler {
    private static final String ROOT = "/assets/salts_anti_aliasing/shaders/";
    private static final Pattern IMPORT = Pattern.compile(
            "(?m)^\\s*#(?:moj_import|include)\\s+[<\"]([^>\"]+)[>\"]\\s*$");
    private static final Pattern BLOCK = Pattern.compile(
            "(?:layout\\s*\\(\\s*std140\\s*\\)\\s*)?uniform\\s+(\\w+)\\s*\\{");
    private static final Pattern SAMPLER = Pattern.compile(
            "uniform\\s+((?:(?:lowp|mediump|highp)\\s+)?(?:[iu]?sampler\\w+))\\s+(\\w+)\\s*;");

    public record Compiled(byte[] spirv, SpirvReflection reflection) {}

    public static Compiled fragment(String name) {
        return compile(name, source("post/" + name + ".fsh"), Shaderc.shaderc_fragment_shader);
    }
    public static Compiled vertex(String name) {
        return compile(name, source("post/" + name + ".vsh"), Shaderc.shaderc_vertex_shader);
    }

    public static Compiled fullscreenVertex() {
        return compile("salts_fullscreen.vsh", """
                #version 450
                layout(location = 0) out vec2 texCoord;
                void main() {
                    texCoord = vec2((gl_VertexIndex << 1) & 2, gl_VertexIndex & 2);
                    gl_Position = vec4(texCoord * 2.0 - 1.0, 0.0, 1.0);
                }
                """, Shaderc.shaderc_vertex_shader);
    }

    public static Compiled compile(String name, String source, int stage) {
        try {
            return java.util.concurrent.CompletableFuture.supplyAsync(() -> compileDirect(name, source, stage),
                    ShaderCompilationExecutor.forPipelineCompilation(Runnable::run)).join();
        } catch (java.util.concurrent.CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw e;
        }
    }

    private static Compiled compileDirect(String name, String source, int stage) {
        long compiler = Shaderc.shaderc_compiler_initialize();
        if (compiler == 0) throw new IllegalStateException("Shaderc compiler unavailable");
        long options = Shaderc.shaderc_compile_options_initialize();
        if (options == 0) {
            Shaderc.shaderc_compiler_release(compiler);
            throw new IllegalStateException("Shaderc compile options unavailable");
        }
        try {
            // VulkanMod's oldest supported releases use Vulkan 1.1, not dynamic rendering.
            Shaderc.shaderc_compile_options_set_target_env(options,
                    Shaderc.shaderc_target_env_vulkan, Shaderc.shaderc_env_version_vulkan_1_1);
            Shaderc.shaderc_compile_options_set_auto_bind_uniforms(options, true);
            Shaderc.shaderc_compile_options_set_auto_map_locations(options, true);
            long result = Shaderc.shaderc_compile_into_spv(compiler, bind(source), stage, name, "main", options);
            if (result == 0) throw new IllegalStateException("Shaderc returned no result for " + name);
            try {
                if (Shaderc.shaderc_result_get_compilation_status(result) != Shaderc.shaderc_compilation_status_success)
                    throw new IllegalArgumentException(name + ": " + Shaderc.shaderc_result_get_error_message(result));
                ByteBuffer bytes = Shaderc.shaderc_result_get_bytes(result);
                if (bytes == null) throw new IllegalStateException("Shaderc returned no SPIR-V for " + name);
                byte[] binary = new byte[bytes.remaining()];
                bytes.get(binary);
                return new Compiled(binary, SpirvReflection.read(binary));
            } finally {
                Shaderc.shaderc_result_release(result);
            }
        } finally {
            Shaderc.shaderc_compile_options_release(options);
            Shaderc.shaderc_compiler_release(compiler);
        }
    }

    private static String bind(String source) {
        // Older Shaderc can assign colliding automatic bindings when a large include
        // contains inactive shader branches (notably SMAA). Allocate a single namespace.
        int binding = 0;
        var output = new StringBuilder();
        var blocks = BLOCK.matcher(source);
        while (blocks.find()) blocks.appendReplacement(output,
                "layout(std140, set=0, binding=" + binding++ + ") uniform " + blocks.group(1) + " {");
        blocks.appendTail(output);
        var samplers = SAMPLER.matcher(output.toString());
        output.setLength(0);
        while (samplers.find()) samplers.appendReplacement(output,
                "layout(set=0, binding=" + binding++ + ") uniform " + samplers.group(1) + " " + samplers.group(2) + ";");
        samplers.appendTail(output);
        return output.toString();
    }

    public static String source(String path) {
        return expand(path, new LinkedHashSet<>(), new LinkedHashSet<>());
    }

    private static String expand(String path, Set<String> importing, Set<String> imported) {
        if (path.contains("..") || path.startsWith("/") || path.contains("\\"))
            throw new IllegalArgumentException("Invalid shader resource " + path);
        if (!importing.add(path)) throw new IllegalArgumentException("Shader include cycle: " + importing + " -> " + path);
        if (importing.size() > 32) throw new IllegalArgumentException("Shader includes exceed 32 levels: " + path);
        try (var input = VulkanShaderCompiler.class.getResourceAsStream(ROOT + path)) {
            if (input == null) throw new IllegalArgumentException("Missing shader resource " + path);
            String text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            var matcher = IMPORT.matcher(text);
            var output = new StringBuilder();
            while (matcher.find()) {
                String name = matcher.group(1);
                if (name.contains(":")) {
                    if (!name.startsWith("salts_anti_aliasing:"))
                        throw new IllegalArgumentException("Unsupported shader namespace " + name);
                    name = "include/" + name.substring(name.indexOf(':') + 1);
                } else if (!name.startsWith("include/")) {
                    name = path.substring(0, path.lastIndexOf('/') + 1) + name;
                }
                String included = imported.add(name) ? expand(name, importing, imported) : "";
                matcher.appendReplacement(output, java.util.regex.Matcher.quoteReplacement(included));
            }
            matcher.appendTail(output);
            return output.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read shader " + path, e);
        } finally {
            importing.remove(path);
        }
    }
}
