package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.llamalad7.mixinextras.injector.wrapoperation.*;
import com.llamalad7.mixinextras.sugar.Local;
import net.vulkanmod.vulkan.Pipeline;
import net.vulkanmod.vulkan.Pipeline.PipelineState;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyPipelineState;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
@Mixin(value = Pipeline.class, remap = false)
abstract class MultisamplePipelineMixin {
    @WrapOperation(method = "createGraphicsPipeline", at = @At(value = "INVOKE", target = "Lorg/lwjgl/vulkan/VK10;vkCreateGraphicsPipelines(Lorg/lwjgl/vulkan/VkDevice;JLorg/lwjgl/vulkan/VkGraphicsPipelineCreateInfo$Buffer;Lorg/lwjgl/vulkan/VkAllocationCallbacks;Ljava/nio/LongBuffer;)I"))
    private int salts$attachments(VkDevice device, long cache, VkGraphicsPipelineCreateInfo.Buffer infos,
            VkAllocationCallbacks allocator, java.nio.LongBuffer result, Operation<Integer> original,
            @Local(argsOnly = true) PipelineState state) {
        var signature = ((LegacyPipelineState) state).salts$signature();
        var samples = infos.pMultisampleState();
        samples.rasterizationSamples(signature.samples())
                .alphaToCoverageEnable(signature.coverage() && !((PipelineStateAccessor) state).salts$blendState().enabled)
                .sampleShadingEnable(signature.hand()).minSampleShading(signature.hand() ? 1f : 0f);
        if (signature.color() != 0) {
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                var rendering = VkPipelineRenderingCreateInfo.calloc(stack).sType$Default()
                        .pColorAttachmentFormats(stack.ints(signature.color())).depthAttachmentFormat(signature.depth());
                infos.renderPass(0).pNext(rendering.address());
                return original.call(device, cache, infos, allocator, result);
            }
        }
        return original.call(device, cache, infos, allocator, result);
    }
}
