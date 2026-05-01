package org.betterLostItems.salts_anti_aliasing.client.compat;

import net.fabricmc.loader.api.FabricLoader;

public final class LoadedMods {
    private static final boolean SODIUM_LOADED = FabricLoader.getInstance().isModLoaded("sodium");

    private LoadedMods() {
    }

    public static boolean sodiumLoaded() {
        return SODIUM_LOADED;
    }
}
