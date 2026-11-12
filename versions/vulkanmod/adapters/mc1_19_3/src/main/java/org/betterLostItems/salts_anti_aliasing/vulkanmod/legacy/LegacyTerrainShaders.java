package org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy;
public final class LegacyTerrainShaders {
    private LegacyTerrainShaders(){}
    public static boolean terrain(String path) {
        return java.util.Set.of("core/rendertype_solid","core/rendertype_cutout_mipped","core/rendertype_cutout",
                "core/rendertype_translucent","core/rendertype_tripwire").contains(path);
    }
}
