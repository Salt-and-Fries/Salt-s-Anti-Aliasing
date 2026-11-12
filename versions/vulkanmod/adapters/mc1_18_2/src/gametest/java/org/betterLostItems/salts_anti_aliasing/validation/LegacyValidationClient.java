package org.betterLostItems.salts_anti_aliasing.validation;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Runs the same feature sweep in a disposable integrated world and records a required result. */
public final class LegacyValidationClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> LegacyContext.tick());
        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            new Thread(() -> {
                var context = new LegacyContext();
                Throwable failure = null;
                try {
                    context.runOnClient(mc -> {
                        mc.options.pauseOnLostFocus = false;
                        mc.getTutorial().setStep(net.minecraft.client.tutorial.TutorialSteps.NONE);
                        mc.options.renderDistance=8;
                        mc.options.enableVsync=false;
                        mc.options.framerateLimit=120;
                    });
                    new VulkanModSmokeTest().runTest(context);
                } catch (Throwable error) {
                    failure = error;
                    org.slf4j.LoggerFactory.getLogger("salts_validation").error("Legacy client validation failed", error);
                }
                var result = new com.google.gson.JsonObject();
                result.addProperty("status", failure == null ? "PASS" : "FAIL");
                if (failure != null) result.addProperty("failure", failure.toString());
                try {
                    java.nio.file.Files.writeString(net.fabricmc.loader.api.FabricLoader.getInstance().getGameDir()
                            .resolve("salts-validation-result.json"), result.toString());
                } catch (java.io.IOException error) { throw new AssertionError("Cannot write validation result", error); }
                finally { context.runOnClient(mc -> mc.stop()); }
            }, "Salt's legacy validation").start();
        });
    }
}
