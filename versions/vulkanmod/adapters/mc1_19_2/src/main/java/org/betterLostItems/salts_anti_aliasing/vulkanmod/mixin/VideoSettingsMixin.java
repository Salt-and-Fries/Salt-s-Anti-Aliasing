package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.LegacyButton;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingConfigScreen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
abstract class VideoSettingsMixin extends Screen {
    @Shadow private OptionsList list;
    VideoSettingsMixin(Component title) { super(title); }
    @Inject(method = "init", at = @At("RETURN"))
    private void salts$entry(CallbackInfo info) {
        int y = 32;
        list.updateSize(width, height, y + 25, height - 32);
        addRenderableWidget(LegacyButton.builder(Component.translatable("screen.salts_anti_aliasing.config"),
                b -> minecraft.setScreen(new AntiAliasingConfigScreen(this)))
                .bounds(width / 2 - 155, y, 310, 20).build());
    }
}
