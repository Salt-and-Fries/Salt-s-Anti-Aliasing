package org.betterLostItems.salts_anti_aliasing.validation.mixin;

import net.minecraft.client.server.IntegratedServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps vanilla shutdown from blocking the client while Fabric owns the server tick barrier. */
@Mixin(IntegratedServer.class)
abstract class IntegratedServerShutdownMixin {
    @Redirect(method = "halt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/server/IntegratedServer;executeBlocking(Ljava/lang/Runnable;)V"))
    private void salts$queuePlayerCleanup(IntegratedServer server, Runnable cleanup) {
        // Only the disposable validation world uses this mixin. Vanilla stopServer still
        // removes players and saves all worlds; allow Fabric's disconnect tick loop to run.
        server.execute(cleanup);
    }
}
