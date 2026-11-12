package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import java.util.List;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.vulkanmod.config.gui.widget.VButtonWidget;
import org.betterLostItems.salts_anti_aliasing.client.gui.AntiAliasingConfigScreen;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.VulkanSettingsAccess;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** VulkanMod replaces vanilla's Video Settings screen, so expose our complete controls there too. */
@Mixin(targets = "net.vulkanmod.config.gui.VOptionScreen", remap = false)
abstract class VulkanSettingsMixin extends Screen implements VulkanSettingsAccess {
    @Shadow @Final private List<VButtonWidget> buttons;
    @Unique private VButtonWidget salts$settings;
    VulkanSettingsMixin(Component title) { super(title); }
    @Inject(method = "addButtonsWithSearchBar", at = @At("TAIL"))
    private void salts$entry(CallbackInfo info) {
        var label = Component.literal("Salt's AA");
        int buttonWidth = font.width(label) + 18;
        final int footerY = height - 27;
        int y = footerY;
        int x = 20;
        // Fit the entry into the existing footer, including narrow GUI scales.
        for (var button : buttons.stream().filter(b -> b.getY() < footerY + 20 && b.getY() + b.getHeight() > footerY)
                .sorted(java.util.Comparator.comparingInt(VButtonWidget::getX)).toList()) {
            if (x + buttonWidth + 5 <= button.getX()) break;
            x = Math.max(x, button.getX() + button.getWidth() + 5);
        }
        if (x + buttonWidth > width - 10) { x = width - buttonWidth - 10; y = 8; }
        salts$settings = new VButtonWidget(x, y, buttonWidth, 20, label,
                button -> minecraft.setScreen(new AntiAliasingConfigScreen(this)));
        buttons.add(salts$settings);
        addWidget(salts$settings);
    }
    public int salts$settingsX() { return salts$settings.getX(); }
    public int salts$settingsY() { return salts$settings.getY(); }
}
