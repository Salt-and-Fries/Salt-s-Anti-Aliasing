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
            "Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;rasterizationSamples(I)Lorg/lwjgl/vulkan/VkPipelineMultisampleStateCreateInfo;"))
    private VkPipelineMultisampleStateCreateInfo salts$samples(VkPipelineMultisampleStateCreateInfo info, int original,
            Operation<VkPipelineMultisampleStateCreateInfo> operation, @Local(argsOnly = true) PipelineState state) {
        var access = (PipelineStateAccessor) state;
        if (!(access.salts$renderPass() instanceof MultisampleWorld.Pass pass)) return operation.call(info, original);
        String name = ((Pipeline) (Object) this).name.toLowerCase(java.util.Locale.ROOT);
        boolean cutout = name.contains("cutout") || name.contains("terrain");
        info.alphaToCoverageEnable(pass.alphaToCoverage && cutout && !PipelineState.BlendState.enable(access.salts$blendState()));
        info.sampleShadingEnable(pass.hand).minSampleShading(pass.hand ? 1f : 0f);
        return operation.call(info, pass.world.samples());
    }
}
