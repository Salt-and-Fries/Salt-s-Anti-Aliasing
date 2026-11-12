package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.vulkanmod.vulkan.shader.GraphicsPipeline;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.PipelineState;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.MultisampleWorld;
import org.lwjgl.vulkan.VkPipelineMultisampleStateCreateInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(value = GraphicsPipeline.class, remap = false)
abstract class MultisamplePipelineMixin {
    @WrapOperation(method = "createGraphicsPipeline", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VK10;vkCreateGraphicsPipelines(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkGraphicsPipelineCreateInfo$Buffer;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"))
    private int salts$dynamicPipeline(org.lwjgl.vulkan.VkDevice device, long cache,
            org.lwjgl.vulkan.VkGraphicsPipelineCreateInfo.Buffer infos, org.lwjgl.vulkan.VkAllocationCallbacks allocator,
            java.nio.LongBuffer result, Operation<Integer> operation, @Local(argsOnly = true) PipelineState state) {
        var pass = ((PipelineStateAccessor) state).salts$renderPass();
        if (!(pass instanceof MultisampleWorld.Pass msaa)) return operation.call(device, cache, infos, allocator, result);
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var rendering = org.lwjgl.vulkan.VkPipelineRenderingCreateInfo.calloc(stack).sType$Default()
                    .pNext(infos.pNext()).pColorAttachmentFormats(stack.ints(msaa.world.colorFormat()))
                    .depthAttachmentFormat(msaa.world.depthFormat())
                    .stencilAttachmentFormat(0);
            infos.renderPass(0).pNext(rendering.address());
            return operation.call(device, cache, infos, allocator, result);
        }
    }

    @WrapOperation(method = "createGraphicsPipeline", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;rasterizationSamples(I)Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;"))
    private VkPipelineMultisampleStateCreateInfo salts$samples(VkPipelineMultisampleStateCreateInfo info, int original,
            Operation<VkPipelineMultisampleStateCreateInfo> operation, @Local(argsOnly = true) PipelineState state) {
        var access = (PipelineStateAccessor) state;
        if (!(access.salts$renderPass() instanceof MultisampleWorld.Pass pass)) return operation.call(info, original);
        String name = ((Pipeline) (Object) this).name.toLowerCase(java.util.Locale.ROOT);
        boolean cutout = name.contains("cutout") || name.contains("terrain");
        info.alphaToCoverageEnable(pass.alphaToCoverage && cutout && !access.salts$blendState().enabled);
        info.sampleShadingEnable(pass.hand).minSampleShading(pass.hand ? 1f : 0f);
        return operation.call(info, pass.world.samples());
    }
}
