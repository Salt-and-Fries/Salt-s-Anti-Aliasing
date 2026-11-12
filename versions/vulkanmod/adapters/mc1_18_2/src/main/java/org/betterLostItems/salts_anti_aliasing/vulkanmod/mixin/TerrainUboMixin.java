package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.google.gson.*;
import net.vulkanmod.vulkan.shader.Pipeline;
import org.betterLostItems.salts_anti_aliasing.vulkanmod.legacy.LegacyTerrainShaders;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
@Mixin(value=Pipeline.Builder.class,remap=false)
abstract class TerrainUboMixin {
    @Shadow @Final private String shaderPath;
    @ModifyVariable(method="parseUboNode",at=@At("HEAD"),argsOnly=true)
    private JsonElement salts$mipUniform(JsonElement element) {
        var node=element.getAsJsonObject();
        if(!LegacyTerrainShaders.terrain(shaderPath)||!node.get("type").getAsString().equals("fragment"))return element;
        var field=new JsonObject();field.addProperty("name","SaltsMipBias");field.addProperty("type","float");field.addProperty("count",1);
        var values=new JsonArray();values.add(0);field.add("values",values);node.getAsJsonArray("fields").add(field);
        return element;
    }
}
