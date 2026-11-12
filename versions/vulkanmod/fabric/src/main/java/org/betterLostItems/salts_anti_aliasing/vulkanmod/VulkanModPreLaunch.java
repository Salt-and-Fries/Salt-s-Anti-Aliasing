package org.betterLostItems.salts_anti_aliasing.vulkanmod;

import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;

/** Old LWJGL instance creation puts the current driver's extension table on MemoryStack. */
public final class VulkanModPreLaunch implements PreLaunchEntrypoint {
    @Override public void onPreLaunch() {
        String property = "org.lwjgl.system.stackSize";
        int configured;
        try { configured = Integer.parseInt(System.getProperty(property, "64")); }
        catch (NumberFormatException ignored) { configured = 64; }
        if (configured < 512) System.setProperty(property, "512");
    }
}
