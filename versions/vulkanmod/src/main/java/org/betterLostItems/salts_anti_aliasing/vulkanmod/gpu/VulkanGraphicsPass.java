package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.vulkan.*;

import java.nio.ByteBuffer;
import java.util.*;

import static org.lwjgl.vulkan.VK10.*;

/** Standalone Vulkan 1.1 fullscreen pipeline, shared by every VulkanMod/Minecraft adapter. */
public final class VulkanGraphicsPass implements AutoCloseable {
    public record Sample(VulkanImageResource image, boolean linear) {}
    private final VulkanContext context;
    private final SpirvReflection reflection;
    private final int[] formats;
    private final int depthFormat;
    private final int sampleCount;
    private final boolean geometry;
    private final boolean particles;
    private final int vertexStride;
    private long layout, descriptorLayout, renderPass, pipeline, nearest, linear;

    public VulkanGraphicsPass(VulkanContext context, String fragment, int... formats) {
        this(context, VulkanShaderCompiler.fragment(fragment), formats);
    }

    public VulkanGraphicsPass(VulkanContext context, VulkanShaderCompiler.Compiled fragment, int... formats) {
        this(context, fragment, formats, 0, 1);
    }

    public static VulkanGraphicsPass depth(VulkanContext context, String fragment, int format) {
        return new VulkanGraphicsPass(context, VulkanShaderCompiler.fragment(fragment), new int[0], format, 1);
    }

    public static VulkanGraphicsPass multisample(VulkanContext context, String fragment, int format, int samples) {
        return new VulkanGraphicsPass(context, VulkanShaderCompiler.fragment(fragment), new int[]{format}, 0, samples);
    }

    private VulkanGraphicsPass(VulkanContext context, VulkanShaderCompiler.Compiled fragment, int[] formats, int depthFormat, int samples) {
        this(context, fragment, formats, depthFormat, samples, 0);
    }

    public static VulkanGraphicsPass motion(VulkanContext context, int format, boolean sgsr) {
        return new VulkanGraphicsPass(context, VulkanShaderCompiler.fragment(sgsr ? "sgsr2_entity_motion" : "entity_motion"),
                new int[]{format}, 0, 1, 1);
    }

    public static VulkanGraphicsPass particles(VulkanContext context, int format, boolean sgsr) {
        return new VulkanGraphicsPass(context, VulkanShaderCompiler.fragment(sgsr ? "sgsr2_particle_motion" : "particle_motion"),
                new int[]{format}, VK_FORMAT_D32_SFLOAT, 1, 2);
    }

    private VulkanGraphicsPass(VulkanContext context, VulkanShaderCompiler.Compiled fragment, int[] formats, int depthFormat, int samples, int kind) {
        this.context = context; this.reflection = fragment.reflection(); this.formats = formats.clone(); this.depthFormat = depthFormat;
        this.geometry = kind != 0; this.particles = kind == 2; vertexStride = particles ? 40 : 24;
        if (samples < 1 || samples > 64 || Integer.bitCount(samples) != 1) throw new IllegalArgumentException("Invalid sample count");
        sampleCount = samples;
        if (formats.length == 0 && depthFormat == 0 || reflection.colorOutputs() != formats.length)
            throw new IllegalArgumentException("Fragment output/attachment count mismatch");
        for (var descriptor : reflection.descriptors())
            if (descriptor.set() != 0 || descriptor.count() != 1)
                throw new IllegalArgumentException("Unsupported shader descriptor shape " + descriptor);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var bindings = VkDescriptorSetLayoutBinding.calloc(reflection.descriptors().size(), stack);
            for (int i = 0; i < bindings.capacity(); i++) {
                var descriptor = reflection.descriptors().get(i);
                bindings.get(i).binding(descriptor.binding()).descriptorCount(1)
                        .descriptorType(descriptorType(descriptor.kind())).stageFlags(VK_SHADER_STAGE_FRAGMENT_BIT | (geometry ? VK_SHADER_STAGE_VERTEX_BIT : 0));
            }
            var setInfo = VkDescriptorSetLayoutCreateInfo.calloc(stack).sType$Default().pBindings(bindings);
            var handle = stack.mallocLong(1);
            VulkanContext.check(vkCreateDescriptorSetLayout(context.device(), setInfo, null, handle), "Create descriptor layout");
            descriptorLayout = handle.get(0);
            var layoutInfo = VkPipelineLayoutCreateInfo.calloc(stack).sType$Default().pSetLayouts(stack.longs(descriptorLayout));
            VulkanContext.check(vkCreatePipelineLayout(context.device(), layoutInfo, null, handle), "Create pipeline layout");
            layout = handle.get(0);
            createRenderPass(stack);
            createPipeline(stack, fragment);
            nearest = createSampler(stack, VK_FILTER_NEAREST);
            linear = createSampler(stack, VK_FILTER_LINEAR);
        } catch (RuntimeException e) {
            close(); throw e;
        }
    }

    private void createRenderPass(MemoryStack stack) {
        var attachments = VkAttachmentDescription.calloc(formats.length + (depthFormat == 0 ? 0 : 1), stack);
        var references = VkAttachmentReference.calloc(formats.length, stack);
        for (int i = 0; i < formats.length; i++) {
            attachments.get(i).format(formats[i]).samples(sampleCount)
                    .loadOp(geometry ? VK_ATTACHMENT_LOAD_OP_LOAD : VK_ATTACHMENT_LOAD_OP_DONT_CARE).storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL).finalLayout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            references.get(i).attachment(i).layout(VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
        }
        var subpass = VkSubpassDescription.calloc(1, stack).pipelineBindPoint(VK_PIPELINE_BIND_POINT_GRAPHICS)
                .colorAttachmentCount(formats.length).pColorAttachments(references);
        if (depthFormat != 0) {
            attachments.get(formats.length).format(depthFormat).samples(sampleCount)
                    .loadOp(particles ? VK_ATTACHMENT_LOAD_OP_LOAD : VK_ATTACHMENT_LOAD_OP_DONT_CARE).storeOp(VK_ATTACHMENT_STORE_OP_STORE)
                    .stencilLoadOp(VK_ATTACHMENT_LOAD_OP_DONT_CARE).stencilStoreOp(VK_ATTACHMENT_STORE_OP_DONT_CARE)
                    .initialLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL)
                    .finalLayout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL);
            subpass.pDepthStencilAttachment(VkAttachmentReference.calloc(stack).attachment(formats.length)
                    .layout(VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL));
        }
        var dependency = VkSubpassDependency.calloc(1, stack).srcSubpass(VK_SUBPASS_EXTERNAL).dstSubpass(0)
                .srcStageMask(VK_PIPELINE_STAGE_ALL_COMMANDS_BIT).dstStageMask(VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT
                        | VK_PIPELINE_STAGE_EARLY_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_LATE_FRAGMENT_TESTS_BIT | VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT)
                .srcAccessMask(VK_ACCESS_MEMORY_WRITE_BIT).dstAccessMask(VK_ACCESS_COLOR_ATTACHMENT_READ_BIT | VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT
                        | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_READ_BIT | VK_ACCESS_DEPTH_STENCIL_ATTACHMENT_WRITE_BIT | VK_ACCESS_SHADER_READ_BIT);
        var info = VkRenderPassCreateInfo.calloc(stack).sType$Default().pAttachments(attachments)
                .pSubpasses(subpass).pDependencies(dependency);
        var handle = stack.mallocLong(1);
        VulkanContext.check(vkCreateRenderPass(context.device(), info, null, handle), "Create pass");
        renderPass = handle.get(0);
    }

    private void createPipeline(MemoryStack stack, VulkanShaderCompiler.Compiled fragment) {
        long vertexModule = shaderModule(stack, (geometry ? VulkanShaderCompiler.vertex(particles ? "particle_motion" : "entity_motion") : VulkanShaderCompiler.fullscreenVertex()).spirv());
        long fragmentModule = 0;
        try {
            fragmentModule = shaderModule(stack, fragment.spirv());
            var stages = VkPipelineShaderStageCreateInfo.calloc(2, stack);
            stages.get(0).sType$Default().stage(VK_SHADER_STAGE_VERTEX_BIT).module(vertexModule).pName(stack.UTF8("main"));
            stages.get(1).sType$Default().stage(VK_SHADER_STAGE_FRAGMENT_BIT).module(fragmentModule).pName(stack.UTF8("main"));
            var vertex = VkPipelineVertexInputStateCreateInfo.calloc(stack).sType$Default();
            if (geometry) {
                vertex.pVertexBindingDescriptions(VkVertexInputBindingDescription.calloc(1, stack).binding(0).stride(vertexStride).inputRate(VK_VERTEX_INPUT_RATE_VERTEX));
                var attributes = VkVertexInputAttributeDescription.calloc(particles ? 5 : 2, stack);
                attributes.get(0).location(0).binding(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(0);
                attributes.get(1).location(1).binding(0).format(VK_FORMAT_R32G32B32_SFLOAT).offset(12);
                if (particles) {
                    attributes.get(2).location(2).binding(0).format(VK_FORMAT_R32G32_SFLOAT).offset(24);
                    attributes.get(3).location(3).binding(0).format(VK_FORMAT_R32_SFLOAT).offset(32);
                    attributes.get(4).location(4).binding(0).format(VK_FORMAT_R32_SFLOAT).offset(36);
                }
                vertex.pVertexAttributeDescriptions(attributes);
            }
            var assembly = VkPipelineInputAssemblyStateCreateInfo.calloc(stack).sType$Default().topology(VK_PRIMITIVE_TOPOLOGY_TRIANGLE_LIST);
            var viewport = VkPipelineViewportStateCreateInfo.calloc(stack).sType$Default().viewportCount(1).scissorCount(1);
            var raster = VkPipelineRasterizationStateCreateInfo.calloc(stack).sType$Default().polygonMode(VK_POLYGON_MODE_FILL)
                    .cullMode(VK_CULL_MODE_NONE).frontFace(VK_FRONT_FACE_COUNTER_CLOCKWISE).lineWidth(1.0f);
            var samples = VkPipelineMultisampleStateCreateInfo.calloc(stack).sType$Default().rasterizationSamples(sampleCount);
            var depth = VkPipelineDepthStencilStateCreateInfo.calloc(stack).sType$Default()
                    .depthTestEnable(depthFormat != 0).depthWriteEnable(depthFormat != 0).depthCompareOp(particles ? VK_COMPARE_OP_GREATER : VK_COMPARE_OP_ALWAYS);
            var attachments = VkPipelineColorBlendAttachmentState.calloc(formats.length, stack);
            for (int i = 0; i < formats.length; i++) attachments.get(i).blendEnable(false)
                    .colorWriteMask(VK_COLOR_COMPONENT_R_BIT | VK_COLOR_COMPONENT_G_BIT | VK_COLOR_COMPONENT_B_BIT | VK_COLOR_COMPONENT_A_BIT);
            var blend = VkPipelineColorBlendStateCreateInfo.calloc(stack).sType$Default().pAttachments(attachments);
            var dynamic = VkPipelineDynamicStateCreateInfo.calloc(stack).sType$Default()
                    .pDynamicStates(stack.ints(VK_DYNAMIC_STATE_VIEWPORT, VK_DYNAMIC_STATE_SCISSOR,
                            VK_DYNAMIC_STATE_DEPTH_BIAS, VK_DYNAMIC_STATE_LINE_WIDTH));
            var info = VkGraphicsPipelineCreateInfo.calloc(1, stack).sType$Default().pStages(stages)
                    .pVertexInputState(vertex).pInputAssemblyState(assembly).pViewportState(viewport)
                    .pRasterizationState(raster).pMultisampleState(samples).pColorBlendState(blend).pDepthStencilState(depth)
                    .pDynamicState(dynamic).layout(layout).renderPass(renderPass).subpass(0);
            var handle = stack.mallocLong(1);
            VulkanContext.check(vkCreateGraphicsPipelines(context.device(), VK_NULL_HANDLE, info, null, handle), "Create graphics pipeline");
            pipeline = handle.get(0);
        } finally {
            if (fragmentModule != 0) vkDestroyShaderModule(context.device(), fragmentModule, null);
            vkDestroyShaderModule(context.device(), vertexModule, null);
        }
    }

    private long shaderModule(MemoryStack stack, byte[] bytes) {
        var code = MemoryUtil.memAlloc(bytes.length).put(bytes).flip();
        try {
            var info = VkShaderModuleCreateInfo.calloc(stack).sType$Default().pCode(code);
            var handle = stack.mallocLong(1);
            VulkanContext.check(vkCreateShaderModule(context.device(), info, null, handle), "Create shader module");
            return handle.get(0);
        } finally { MemoryUtil.memFree(code); }
    }

    private long createSampler(MemoryStack stack, int filter) {
        var info = VkSamplerCreateInfo.calloc(stack).sType$Default().magFilter(filter).minFilter(filter)
                .mipmapMode(VK_SAMPLER_MIPMAP_MODE_NEAREST).addressModeU(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .addressModeV(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE).addressModeW(VK_SAMPLER_ADDRESS_MODE_CLAMP_TO_EDGE)
                .minLod(0).maxLod(0).maxAnisotropy(1);
        var handle = stack.mallocLong(1);
        VulkanContext.check(vkCreateSampler(context.device(), info, null, handle), "Create sampler");
        return handle.get(0);
    }

    public void draw(VkCommandBuffer commands, VulkanFrameResources frame, List<VulkanImageResource> outputs,
                     Map<String, Sample> images, Map<String, ByteBuffer> uniforms) {
        draw(commands, frame, outputs, images, uniforms, null);
    }

    public void draw(VkCommandBuffer commands, VulkanFrameResources frame, List<VulkanImageResource> outputs,
                     Map<String, Sample> images, Map<String, ByteBuffer> uniforms, ByteBuffer vertices) {
        if (geometry != (vertices != null) || vertices != null && (vertices.remaining() == 0 || vertices.remaining() % (vertexStride * 3) != 0))
            throw new IllegalArgumentException("Motion geometry requires complete position-pair triangles");
        if (pipeline == 0) throw new IllegalStateException("Pipeline is closed");
        if (outputs.size() != formats.length + (depthFormat == 0 ? 0 : 1)) throw new IllegalArgumentException("Wrong output count");
        int width = outputs.get(0).width, height = outputs.get(0).height;
        for (int i = 0; i < outputs.size(); i++) {
            var image = outputs.get(i);
            int expectedFormat = i < formats.length ? formats[i] : depthFormat;
            if (image.width != width || image.height != height || image.samples != sampleCount || image.format != expectedFormat)
                throw new IllegalArgumentException("Attachment dimensions/format/samples do not match pipeline");
        }
        var resources = frame.next(this);
        if (vertices != null) {
            if (vertices.remaining() > resources.vertexCapacity) {
                if (resources.vertices != null) resources.vertices.close();
                resources.vertexCapacity = Integer.highestOneBit(vertices.remaining()) << 1;
                resources.vertices = new VulkanHostBuffer(context, resources.vertexCapacity, VK_BUFFER_USAGE_VERTEX_BUFFER_BIT);
            }
            resources.vertices.write(vertices);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var writes = VkWriteDescriptorSet.calloc(reflection.descriptors().size(), stack);
            var imageInfos = VkDescriptorImageInfo.calloc(writes.capacity(), stack);
            var bufferInfos = VkDescriptorBufferInfo.calloc(writes.capacity(), stack);
            for (int i = 0; i < writes.capacity(); i++) {
                var descriptor = reflection.descriptors().get(i);
                var write = writes.get(i).sType$Default().dstSet(resources.set).dstBinding(descriptor.binding())
                        .descriptorCount(1).descriptorType(descriptorType(descriptor.kind()));
                if (descriptor.kind() == SpirvReflection.Kind.SAMPLED_IMAGE) {
                    Sample input = images.get(descriptor.name());
                    if (input == null) throw new IllegalArgumentException("Missing sampled input " + descriptor.name());
                    if (outputs.stream().anyMatch(output -> output.image == input.image().image))
                        throw new IllegalArgumentException("Pass reads and writes the same image");
                    input.image().transition(commands, VK_IMAGE_LAYOUT_GENERAL);
                    imageInfos.get(i).imageView(frame.sampledView(context, input.image())).imageLayout(VK_IMAGE_LAYOUT_GENERAL)
                            .sampler(input.linear() ? linear : nearest);
                    write.pImageInfo(VkDescriptorImageInfo.create(imageInfos.get(i).address(), 1));
                } else if (descriptor.kind() == SpirvReflection.Kind.UNIFORM_BUFFER) {
                    ByteBuffer data = uniforms.get(descriptor.name());
                    int required = descriptor.members().stream().mapToInt(m -> m.offset() + m.bytes()).max().orElse(0);
                    if (data == null || data.remaining() < required || data.remaining() > descriptor.bytes())
                        throw new IllegalArgumentException("Wrong uniform payload size for " + descriptor.name() + ": expected " + required + ".." + descriptor.bytes());
                    var buffer = resources.uniforms.get(descriptor.name());
                    buffer.write(data);
                    bufferInfos.get(i).buffer(buffer.buffer).offset(0).range(descriptor.bytes());
                    write.pBufferInfo(VkDescriptorBufferInfo.create(bufferInfos.get(i).address(), 1));
                } else throw new IllegalArgumentException("Fullscreen pass does not support " + descriptor.kind());
            }
            vkUpdateDescriptorSets(context.device(), writes, null);
            for (var output : outputs) output.transition(commands, (output.aspect & VK_IMAGE_ASPECT_DEPTH_BIT) != 0
                    ? VK_IMAGE_LAYOUT_DEPTH_STENCIL_ATTACHMENT_OPTIMAL : VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL);
            long framebuffer = resources.framebuffer(stack, outputs, width, height);
            var begin = VkRenderPassBeginInfo.calloc(stack).sType$Default().renderPass(renderPass).framebuffer(framebuffer);
            begin.renderArea().offset().set(0, 0); begin.renderArea().extent().set(width, height);
            vkCmdBeginRenderPass(commands, begin, VK_SUBPASS_CONTENTS_INLINE);
            var viewport = VkViewport.calloc(1, stack).x(0).y(0).width(width).height(height).minDepth(0).maxDepth(1);
            var scissor = VkRect2D.calloc(1, stack); scissor.offset().set(0, 0); scissor.extent().set(width, height);
            vkCmdSetViewport(commands, 0, viewport); vkCmdSetScissor(commands, 0, scissor);
            vkCmdBindPipeline(commands, VK_PIPELINE_BIND_POINT_GRAPHICS, pipeline);
            vkCmdBindDescriptorSets(commands, VK_PIPELINE_BIND_POINT_GRAPHICS, layout, 0, stack.longs(resources.set), null);
            if (vertices != null) vkCmdBindVertexBuffers(commands, 0, stack.longs(resources.vertices.buffer), stack.longs(0));
            vkCmdDraw(commands, vertices == null ? 3 : vertices.remaining() / vertexStride, 1, 0, 0);
            vkCmdEndRenderPass(commands);
        }
    }

    DrawResources newDrawResources() { return new DrawResources(); }

    final class DrawResources implements AutoCloseable {
        long pool, set, framebuffer;
        long[] attachmentViews;
        final Map<String, VulkanHostBuffer> uniforms = new LinkedHashMap<>();
        VulkanHostBuffer vertices;
        int vertexCapacity;

        DrawResources() {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var counts = new LinkedHashMap<Integer, Integer>();
                for (var descriptor : reflection.descriptors()) counts.merge(descriptorType(descriptor.kind()), 1, Integer::sum);
                var sizes = VkDescriptorPoolSize.calloc(counts.size(), stack);
                int i = 0;
                for (var count : counts.entrySet()) sizes.get(i++).type(count.getKey()).descriptorCount(count.getValue());
                var info = VkDescriptorPoolCreateInfo.calloc(stack).sType$Default().maxSets(1).pPoolSizes(sizes);
                var handle = stack.mallocLong(1);
                VulkanContext.check(vkCreateDescriptorPool(context.device(), info, null, handle), "Create descriptor pool");
                pool = handle.get(0);
                var allocation = VkDescriptorSetAllocateInfo.calloc(stack).sType$Default().descriptorPool(pool)
                        .pSetLayouts(stack.longs(descriptorLayout));
                VulkanContext.check(vkAllocateDescriptorSets(context.device(), allocation, handle), "Allocate descriptor set");
                set = handle.get(0);
                for (var descriptor : reflection.descriptors()) if (descriptor.kind() == SpirvReflection.Kind.UNIFORM_BUFFER)
                    uniforms.put(descriptor.name(), new VulkanHostBuffer(context, descriptor.bytes(), VK_BUFFER_USAGE_UNIFORM_BUFFER_BIT));
            } catch (RuntimeException e) { close(); throw e; }
        }

        long framebuffer(MemoryStack stack, List<VulkanImageResource> images, int width, int height) {
            long[] views = images.stream().mapToLong(image -> image.view).toArray();
            if (framebuffer != 0 && Arrays.equals(views, attachmentViews)) return framebuffer;
            if (framebuffer != 0) vkDestroyFramebuffer(context.device(), framebuffer, null);
            framebuffer = 0;
            var info = VkFramebufferCreateInfo.calloc(stack).sType$Default().renderPass(renderPass)
                    .pAttachments(stack.longs(views)).width(width).height(height).layers(1);
            var handle = stack.mallocLong(1);
            VulkanContext.check(vkCreateFramebuffer(context.device(), info, null, handle), "Create pass framebuffer");
            framebuffer = handle.get(0); attachmentViews = views;
            return framebuffer;
        }

        @Override public void close() {
            if (vertices != null) vertices.close();
            for (var buffer : uniforms.values()) buffer.close();
            uniforms.clear();
            if (framebuffer != 0) vkDestroyFramebuffer(context.device(), framebuffer, null);
            if (pool != 0) vkDestroyDescriptorPool(context.device(), pool, null);
            framebuffer = pool = set = 0;
        }
    }

    private static int descriptorType(SpirvReflection.Kind kind) {
        return switch (kind) {
            case SAMPLED_IMAGE -> VK_DESCRIPTOR_TYPE_COMBINED_IMAGE_SAMPLER;
            case UNIFORM_BUFFER -> VK_DESCRIPTOR_TYPE_UNIFORM_BUFFER;
            case STORAGE_BUFFER -> VK_DESCRIPTOR_TYPE_STORAGE_BUFFER;
            case STORAGE_IMAGE -> VK_DESCRIPTOR_TYPE_STORAGE_IMAGE;
        };
    }

    @Override public void close() {
        if (linear != 0) vkDestroySampler(context.device(), linear, null);
        if (nearest != 0) vkDestroySampler(context.device(), nearest, null);
        if (pipeline != 0) vkDestroyPipeline(context.device(), pipeline, null);
        if (renderPass != 0) vkDestroyRenderPass(context.device(), renderPass, null);
        if (layout != 0) vkDestroyPipelineLayout(context.device(), layout, null);
        if (descriptorLayout != 0) vkDestroyDescriptorSetLayout(context.device(), descriptorLayout, null);
        linear = nearest = pipeline = renderPass = layout = descriptorLayout = 0;
    }
}
