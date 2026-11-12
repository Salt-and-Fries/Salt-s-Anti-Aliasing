package org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy;

import static org.lwjgl.vulkan.VK10.*;

public final class LegacyTextures {
    private LegacyTextures() {}
    public static int aspect(int format) {
        return switch (format) {
            case VK_FORMAT_D32_SFLOAT, VK_FORMAT_D16_UNORM -> VK_IMAGE_ASPECT_DEPTH_BIT;
            case VK_FORMAT_D24_UNORM_S8_UINT, VK_FORMAT_D32_SFLOAT_S8_UINT, VK_FORMAT_D16_UNORM_S8_UINT ->
                    VK_IMAGE_ASPECT_DEPTH_BIT | VK_IMAGE_ASPECT_STENCIL_BIT;
            default -> VK_IMAGE_ASPECT_COLOR_BIT;
        };
    }
}
