package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OptionalSodiumClassLoadingTest {
    @Test void entityCaptureLinksWithoutResolvingAnySodiumApiClass() throws Exception {
        var sodiumRequests = new AtomicInteger();
        var loader = new ClassLoader(getClass().getClassLoader()) {
            @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                if (name.startsWith("net.caffeinemc.mods.sodium.")) {
                    sodiumRequests.incrementAndGet();
                    throw new ClassNotFoundException("Sodium deliberately absent: " + name);
                }
                if (name.startsWith(EntityMotionGeometry.class.getName())
                        || name.endsWith("compat.sodium.SodiumMotionVertexConsumer")) {
                    synchronized (getClassLoadingLock(name)) {
                        Class<?> type = findLoadedClass(name);
                        if (type == null) {
                            try (var stream = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                                if (stream == null) throw new ClassNotFoundException(name);
                                byte[] bytes = stream.readAllBytes();
                                type = defineClass(name, bytes, 0, bytes.length);
                            } catch (IOException exception) { throw new ClassNotFoundException(name, exception); }
                        }
                        if (resolve) resolveClass(type);
                        return type;
                    }
                }
                return super.loadClass(name, resolve);
            }
        };
        Class<?> capture = Class.forName(EntityMotionGeometry.class.getName(), false, loader);
        assertTrue(capture.getDeclaredMethods().length > 0); // Forces verification without booting Minecraft.
        assertEquals(0, sodiumRequests.get());
    }
}
