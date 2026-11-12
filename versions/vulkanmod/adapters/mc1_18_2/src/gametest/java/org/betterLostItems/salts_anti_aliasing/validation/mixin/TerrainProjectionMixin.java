package org.betterLostItems.salts_anti_aliasing.validation.mixin;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Matrix4f;
import net.minecraft.client.renderer.RenderType;
import org.betterLostItems.salts_anti_aliasing.validation.TerrainProjectionChecks;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyMatrices;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** This projection is passed unchanged to VRenderSystem.applyMVP by the published renderer. */
@Mixin(targets="net.vulkanmod.render.chunk.WorldRenderer",remap=false)
abstract class TerrainProjectionMixin {
    @Inject(method="renderChunkLayer",at=@At("HEAD"))
    private void salts$projection(RenderType layer,PoseStack view,double x,double y,double z,Matrix4f projection,CallbackInfo ci){
        TerrainProjectionChecks.observe(LegacyMatrices.read(projection));
    }
}
