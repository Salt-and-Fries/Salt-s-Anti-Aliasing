package org.betterLostItems.salts_anti_aliasing.client.gui;

import org.betterLostItems.salts_anti_aliasing.client.gui.legacy.*;

import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/** Uses Minecraft 26.2's native dialog library without blocking rendering. */
final class NativeRuntimeFilePicker {
    record Selection(Path path, String error) {}
    private NativeRuntimeFilePicker() {}
    static void choose(Minecraft minecraft, boolean folder, Consumer<Selection> result) {
        CompletableFuture.supplyAsync(() -> {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                String selected = folder
                        ? TinyFileDialogs.tinyfd_selectFolderDialog("Select DLSS runtime folder", "")
                        : TinyFileDialogs.tinyfd_openFileDialog("Select nvngx_dlss.dll", "",
                                stack.pointers(stack.UTF8("*.dll")), "DLSS runtime libraries", false);
                return new Selection(selected == null ? null : Path.of(selected), null);
            } catch (RuntimeException | LinkageError exception) {
                return new Selection(null, "Unable to open the system file picker: " + exception.getMessage());
            }
        }, Util.ioPool()).thenAccept(selection -> minecraft.execute(() -> result.accept(selection)));
    }
}
