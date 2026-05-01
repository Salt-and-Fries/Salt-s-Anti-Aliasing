package org.betterLostItems.salts_anti_aliasing.client.compat.modmenu;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingConfigScreen;

/**
 * Documents salts anti aliasing mod menu api behavior for Salt's Anti Aliasing. Optional-mod
 * compatibility glue that avoids hard dependencies.
 */
public final class SaltsAntiAliasingModMenuApi implements ModMenuApi {
    /**
     * Returns get mod config screen factory for callers that need to coordinate UI, mixin, or render
     * behavior.
     * @return get mod config screen factory value produced or selected by this code path
     */
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return AntiAliasingConfigScreen::new;
    }
}
