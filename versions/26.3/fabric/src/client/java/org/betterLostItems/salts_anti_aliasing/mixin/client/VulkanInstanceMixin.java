package org.betterLostItems.salts_anti_aliasing.mixin.client;

import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.dlss.DlssRuntime;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import java.util.Set;

/** Adds optional Streamline instance requirements before the extension-name buffer is allocated. */
@Mixin(targets = "com.mojang.renderpearl.backend.vulkan.VulkanInstance")
public abstract class VulkanInstanceMixin {
    @Shadow @Final private Set<String> enabledExtensions;
    @Shadow private Set<String> getSupportedInstanceExtensions() { throw new AssertionError(); }

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target =
            "Lorg/lwjgl/vulkan/VkApplicationInfo;apiVersion(I)Lorg/lwjgl/vulkan/VkApplicationInfo;"), index = 0)
    private int saltsAntiAliasing$xessApiVersion(int requested) {
        var xess = org.betterLostItems.salts_anti_aliasing.client.render.vulkan.xess.XessRuntime.instance();
        int required = requested;
        for (String requirement : xess.instanceRequirements())
            if (requirement.startsWith("api:")) required = Math.max(required, Integer.parseInt(requirement.substring(4)));
        if (required == requested) return requested;
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var supported = stack.ints(org.lwjgl.vulkan.VK10.VK_API_VERSION_1_0);
            if (org.lwjgl.vulkan.VK.getFunctionProvider().getFunctionAddress("vkEnumerateInstanceVersion") != 0
                    && org.lwjgl.vulkan.VK11.vkEnumerateInstanceVersion(supported) == org.lwjgl.vulkan.VK10.VK_SUCCESS
                    && supported.get(0) >= required) return required;
        }
        xess.reject("The Vulkan loader does not support XeSS's required API version");
        return requested;
    }

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
        var xess = org.betterLostItems.salts_anti_aliasing.client.render.vulkan.xess.XessRuntime.instance();
        for (String requirement : xess.instanceRequirements()) {
            if (requirement.startsWith("instance:")) {
                String extension = requirement.substring(9);
                if (supported.contains(extension)) enabledExtensions.add(extension);
                else xess.reject("Missing instance extension " + extension);
            }
        }
        return supported;
    }
}
