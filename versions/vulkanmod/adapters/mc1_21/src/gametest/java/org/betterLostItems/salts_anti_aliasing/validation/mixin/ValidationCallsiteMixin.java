package org.betterLostItems.salts_anti_aliasing.validation.mixin;

import net.vulkanmod.vulkan.Vulkan;
import org.lwjgl.vulkan.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import static org.lwjgl.vulkan.EXTDebugUtils.*;

/** Adds a Java callsite to layout diagnostics; never included in distributables. */
@Mixin(value = Vulkan.class, remap = false)
abstract class ValidationCallsiteMixin {
    @Shadow private static VkInstance instance;
    @Unique private static VkDebugUtilsMessengerCallbackEXT salts$callback;
    @Unique private static long salts$messenger;
    @Inject(method = "createInstance", at = @At("RETURN"))
    private static void salts$install(CallbackInfo info) {
        if (!instance.getCapabilities().VK_EXT_debug_utils) return;
        salts$callback = VkDebugUtilsMessengerCallbackEXT.create((severity, types, data, user) -> {
            var message = VkDebugUtilsMessengerCallbackDataEXT.create(data).pMessageString();
            if (message.contains("VUID-vkCmdDraw-None-09600")) {
                System.err.println("SALTS_VALIDATION_CALLSITE " + message);
                new Throwable("Vulkan layout validation callsite").printStackTrace();
            }
            return 0;
        });
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            var create = VkDebugUtilsMessengerCreateInfoEXT.calloc(stack).sType$Default()
                    .messageSeverity(VK_DEBUG_UTILS_MESSAGE_SEVERITY_ERROR_BIT_EXT)
                    .messageType(VK_DEBUG_UTILS_MESSAGE_TYPE_VALIDATION_BIT_EXT).pfnUserCallback(salts$callback);
            var handle = stack.mallocLong(1);
            if (vkCreateDebugUtilsMessengerEXT(instance, create, null, handle) != 0)
                throw new AssertionError("Cannot install validation diagnostic callback");
            salts$messenger = handle.get(0);
        }
    }
    @Inject(method = "cleanUp", at = @At("HEAD"))
    private static void salts$remove(CallbackInfo info) {
        if (salts$messenger != 0) vkDestroyDebugUtilsMessengerEXT(instance, salts$messenger, null);
        if (salts$callback != null) salts$callback.free();
        salts$messenger = 0; salts$callback = null;
    }
}
