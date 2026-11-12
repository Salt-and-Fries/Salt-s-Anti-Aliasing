package org.betterLostItems.salts_anti_aliasing.client.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingConfigScreen;

/**
 * Implements salts anti aliasing mod menu api behavior for Salt's Anti Aliasing. Compatibility glue
 * code that cooperates with optional mods without making them hard dependencies.
 */
public final class SaltsAntiAliasingModMenuApi implements ModMenuApi {
    /**
     * Returns get mod config screen factory for callers that need to coordinate UI, mixin, or
     * render behavior.
     * @return the requested Minecraft or renderer object
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AntiAliasingConfigScreen::new;
    }
}
