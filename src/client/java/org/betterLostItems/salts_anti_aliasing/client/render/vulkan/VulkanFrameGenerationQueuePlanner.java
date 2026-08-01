package org.betterLostItems.salts_anti_aliasing.client.render.vulkan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Selects the three private Vulkan queues required by FidelityFX frame generation.
 *
 * <p>The SDK submits work from its own presentation threads, so sharing Minecraft's queues would
 * violate Vulkan's external-synchronization rules. Candidates therefore begin after every queue
 * index already requested by Minecraft.</p>
 */
public final class VulkanFrameGenerationQueuePlanner {
    static final int QUEUE_GRAPHICS = 1;
    static final int QUEUE_COMPUTE = 2;
    static final int QUEUE_TRANSFER = 4;

    private VulkanFrameGenerationQueuePlanner() {
    }

    public static Optional<Plan> plan(List<QueueFamily> families, QueueRef gameQueue) {
        if (families == null || gameQueue == null) {
            return Optional.empty();
        }

        List<Candidate> candidates = new ArrayList<>();
        for (QueueFamily family : families) {
            if (family == null
                    || family.index() < 0
                    || family.queueCount() <= 0
                    || family.requestedCount() < 0
                    || family.requestedCount() > family.queueCount()) {
                continue;
            }

            for (int queueIndex = family.requestedCount(); queueIndex < family.queueCount(); queueIndex++) {
                candidates.add(new Candidate(new QueueRef(family.index(), queueIndex), family));
            }
        }

        return candidates.stream()
                .filter(VulkanFrameGenerationQueuePlanner::canPresent)
                .flatMap(present -> candidates.stream()
                        .filter(async -> supports(async.family().flags(), QUEUE_COMPUTE))
                        .filter(async -> !async.queue().equals(present.queue()))
                        .flatMap(async -> candidates.stream()
                                .filter(acquire -> !acquire.queue().equals(present.queue()))
                                .filter(acquire -> !acquire.queue().equals(async.queue()))
                                .map(acquire -> new ScoredPlan(
                                        new Plan(async.queue(), present.queue(), acquire.queue()),
                                        score(gameQueue, async, present, acquire)
                                ))))
                .min(Comparator.comparingLong(ScoredPlan::score))
                .map(ScoredPlan::plan);
    }

    private static boolean canPresent(Candidate candidate) {
        return candidate.family().presentationSupported()
                && (candidate.family().flags() & (QUEUE_GRAPHICS | QUEUE_COMPUTE | QUEUE_TRANSFER)) != 0;
    }

    private static long score(QueueRef gameQueue, Candidate async, Candidate present, Candidate acquire) {
        int presentFlags = present.family().flags();
        int asyncFlags = async.family().flags();
        int acquireFlags = acquire.family().flags();

        long score = 0L;
        if (present.queue().family() != gameQueue.family()) {
            score += 100_000L;
        }
        if (!supports(presentFlags, QUEUE_GRAPHICS | QUEUE_COMPUTE)) {
            score += 10_000L;
        }
        if ((asyncFlags & QUEUE_GRAPHICS) != 0) {
            score += 1_000L;
        }
        if (supports(acquireFlags, QUEUE_TRANSFER)
                && (acquireFlags & (QUEUE_GRAPHICS | QUEUE_COMPUTE)) == 0) {
            score -= 100L;
        } else if ((acquireFlags & QUEUE_GRAPHICS) != 0) {
            score += 100L;
        }

        score += present.queue().index();
        score += async.queue().index();
        score += acquire.queue().index();
        return score;
    }

    private static boolean supports(int flags, int requiredFlags) {
        return (flags & requiredFlags) == requiredFlags;
    }

    public record QueueFamily(
            int index,
            int flags,
            int queueCount,
            int requestedCount,
            boolean presentationSupported
    ) {
    }

    public record QueueRef(int family, int index) {
        public QueueRef {
            if (family < 0 || index < 0) {
                throw new IllegalArgumentException("Queue family and index must be non-negative");
            }
        }
    }

    public record Plan(QueueRef asyncCompute, QueueRef present, QueueRef imageAcquire) {
        public Plan {
            if (asyncCompute == null || present == null || imageAcquire == null
                    || asyncCompute.equals(present)
                    || asyncCompute.equals(imageAcquire)
                    || present.equals(imageAcquire)) {
                throw new IllegalArgumentException("FidelityFX queues must be non-null and distinct");
            }
        }
    }

    private record Candidate(QueueRef queue, QueueFamily family) {
    }

    private record ScoredPlan(Plan plan, long score) {
    }
}
