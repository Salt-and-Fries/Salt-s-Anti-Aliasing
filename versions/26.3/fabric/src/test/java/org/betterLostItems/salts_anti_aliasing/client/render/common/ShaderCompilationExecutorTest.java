package org.betterLostItems.salts_anti_aliasing.client.render.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
final class ShaderCompilationExecutorTest {
    @Test
    void preservesFutureErrorsAndNestedCompilationWithoutBlockingThePool() throws Exception {
        var executor = ShaderCompilationExecutor.forPipelineCompilation(Runnable::run);
        var future = CompletableFuture.supplyAsync(() -> {
            assertTrue(Thread.currentThread().getName().startsWith("Salt's Shader Compiler-"));
            assertEquals(Thread.NORM_PRIORITY, Thread.currentThread().getPriority());
            var nested = ShaderCompilationExecutor.forPipelineCompilation(executor);
            return CompletableFuture.supplyAsync(() -> 42, nested).join();
        }, executor);
        assertEquals(42, future.get(5, TimeUnit.SECONDS));
        var failed = CompletableFuture.supplyAsync(() -> { throw new IllegalArgumentException("compiler failure"); }, executor);
        assertInstanceOf(IllegalArgumentException.class, assertThrows(CompletionException.class, failed::join).getCause());
    }
}
