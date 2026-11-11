package salts.qa.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.*;
@Mixin(value=VulkanSceneSgsrController.class,remap=false)
public class AuditSgsrMixin {
 @Redirect(method="writeTemporalConfig",at=@At(value="INVOKE",target="Lorg/betterLostItems/salts_anti_aliasing/client/render/vulkan/VulkanSceneTemporalController;currentJitterTexelX()F"))
 private float x(VulkanSceneTemporalController t) { return t.currentJitterTexelX()*salts.qa.AuditCapture.jitterSignX; }
 @Redirect(method="writeTemporalConfig",at=@At(value="INVOKE",target="Lorg/betterLostItems/salts_anti_aliasing/client/render/vulkan/VulkanSceneTemporalController;currentJitterTexelY()F"))
 private float y(VulkanSceneTemporalController t) { return t.currentJitterTexelY()*salts.qa.AuditCapture.jitterSignY; }
}
