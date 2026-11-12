package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import net.vulkanmod.vulkan.ShaderSPIRVUtils;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu.TerrainTextureDetail;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyTerrainShaders;
import com.llamalad7.mixinextras.sugar.Local;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=ShaderSPIRVUtils.class,remap=false)
abstract class TerrainShaderMixin {
    @ModifyVariable(method="compileShader",at=@At("HEAD"),argsOnly=true,ordinal=1)
    private static String salts$source(String source,@Local(argsOnly=true,ordinal=0)String filename) {
        for(String name:new String[]{"rendertype_solid","rendertype_cutout_mipped","rendertype_cutout","rendertype_translucent","rendertype_tripwire"})
            if(filename.endsWith("/core/"+name+".fsh"))return TerrainTextureDetail.patchClassic(source);
        return source;
    }
}
