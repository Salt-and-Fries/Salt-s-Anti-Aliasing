package org.betterLostItems.salts_anti_aliasing.client.render.opengl;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Caches old-format 1.21.1 post chains and keeps them resized to Minecraft's main target.
 */
final class OpenGlPostChainManager {
    private static final Map<ResourceLocation, ChainHandle> CHAINS = new HashMap<>();

    private OpenGlPostChainManager() {
    }

    static PostChain get(Minecraft minecraft, ResourceLocation effectId) throws IOException {
        RenderTarget mainTarget = minecraft.getMainRenderTarget();
        ChainHandle handle = CHAINS.get(effectId);
        if (handle == null || handle.mainTarget != mainTarget) {
            close(effectId);
            PostChain postChain = new PostChain(
                    minecraft.getTextureManager(),
                    minecraft.getResourceManager(),
                    mainTarget,
                    postChainLocation(effectId)
            );
            handle = new ChainHandle(postChain, mainTarget, -1, -1);
            CHAINS.put(effectId, handle);
        }

        if (handle.width != mainTarget.width || handle.height != mainTarget.height) {
            handle.postChain.resize(mainTarget.width, mainTarget.height);
            handle.width = mainTarget.width;
            handle.height = mainTarget.height;
        }

        return handle.postChain;
    }

    static void closeAll() {
        for (ChainHandle handle : CHAINS.values()) {
            handle.postChain.close();
        }
        CHAINS.clear();
    }

    private static void close(ResourceLocation effectId) {
        ChainHandle oldHandle = CHAINS.remove(effectId);
        if (oldHandle != null) {
            oldHandle.postChain.close();
        }
    }

    private static ResourceLocation postChainLocation(ResourceLocation effectId) {
        return effectId.withPath(path -> "shaders/post/" + path + ".json");
    }

    private static final class ChainHandle {
        private final PostChain postChain;
        private final RenderTarget mainTarget;
        private int width;
        private int height;

        private ChainHandle(PostChain postChain, RenderTarget mainTarget, int width, int height) {
            this.postChain = postChain;
            this.mainTarget = mainTarget;
            this.width = width;
            this.height = height;
        }
    }
}
