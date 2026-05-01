package org.betterLostItems.salts_anti_aliasing;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main Fabric mod initializer that exposes the shared mod id and logger used by common and client-side
 * code.
 */
public final class SaltsAntiAliasing implements ModInitializer {
    public static final String MOD_ID = "salts_anti_aliasing";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    /**
     * Initializes the common mod entrypoint; client-side renderer setup is handled by the client
     * initializer.
     */
    @Override
    public void onInitialize() {
        LOGGER.info("Initializing Salt's Anti Aliasing foundation");
    }
}
