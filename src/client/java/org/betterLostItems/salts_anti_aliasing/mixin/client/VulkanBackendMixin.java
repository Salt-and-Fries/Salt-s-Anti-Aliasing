package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.mojang.blaze3d.shaders.GpuDebugOptions;
import com.mojang.blaze3d.shaders.ShaderSource;
import com.mojang.blaze3d.systems.BackendCreationException;
import com.mojang.blaze3d.systems.GpuDevice;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssRuntime;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Gives the optional DLSS bridge a chance to initialize before Vulkan handles are created.
 */
@Mixin(targets = "com.mojang.blaze3d.vulkan.VulkanBackend")
public abstract class VulkanBackendMixin {
    @Inject(method = "createDevice", at = @At("HEAD"))
    private void saltsAntiAliasing$preInitializeDlss(
            long window,
            ShaderSource shaderSource,
            GpuDebugOptions debugOptions,
            Runnable criticalShaderLoader,
            CallbackInfoReturnable<GpuDevice> callbackInfo
    ) throws BackendCreationException {
        DlssRuntime.instance().preInitializeFromEnvironment();
    }
}
