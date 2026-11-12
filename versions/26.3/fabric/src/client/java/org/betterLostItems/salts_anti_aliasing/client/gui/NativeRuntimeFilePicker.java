package org.betterLostItems.salts_anti_aliasing.client.gui;

import static org.lwjgl.sdl.SDLDialog.*;
import static org.lwjgl.sdl.SDLError.SDL_GetError;

import net.minecraft.client.Minecraft;

import org.lwjgl.sdl.SDL_DialogFileCallback;
import org.lwjgl.sdl.SDL_DialogFileFilter;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/** Owns asynchronous SDL dialog memory until its callback has returned to native code. */
final class NativeRuntimeFilePicker {
    record Selection(Path path, String error) {}

    private static final ConcurrentHashMap<Long, Request> PENDING = new ConcurrentHashMap<>();
    private static final AtomicLong IDS = new AtomicLong();
    // One process-lifetime closure. A worker-thread callback must never free its own trampoline.
    private static final SDL_DialogFileCallback CALLBACK =
            SDL_DialogFileCallback.create(
                    (userdata, files, filter) -> {
                        Request request = PENDING.remove(userdata);
                        if (request != null) request.complete(files);
                    });

    private NativeRuntimeFilePicker() {}

    static void choose(Minecraft minecraft, boolean folder, Consumer<Selection> result) {
        Request request = new Request(minecraft, folder, result);
        PENDING.put(request.id, request);
        try {
            request.open();
        } catch (RuntimeException | LinkageError exception) {
            request.close();
            result.accept(
                    new Selection(
                            null,
                            "Unable to open the system file picker: " + exception.getMessage()));
        }
    }

    private static final class Request {
        private final Minecraft minecraft;
        private final boolean folder;
        private final Consumer<Selection> result;
        private final long id = IDS.incrementAndGet();
        private SDL_DialogFileFilter.Buffer filters;
        private ByteBuffer name, pattern;

        Request(Minecraft minecraft, boolean folder, Consumer<Selection> result) {
            this.minecraft = minecraft;
            this.folder = folder;
            this.result = result;
        }

        void open() {
            if (folder)
                SDL_ShowOpenFolderDialog(
                        CALLBACK, id, minecraft.getWindow().handle(), (CharSequence) null, false);
            else {
                name = MemoryUtil.memUTF8("DLSS runtime (select nvngx_dlss.dll)");
                pattern = MemoryUtil.memUTF8("dll");
                filters = SDL_DialogFileFilter.calloc(1);
                filters.get(0).name(name).pattern(pattern);
                SDL_ShowOpenFileDialog(
                        CALLBACK,
                        id,
                        minecraft.getWindow().handle(),
                        filters,
                        (CharSequence) null,
                        false);
            }
        }

        void complete(long files) {
            Path selected = null;
            String error = null;
            try {
                if (files == MemoryUtil.NULL) error = SDL_GetError();
                else {
                    long first = MemoryUtil.memGetAddress(files);
                    if (first != MemoryUtil.NULL) selected = Path.of(MemoryUtil.memUTF8(first));
                }
            } catch (RuntimeException exception) {
                error = exception.getMessage();
            }
            Selection selection = new Selection(selected, error);
            // Native strings must be copied before returning; widgets run on Minecraft's thread.
            minecraft.schedule(
                    () -> {
                        close();
                        result.accept(selection);
                    });
        }

        void close() {
            PENDING.remove(id);
            if (filters != null) {
                filters.free();
                filters = null;
            }
            if (name != null) {
                MemoryUtil.memFree(name);
                name = null;
            }
            if (pattern != null) {
                MemoryUtil.memFree(pattern);
                pattern = null;
            }
        }
    }
}
