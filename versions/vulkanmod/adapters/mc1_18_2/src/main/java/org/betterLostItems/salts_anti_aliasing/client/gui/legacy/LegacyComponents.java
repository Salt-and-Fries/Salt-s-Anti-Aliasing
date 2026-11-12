package org.betterLostItems.salts_anti_aliasing.client.gui.legacy;
import net.minecraft.network.chat.*;
/** Text factories for Minecraft releases before Component's static factories. */
public final class LegacyComponents {
    private LegacyComponents(){}
    public static MutableComponent literal(String text){return new TextComponent(text);}
    public static MutableComponent translatable(String key,Object... args){return new TranslatableComponent(key,args);}
    public static MutableComponent empty(){return new TextComponent("");}
}
