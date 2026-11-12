package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.Vulkan;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.*;
import java.util.List;
@Mixin(value=Vulkan.class,remap=false)
public interface VulkanWindowAccessor {
    @Accessor("debugMessenger") static long salts$debugMessenger(){throw new AssertionError("Mixin accessor");}
    @Accessor("surface") static long salts$surface(){throw new AssertionError("Mixin accessor");}
    @Accessor("swapChainImageFormat") static int salts$format(){throw new AssertionError("Mixin accessor");}
    @Accessor("swapChainImageViews") static List<Long> salts$views(){throw new AssertionError("Mixin accessor");}
    @Accessor("depthImage") static long salts$depth(){throw new AssertionError("Mixin accessor");}
    @Accessor("depthImageView") static long salts$depthView(){throw new AssertionError("Mixin accessor");}
    @Invoker("findDepthFormat") static int salts$depthFormat(){throw new AssertionError("Mixin invoker");}
}
