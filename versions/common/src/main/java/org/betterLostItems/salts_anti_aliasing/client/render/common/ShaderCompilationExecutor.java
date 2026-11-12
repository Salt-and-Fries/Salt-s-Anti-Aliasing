package org.betterLostItems.salts_anti_aliasing.client.render.common;

import java.util.Locale;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Keeps Shaderc's native parser and its Java include callbacks on a sufficiently large stack. */
public final class ShaderCompilationExecutor {
    private static final long STACK_BYTES = (Boolean.getBoolean("salts.shaderCompiler.smallStackControl") ? 1L : 4L) * 1024 * 1024;
    private static final ThreadLocal<Boolean> COMPILING = ThreadLocal.withInitial(() -> false);
    private static final boolean ENABLED = Runtime.version().feature() >= 25
            && System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

    private ShaderCompilationExecutor() {}

    private static final class Pool {
        static final ThreadPoolExecutor INSTANCE = createPool();
    }

    private static ThreadPoolExecutor createPool() {
        int threads = Math.max(1, Math.min(8, Runtime.getRuntime().availableProcessors() - 1));
        var nextThread = new AtomicInteger();
        var pool = new ThreadPoolExecutor(threads, threads, 30, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(), task -> {
                    var thread = new Thread(null, () -> {
                        COMPILING.set(true);
                        try { task.run(); } finally { COMPILING.remove(); }
                    }, "Salt's Shader Compiler-" + nextThread.getAndIncrement(), STACK_BYTES);
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY);
                    return thread;
                });
        pool.allowCoreThreadTimeOut(true);
        return pool;
    }

    public static Executor forPipelineCompilation(Executor original) {
        if (!ENABLED) return original;
        // Nested pipeline requests can wait for their futures. Keep them on the already
        // protected stack rather than queuing work behind all occupied compiler threads.
        return COMPILING.get() ? Runnable::run : Pool.INSTANCE;
    }
}
