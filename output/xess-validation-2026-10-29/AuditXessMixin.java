package salts.qa.mixin;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.betterLostItems.salts_anti_aliasing.client.render.vulkan.xess.XessRuntime;
@Mixin(value=XessRuntime.class,remap=false)
public class AuditXessMixin {
 @ModifyVariable(method="evaluate",at=@At("HEAD"),argsOnly=true,ordinal=0)
 private float x(float value) { return value*salts.qa.AuditCapture.jitterSignX; }
 @ModifyVariable(method="evaluate",at=@At("HEAD"),argsOnly=true,ordinal=1)
 private float y(float value) { return value*salts.qa.AuditCapture.jitterSignY; }
}
