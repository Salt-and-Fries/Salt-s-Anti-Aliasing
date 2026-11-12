package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.vulkanmod.config.widget.CustomButtonWidget;
import org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingConfigScreen;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.VulkanSettingsAccess;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** VulkanMod replaces vanilla's Video Settings screen, so expose our complete controls there too. */
@Mixin(targets = "net.vulkanmod.config.OptionScreenV", remap = false)
abstract class VulkanSettingsMixin extends Screen implements VulkanSettingsAccess {
    @Unique private CustomButtonWidget salts$settings;
    VulkanSettingsMixin(Component title) { super(title); }
    @Inject(method = "addButtons", at = @At("TAIL"))
    private void salts$entry(CallbackInfo info) {
        var label = Component.literal("Salt's AA");
        int buttonWidth = font.width(label) + 18;
        final int footerY = height - 27;
        int y = footerY;
        int x = 20;
        // Fit the entry into the existing footer, including narrow GUI scales.
        for (var button : children().stream().filter(net.minecraft.client.gui.components.AbstractWidget.class::isInstance).map(net.minecraft.client.gui.components.AbstractWidget.class::cast).filter(b -> b.y < footerY + 20 && b.y + b.getHeight() > footerY)
                .sorted(java.util.Comparator.comparingInt((net.minecraft.client.gui.components.AbstractWidget b) -> b.x)).toList()) {
            if (x + buttonWidth + 5 <= button.x) break;
            x = Math.max(x, button.x + button.getWidth() + 5);
        }
        if (x + buttonWidth > width - 10) { x = width - buttonWidth - 10; y = 8; }
        salts$settings = new CustomButtonWidget(x, y, buttonWidth, 20, label,
                button -> minecraft.setScreen(new AntiAliasingConfigScreen(this)));
        addRenderableWidget(salts$settings);
    }
    public int salts$settingsX() { return ((net.minecraft.client.gui.components.AbstractWidget) salts$settings).x; }
    public int salts$settingsY() { return ((net.minecraft.client.gui.components.AbstractWidget) salts$settings).y; }
}
