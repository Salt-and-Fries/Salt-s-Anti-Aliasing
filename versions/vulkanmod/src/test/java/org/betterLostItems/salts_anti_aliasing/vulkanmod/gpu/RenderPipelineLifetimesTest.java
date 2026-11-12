package org.betterLostItems.salts_anti_aliasing.vulkanmod.gpu;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RenderPipelineLifetimesTest {
    @AfterEach void cleanup() { RenderPipelineLifetimes.close(); }
    @Test void reloadDefersOldPipelineAndShutdownDestroysReplacement() {
        var retired = new AtomicInteger(); var destroyed = new AtomicInteger(); var owner = new Object();
        RenderPipelineLifetimes.replaced(owner, new Object(), retired::incrementAndGet, () -> fail("Retired pipeline destroyed twice"));
        RenderPipelineLifetimes.replaced(owner, new Object(), () -> fail("Live replacement retired"), destroyed::incrementAndGet);
        assertEquals(1, retired.get()); assertEquals(0, destroyed.get());
        RenderPipelineLifetimes.close(); assertEquals(1, destroyed.get());
    }
    @Test void repeatedAssignmentKeepsThePipelineAlive() {
        var retired = new AtomicInteger(); var destroyed = new AtomicInteger(); var owner = new Object(); var pipeline = new Object();
        RenderPipelineLifetimes.replaced(owner, pipeline, retired::incrementAndGet, destroyed::incrementAndGet);
        RenderPipelineLifetimes.replaced(owner, pipeline, retired::incrementAndGet, destroyed::incrementAndGet);
        assertEquals(0, retired.get()); RenderPipelineLifetimes.close(); assertEquals(1, destroyed.get());
    }
    @Test void sharedPipelineSurvivesUntilItsLastOwnerReleasesIt() {
        var retired = new AtomicInteger(); var pipeline = new Object(); var a = new Object(); var b = new Object();
        RenderPipelineLifetimes.replaced(a, pipeline, retired::incrementAndGet, () -> fail("Released pipeline destroyed twice"));
        RenderPipelineLifetimes.replaced(b, pipeline, retired::incrementAndGet, () -> fail("Released pipeline destroyed twice"));
        RenderPipelineLifetimes.replaced(a, null, null, null); assertEquals(0, retired.get());
        RenderPipelineLifetimes.replaced(b, null, null, null); assertEquals(1, retired.get());
    }
    @Test void shutdownDestroysAliasesOnceAndIsIdempotent() {
        var destroyed = new AtomicInteger(); var pipeline = new Object();
        for (int i = 0; i < 3; i++) RenderPipelineLifetimes.replaced(new Object(), pipeline, () -> fail("Shutdown must destroy after idle"), destroyed::incrementAndGet);
        RenderPipelineLifetimes.close(); RenderPipelineLifetimes.close(); assertEquals(1, destroyed.get());
    }
    @Test void equalDescriptionsRemainIndependentOwners() {
        var destroyed = new AtomicInteger(); var a = new String("same"); var b = new String("same");
        RenderPipelineLifetimes.replaced(a, new Object(), () -> fail("Owners must use identity"), destroyed::incrementAndGet);
        RenderPipelineLifetimes.replaced(b, new Object(), () -> fail("Owners must use identity"), destroyed::incrementAndGet);
        RenderPipelineLifetimes.close(); assertEquals(2, destroyed.get());
    }
}
