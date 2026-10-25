package org.betterLostItems.salts_anti_aliasing.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.client.gui.screens.options.VideoSettingsScreen;
import net.minecraft.network.chat.Component;

import org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingConfigScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.Arrays;

/** One entry point; all method controls live in the dedicated screen. */
@Mixin(VideoSettingsScreen.class)
public abstract class VideoSettingsScreenMixin extends OptionsSubScreen {
    protected VideoSettingsScreenMixin(Screen parent, Options options, Component title) {
        super(parent, options, title);
    }

    @WrapOperation(
            method = "addOptions",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/gui/components/OptionsList;addSmall([Lnet/minecraft/client/OptionInstance;)V",
                            ordinal = 1))
    private void saltsAntiAliasing$addSettings(
            OptionsList list, OptionInstance<?>[] qualityOptions, Operation<Void> original) {
        Button settings =
                Button.builder(
                                Component.translatable("screen.salts_anti_aliasing.config"),
                                button ->
                                        minecraft.setScreenAndShow(
                                                new AntiAliasingConfigScreen(this)))
                        .width(150)
                        .tooltip(
                                Tooltip.create(
                                        Component.translatable(
                                                "screen.salts_anti_aliasing.config.tooltip")))
                        .build();
        // Vanilla 26.3 ends this section with an unpaired Weather Effect Radius option.
        if (qualityOptions.length % 2 == 1
                && qualityOptions[qualityOptions.length - 1] == options.weatherRadius()) {
            original.call(list, Arrays.copyOf(qualityOptions, qualityOptions.length - 1));
            var weather = qualityOptions[qualityOptions.length - 1];
            list.addSmall(weather.createButton(options), weather, settings);
        } else {
            original.call(list, qualityOptions);
            list.addSmall(settings, null);
        }
    }
}
