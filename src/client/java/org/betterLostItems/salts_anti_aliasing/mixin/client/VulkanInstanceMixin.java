package org.betterLostItems.salts_anti_aliasing.mixin.client;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssRuntime;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import java.util.Set;

/** Adds optional Streamline instance requirements before the extension-name buffer is allocated. */
@Mixin(targets = "com.mojang.renderpearl.backend.vulkan.VulkanInstance")
public abstract class VulkanInstanceMixin {
    @Shadow @Final private Set<String> enabledExtensions;
    @Shadow private Set<String> getSupportedInstanceExtensions() { throw new AssertionError(); }

    @Redirect(method = "<init>", at = @At(value = "INVOKE", target =
            "Lcom/mojang/renderpearl/backend/vulkan/VulkanInstance;getSupportedInstanceExtensions()Ljava/util/Set;"))
    private Set<String> saltsAntiAliasing$addDlssInstanceExtensions(
            com.mojang.renderpearl.backend.vulkan.VulkanInstance instance) {
        Set<String> supported = getSupportedInstanceExtensions();
        var runtime = DlssRuntime.instance();
        for (String requirement : runtime.vulkanRequirements()) {
            if (!requirement.startsWith("instance:")) continue;
            String extension = requirement.substring("instance:".length());
            if (supported.contains(extension)) enabledExtensions.add(extension);
            else runtime.rejectVulkanRequirements("Missing instance extension " + extension);
        }
        return supported;
    }
}
