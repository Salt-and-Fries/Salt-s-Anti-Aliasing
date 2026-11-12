package org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy;
import net.vulkanmod.render.VBO;
import java.util.*;
/** Vanilla relies on graphics-context destruction for its retained sky and cloud VBOs. */
public final class VulkanVboLifetimes {
    private static final Set<VBO> live=Collections.newSetFromMap(new IdentityHashMap<>());
    private VulkanVboLifetimes(){}
    public static synchronized void created(VBO vbo){live.add(vbo);}
    public static synchronized void destroyed(VBO vbo){live.remove(vbo);}
    public static void close(){
        VBO[] remaining;
        synchronized(VulkanVboLifetimes.class){remaining=live.toArray(VBO[]::new);}
        for(var vbo:remaining)vbo.close();
    }
}
