package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.shaderc.Shaderc;
import org.lwjgl.vulkan.VkBufferImageCopy;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.vulkan.VK10.*;

@EnabledIfSystemProperty(named = "salts.gpuValidation", matches = "true")
class VulkanGraphicsPassGpuTest {
    @Test void drawsProductionFxaaAndReusesFrameResourcesWithoutReadWriteAliasing() {
        try (var gpu = new HeadlessVulkan();
             var source = VulkanImageResource.create(gpu.context, 32, 24, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT, 1);
             var output = VulkanImageResource.create(gpu.context, 32, 24, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT, 1);
             var pass = new VulkanGraphicsPass(gpu.context, "fxaa", VK_FORMAT_R8G8B8A8_UNORM);
             var frame = new VulkanFrameResources();
             var readback = new VulkanHostBuffer(gpu.context, 32 * 24 * 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT)) {
            Map<String, ByteBuffer> uniforms = Map.of("SamplerInfo", floats(32, 24, 32, 24),
                    "FxaaConfig", floats(0.75f, 0.166f, 0.0833f, 0));
            for (int iteration = 0; iteration < 3; iteration++) {
                gpu.begin(); frame.beginAfterFence();
                source.clear(gpu.commands, 0.25f, 0.5f, 0.75f, 1);
                pass.draw(gpu.commands, frame, List.of(output), Map.of("InSampler", new VulkanGraphicsPass.Sample(source, false)), uniforms);
                copy(gpu, output, readback);
                gpu.submit();
                var pixels = readback.read();
                for (int pixel = 0; pixel < 32 * 24; pixel++) {
                    assertEquals(64, Byte.toUnsignedInt(pixels.get()), 1);
                    assertEquals(128, Byte.toUnsignedInt(pixels.get()), 1);
                    assertEquals(191, Byte.toUnsignedInt(pixels.get()), 1);
                    assertEquals(255, Byte.toUnsignedInt(pixels.get()));
                }
            }
            gpu.begin(); frame.beginAfterFence();
            assertThrows(IllegalArgumentException.class, () -> pass.draw(gpu.commands, frame, List.of(source),
                    Map.of("InSampler", new VulkanGraphicsPass.Sample(source, false)), uniforms));
            gpu.submit();
        }
    }

    @Test void writesBothMrtOutputsWithAnExplicitRenderArea() {
        var shader = VulkanShaderCompiler.compile("mrt-test", """
                #version 450
                layout(location=0) out vec4 first;
                layout(location=1) out vec4 second;
                void main() { first=vec4(1,0,0,1); second=vec4(0,1,0,1); }
                """, Shaderc.shaderc_fragment_shader);
        try (var gpu = new HeadlessVulkan();
             var first = VulkanImageResource.create(gpu.context, 8, 8, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT, 1);
             var second = VulkanImageResource.create(gpu.context, 8, 8, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT, 1);
             var pass = new VulkanGraphicsPass(gpu.context, shader, VK_FORMAT_R8G8B8A8_UNORM, VK_FORMAT_R8G8B8A8_UNORM);
             var frame = new VulkanFrameResources();
             var readback = new VulkanHostBuffer(gpu.context, 8 * 8 * 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT)) {
            gpu.begin(); frame.beginAfterFence();
            pass.draw(gpu.commands, frame, List.of(first, second), Map.of(), Map.of());
            copy(gpu, second, readback); gpu.submit();
            var pixels = readback.read();
            for (int pixel = 0; pixel < 64; pixel++) {
                assertEquals(0, pixels.get()); assertEquals(255, Byte.toUnsignedInt(pixels.get()));
                assertEquals(0, pixels.get()); assertEquals(255, Byte.toUnsignedInt(pixels.get()));
            }
        }
    }

    @Test void normalizesForwardDepthIntoARealReverseDepthAttachment() {
        try (var gpu = new HeadlessVulkan();
             var input = VulkanImageResource.create(gpu.context, 16, 16, VK_FORMAT_R32_SFLOAT, VK_IMAGE_ASPECT_COLOR_BIT, 1);
             var depth = VulkanImageResource.create(gpu.context, 16, 16, VK_FORMAT_D32_SFLOAT, VK_IMAGE_ASPECT_DEPTH_BIT, 1);
             var pass = VulkanGraphicsPass.depth(gpu.context, "vulkanmod_reverse_depth", VK_FORMAT_D32_SFLOAT);
             var frame = new VulkanFrameResources();
             var readback = new VulkanHostBuffer(gpu.context, 16 * 16 * 4, VK_BUFFER_USAGE_TRANSFER_DST_BIT)) {
            gpu.begin(); frame.beginAfterFence();
            input.clear(gpu.commands, 0.25f, 0, 0, 1);
            pass.draw(gpu.commands, frame, List.of(depth), Map.of("InputSampler", new VulkanGraphicsPass.Sample(input, false)),
                    Map.of("DepthNormalization", floats(1, 0, 0, 0)));
            copy(gpu, depth, readback); gpu.submit();
            var data = readback.read();
            for (int pixel = 0; pixel < 256; pixel++) assertEquals(0.75f, data.getFloat(), 0.000001f);
        }
    }

    @Test void overlaysObjectVelocityOnlyAtMatchingDepthAndPreservesCameraFlowElsewhere() {
        try (var gpu = new HeadlessVulkan();
             var depth = VulkanImageResource.create(gpu.context, 16, 16, VK_FORMAT_D32_SFLOAT, VK_IMAGE_ASPECT_DEPTH_BIT, 1);
             var motion = VulkanImageResource.create(gpu.context, 16, 16, VK_FORMAT_R32G32B32A32_SFLOAT, VK_IMAGE_ASPECT_COLOR_BIT, 1);
             var pass = VulkanGraphicsPass.motion(gpu.context, motion.format, false);
             var frame = new VulkanFrameResources();
             var readback = new VulkanHostBuffer(gpu.context, 16 * 16 * 16, VK_BUFFER_USAGE_TRANSFER_DST_BIT)) {
            var vertices = ByteBuffer.allocate(72).order(ByteOrder.nativeOrder());
            for (var position : new float[][]{{-.75f, -.75f}, {.75f, -.75f}, {0, .75f}})
                vertices.putFloat(position[0]).putFloat(position[1]).putFloat(.5f)
                        .putFloat(position[0] + .25f).putFloat(position[1]).putFloat(.5f);
            vertices.flip();
            var config = ByteBuffer.allocate(160).order(ByteOrder.nativeOrder());
            for (int matrix = 0; matrix < 2; matrix++) for (float value : new org.joml.Matrix4f().get(new float[16])) config.putFloat(value);
            config.putFloat(0).putFloat(0).putFloat(16).putFloat(16);
            config.putFloat(0).putFloat(0).putFloat(0).putFloat(0).flip();
            for (float sceneDepth : new float[]{.5f, .25f}) {
                gpu.begin(); frame.beginAfterFence();
                depth.clearDepth(gpu.commands, sceneDepth);
                motion.clear(gpu.commands, .125f, .25f, 0, 1);
                pass.draw(gpu.commands, frame, List.of(motion),
                        Map.of("SceneDepthSampler", new VulkanGraphicsPass.Sample(depth, false)),
                        Map.of("EntityMotionConfig", config), vertices);
                copy(gpu, motion, readback); gpu.submit();
                var pixels = readback.read();
                int center = (8 * 16 + 8) * 16;
                assertEquals(sceneDepth == .5f ? 2f : .125f, pixels.getFloat(center), .00001f);
                assertEquals(sceneDepth == .5f ? 0f : .25f, pixels.getFloat(center + 4), .00001f);
                assertEquals(.125f, pixels.getFloat(0), .00001f);
                assertEquals(.25f, pixels.getFloat(4), .00001f);
            }
        }
    }

    private static void copy(HeadlessVulkan gpu, VulkanImageResource image, VulkanHostBuffer buffer) {
        image.transition(gpu.commands, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var copy = VkBufferImageCopy.calloc(1, stack).bufferOffset(0).bufferRowLength(0).bufferImageHeight(0);
            copy.imageSubresource().aspectMask((image.aspect & VK_IMAGE_ASPECT_DEPTH_BIT) != 0 ? VK_IMAGE_ASPECT_DEPTH_BIT : VK_IMAGE_ASPECT_COLOR_BIT)
                    .mipLevel(0).baseArrayLayer(0).layerCount(1);
            copy.imageOffset().set(0, 0, 0); copy.imageExtent().set(image.width, image.height, 1);
            vkCmdCopyImageToBuffer(gpu.commands, image.image, image.layout(), buffer.buffer, copy);
        }
    }

    @Test void particleVelocityRespectsAlphaOpaqueOcclusionAndNearestParticleDepth() {
        try (var gpu = new HeadlessVulkan();
             var opaque = VulkanImageResource.create(gpu.context, 16, 16, VK_FORMAT_D32_SFLOAT, VK_IMAGE_ASPECT_DEPTH_BIT, 1);
             var nearest = VulkanImageResource.create(gpu.context, 16, 16, VK_FORMAT_D32_SFLOAT, VK_IMAGE_ASPECT_DEPTH_BIT, 1);
             var atlas = VulkanImageResource.create(gpu.context, 1, 1, VK_FORMAT_R8G8B8A8_UNORM, VK_IMAGE_ASPECT_COLOR_BIT, 1);
             var motion = VulkanImageResource.create(gpu.context, 16, 16, VK_FORMAT_R32G32B32A32_SFLOAT, VK_IMAGE_ASPECT_COLOR_BIT, 1);
             var pass = VulkanGraphicsPass.particles(gpu.context, motion.format, false);
             var frame = new VulkanFrameResources();
             var readback = new VulkanHostBuffer(gpu.context, 16 * 16 * 16, VK_BUFFER_USAGE_TRANSFER_DST_BIT)) {
            var vertices = ByteBuffer.allocate(240).order(ByteOrder.nativeOrder());
            for (float depth : new float[]{.8f, .6f})
                for (var position : new float[][]{{-.75f, -.75f}, {.75f, -.75f}, {0, .75f}})
                    vertices.putFloat(position[0]).putFloat(position[1]).putFloat(depth)
                            .putFloat(position[0] + (depth == .8f ? .25f : .5f)).putFloat(position[1]).putFloat(depth)
                            .putFloat(.5f).putFloat(.5f).putFloat(1).putFloat(.5f);
            vertices.flip();
            var config = ByteBuffer.allocate(160).order(ByteOrder.nativeOrder());
            for (int matrix = 0; matrix < 2; matrix++) for (float value : new org.joml.Matrix4f().get(new float[16])) config.putFloat(value);
            config.putFloat(0).putFloat(0).putFloat(16).putFloat(16);
            config.putFloat(0).putFloat(0).putFloat(0).putFloat(0).flip();
            for (var scenario : new float[][]{{1, .5f, 2}, {0, .5f, .125f}, {1, .9f, .125f}}) {
                gpu.begin(); frame.beginAfterFence();
                atlas.clear(gpu.commands, 1, 1, 1, scenario[0]);
                opaque.clearDepth(gpu.commands, scenario[1]); nearest.clearDepth(gpu.commands, 0);
                motion.clear(gpu.commands, .125f, .25f, 0, 1);
                pass.draw(gpu.commands, frame, List.of(motion, nearest), Map.of(
                                "SceneDepthSampler", new VulkanGraphicsPass.Sample(opaque, false),
                                "ParticleAtlas", new VulkanGraphicsPass.Sample(atlas, false)),
                        Map.of("ParticleMotionConfig", config), vertices);
                copy(gpu, motion, readback); gpu.submit();
                var pixels = readback.read();
                assertEquals(scenario[2], pixels.getFloat((8 * 16 + 8) * 16), .00001f);
                assertEquals(.125f, pixels.getFloat(0), .00001f);
                assertEquals(.25f, pixels.getFloat(4), .00001f);
            }
        }
    }

    private static ByteBuffer floats(float... values) {
        var bytes = ByteBuffer.allocate(values.length * 4).order(ByteOrder.nativeOrder());
        for (float value : values) bytes.putFloat(value);
        return bytes.flip();
    }
}
