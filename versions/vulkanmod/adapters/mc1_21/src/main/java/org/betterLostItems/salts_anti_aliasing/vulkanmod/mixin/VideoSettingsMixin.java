package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;
import org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingConfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VideoSettingsScreen.class)
abstract class VideoSettingsMixin extends OptionsSubScreen {
    VideoSettingsMixin(Screen parent, Options options, Component title) { super(parent, options, title); }
    @Inject(method = "addOptions", at = @At("RETURN"))
    private void salts$settings(CallbackInfo info) {
        list.addSmall(Button.builder(Component.translatable("screen.salts_anti_aliasing.config"),
                button -> minecraft.setScreen(new AntiAliasingConfigScreen(this))).width(150).build(), null);
    }
}
