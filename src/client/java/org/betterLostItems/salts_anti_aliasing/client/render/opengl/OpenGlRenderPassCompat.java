package org.betterLostItems.salts_anti_aliasing.client.render.opengl;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import com.mojang.blaze3d.vertex.VertexFormat;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

final class OpenGlRenderPassCompat {
    private OpenGlRenderPassCompat() {
    }

    static void bindClampToEdgeTexture(
            RenderPass renderPass,
            String samplerName,
            GpuTextureView textureView,
            FilterMode filterMode
    ) {
        if (tryBindTextureWithSampler(renderPass, samplerName, textureView, filterMode)) {
            return;
        }

        configureLegacyTextureSampler(textureView, filterMode);
        invokeBindSampler(renderPass, samplerName, textureView);
    }

    static void drawFullscreenBlit(RenderPass renderPass) {
        if (tryDrawLegacyFullscreenQuad(renderPass)) {
            return;
        }

        renderPass.draw(0, 3);
    }

    private static boolean tryDrawLegacyFullscreenQuad(RenderPass renderPass) {
        Method getQuadVertexBuffer = method(RenderSystem.class, "getQuadVertexBuffer");
        if (getQuadVertexBuffer == null) {
            return false;
        }

        try {
            RenderSystem.AutoStorageIndexBuffer indexBuffer = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
            GpuBuffer quadVertexBuffer = (GpuBuffer) getQuadVertexBuffer.invoke(null);
            renderPass.setVertexBuffer(0, quadVertexBuffer);
            renderPass.setIndexBuffer(indexBuffer.getBuffer(6), indexBuffer.type());
            renderPass.drawIndexed(0, 0, 6, 1);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | RuntimeException exception) {
            return false;
        }
    }

    private static void configureLegacyTextureSampler(GpuTextureView textureView, FilterMode filterMode) {
        Object texture = textureView.texture();
        invokeIfPresent(texture, "setAddressMode", new Class<?>[]{AddressMode.class}, AddressMode.CLAMP_TO_EDGE);
        invokeIfPresent(texture, "setTextureFilter", new Class<?>[]{FilterMode.class, boolean.class}, filterMode, false);
    }

    private static boolean tryBindTextureWithSampler(
            RenderPass renderPass,
            String samplerName,
            GpuTextureView textureView,
            FilterMode filterMode
    ) {
        Method getSamplerCache = method(RenderSystem.class, "getSamplerCache");
        if (getSamplerCache == null) {
            return false;
        }

        try {
            Object samplerCache = getSamplerCache.invoke(null);
            Method getClampToEdge = firstMethod(
                    samplerCache.getClass(),
                    1,
                    "getClampToEdge",
                    "method_75294"
            );
            if (getClampToEdge == null) {
                return false;
            }

            Object sampler = getClampToEdge.invoke(samplerCache, filterMode);
            Method bindTexture = compatibleBindTextureMethod(renderPass.getClass(), sampler);
            if (bindTexture == null) {
                return false;
            }

            bindTexture.invoke(renderPass, samplerName, textureView, sampler);
            return true;
        } catch (IllegalAccessException | InvocationTargetException | SecurityException exception) {
            return false;
        }
    }

    private static void invokeBindSampler(RenderPass renderPass, String samplerName, GpuTextureView textureView) {
        Method bindSampler = method(renderPass.getClass(), "bindSampler", String.class, GpuTextureView.class);
        if (bindSampler == null) {
            throw new IllegalStateException("No compatible RenderPass texture binding method is available.");
        }

        try {
            bindSampler.invoke(renderPass, samplerName, textureView);
        } catch (IllegalAccessException | InvocationTargetException exception) {
            throw new IllegalStateException("Failed to bind render pass sampler.", exception);
        }
    }

    private static Method compatibleBindTextureMethod(Class<?> renderPassClass, Object sampler) {
        for (Method candidate : renderPassClass.getMethods()) {
            Class<?>[] parameterTypes = candidate.getParameterTypes();
            if (!candidate.getName().equals("bindTexture")
                    || parameterTypes.length != 3
                    || parameterTypes[0] != String.class
                    || !parameterTypes[1].isAssignableFrom(GpuTextureView.class)
                    || !parameterTypes[2].isInstance(sampler)) {
                continue;
            }

            return candidate;
        }

        return null;
    }

    private static Method firstMethod(Class<?> owner, int parameterCount, String... names) {
        for (String name : names) {
            for (Method candidate : owner.getMethods()) {
                if (candidate.getName().equals(name) && candidate.getParameterCount() == parameterCount) {
                    return candidate;
                }
            }
        }

        return null;
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameterTypes) {
        try {
            return owner.getMethod(name, parameterTypes);
        } catch (NoSuchMethodException exception) {
            return null;
        }
    }

    private static void invokeIfPresent(Object owner, String name, Class<?>[] parameterTypes, Object... arguments) {
        Method method = method(owner.getClass(), name, parameterTypes);
        if (method == null) {
            return;
        }

        try {
            method.invoke(owner, arguments);
        } catch (IllegalAccessException | InvocationTargetException ignored) {
        }
    }
}
