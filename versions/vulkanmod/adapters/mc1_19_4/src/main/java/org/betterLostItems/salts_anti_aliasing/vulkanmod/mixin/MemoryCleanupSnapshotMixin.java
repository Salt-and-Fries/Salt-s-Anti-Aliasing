package org.betterLostItems.salts_anti_aliasing.vulkanmod.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.*;
import it.unimi.dsi.fastutil.objects.ReferenceCollection;
import net.vulkanmod.vulkan.memory.MemoryManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import java.util.ArrayList;
import java.util.function.Consumer;

/** Deletion shrinks the native allocation map; iterate a snapshot instead of its live values. */
@Mixin(value = MemoryManager.class, remap = false)
abstract class MemoryCleanupSnapshotMixin {
    @WrapOperation(method = "freeAllBuffers", at = @At(value = "INVOKE", target =
            "Lit/unimi/dsi/fastutil/objects/ReferenceCollection;forEach(Ljava/util/function/Consumer;)V"))
    private void salts$finishCleanup(ReferenceCollection<Object> allocations, Consumer<Object> release, Operation<Void> original) {
        new ArrayList<>(allocations).forEach(release);
    }
}
