package org.betterLostItems.salts_anti_aliasing;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SaltsAntiAliasing implements ModInitializer {
    public static final String MOD_ID = "salts_anti_aliasing";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Salt's Anti Aliasing foundation");
    }
}
