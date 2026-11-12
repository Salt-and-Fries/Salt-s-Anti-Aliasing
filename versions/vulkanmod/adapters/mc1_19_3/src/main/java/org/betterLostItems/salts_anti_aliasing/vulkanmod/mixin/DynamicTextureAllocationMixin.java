package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.vulkanmod.interfaces.VAbstractTextureI;
import net.vulkanmod.gl.TextureMap;
import net.vulkanmod.vulkan.texture.VulkanImage;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
/** Cover constructors missed by this release's legacy constructor-selector redirect. */
@Mixin(DynamicTexture.class)
abstract class DynamicTextureAllocationMixin {
    @Shadow private NativeImage pixels;
    @Unique private void salts$ensureAllocation() {
        var texture=(VAbstractTextureI)this;
        if(pixels==null||texture.getVulkanImage()!=null)return;
        var image=new VulkanImage(pixels.getWidth(),pixels.getHeight());
        texture.setVulkanImage(image);texture.bindTexture();texture.setId(TextureMap.getId(image));
    }
    @Inject(method={"<init>(Lcom/mojang/blaze3d/platform/NativeImage;)V","<init>(IIZ)V"},at=@At("RETURN"))
    private void salts$construct(CallbackInfo info) {
        if(RenderSystem.isOnRenderThreadOrInit())salts$ensureAllocation();
        else RenderSystem.recordRenderCall(this::salts$ensureAllocation);
    }
    @Inject(method="upload",at=@At("HEAD"))
    private void salts$upload(CallbackInfo info){salts$ensureAllocation();}
}
