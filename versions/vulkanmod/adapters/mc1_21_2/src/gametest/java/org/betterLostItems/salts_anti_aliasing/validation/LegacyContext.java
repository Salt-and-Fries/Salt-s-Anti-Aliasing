package org.betterLostItems.salts_anti_aliasing.validation;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.betterLostItems.salts_anti_aliasing.validation.mixin.CreateWorldAccessor;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/** Development-only client driver for Fabric releases predating client game tests. */
public final class LegacyContext {
    private static final Object TICKS = new Object();
    private static long ticks;
    public static void tick() { synchronized (TICKS) { ticks++; TICKS.notifyAll(); } }

    public void runOnClient(Consumer<Minecraft> action) {
        var client = Minecraft.getInstance();
        try { client.submit(() -> action.accept(client)).get(120, TimeUnit.SECONDS); }
        catch (Exception error) { throw new AssertionError("Client operation failed", error); }
    }
    public void waitTicks(int count) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        synchronized (TICKS) {
            long target = ticks + count;
            while (ticks < target) {
                if (System.nanoTime() >= deadline) throw new AssertionError("Client stopped ticking");
                try { TICKS.wait(1000); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); }
            }
        }
    }
    public void waitFor(Predicate<Minecraft> predicate) { waitFor(predicate, 2400); }
    public void waitFor(Predicate<Minecraft> predicate, int timeoutTicks) {
        for (int i = 0; i < timeoutTicks; i++) {
            var satisfied = new java.util.concurrent.atomic.AtomicBoolean();
            runOnClient(client -> satisfied.set(predicate.test(client)));
            if (satisfied.get()) return;
            waitTicks(1);
        }
        throw new AssertionError("Client condition timed out after " + timeoutTicks + " ticks");
    }
    public void setScreen(Supplier<Screen> screen) { runOnClient(client -> client.setScreen(screen.get())); }
    public void waitForScreen(Class<? extends Screen> type) { waitFor(client -> type.isInstance(client.screen)); }
    public WorldBuilder worldBuilder() { return new WorldBuilder(); }

    public final class WorldBuilder {
        public World create() {
            waitFor(client -> client.getOverlay() == null);
            runOnClient(client -> CreateWorldScreen.openFresh(client, new TitleScreen()));
            waitForScreen(CreateWorldScreen.class);
            runOnClient(client -> {
                var screen = (CreateWorldScreen) client.screen;
                var state = screen.getUiState();
                state.setName("Salts validation " + System.currentTimeMillis());
                state.setSeed("739105");
                state.setGameMode(WorldCreationUiState.SelectedGameMode.CREATIVE);
                state.setAllowCommands(true);
                state.setGenerateStructures(false);
                var flat = state.getNormalPresetList().stream()
                        .filter(entry -> entry.preset() != null && entry.preset().is(WorldPresets.FLAT))
                        .findFirst().orElseThrow(() -> new AssertionError("Flat preset unavailable"));
                state.setWorldType(flat);
                ((CreateWorldAccessor) screen).salts$create();
            });
            waitFor(client -> client.level != null && client.player != null && client.getSingleplayerServer() != null, 6000);
            waitTicks(30);
            var world = new World();
            world.getServer().runCommand("gamerule doMobSpawning false");
            world.getServer().runCommand("gamerule doDaylightCycle false");
            world.getServer().runCommand("gamerule doWeatherCycle false");
            return world;
        }
    }
    public final class World implements AutoCloseable {
        public Server getServer() { return new Server(); }
        @Override public void close() {
            runOnClient(client -> {
                // Vanilla's pause-menu flow disconnects the level before unloading the client.
                if (client.level != null) client.level.disconnect();
                client.disconnect(new TitleScreen());
            });
            waitFor(client -> client.level == null && client.getSingleplayerServer() == null);
        }
    }
    public final class Server {
        public void runCommand(String command) {
            var server = Minecraft.getInstance().getSingleplayerServer();
            if (server == null) throw new AssertionError("No integrated test server");
            try {
                server.submit(() -> server.getCommands().performPrefixedCommand(
                        server.createCommandSourceStack().withPermission(4).withSuppressedOutput(), command)).get(120, TimeUnit.SECONDS);
            } catch (Exception error) { throw new AssertionError("Test command failed: " + command, error); }
        }
    }
}
