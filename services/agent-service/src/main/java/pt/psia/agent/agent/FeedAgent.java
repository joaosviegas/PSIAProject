package pt.psia.agent.agent;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.psia.agent.client.ProfileClient;
import pt.psia.agent.client.dto.ProfileDto;
import pt.psia.agent.feed.FeedService;
import pt.psia.agent.feed.FeedStore;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Pre-warms feeds: profiles changed since the last tick get their AI feed built in the
 * background, so the next visit is already "ready". Correctness doesn't depend on this
 * running — {@code GET /api/feed} detects a missing/stale feed and builds it on demand.
 */
@Component
public class FeedAgent {

    private static final Logger log = LoggerFactory.getLogger(FeedAgent.class);

    private final ProfileClient profileClient;
    private final FeedService feedService;
    private final FeedStore feedStore;
    private final Counter okTicks;
    private final Counter failedTicks;
    private final Counter profilesQueued;

    public FeedAgent(ProfileClient profileClient, FeedService feedService, FeedStore feedStore, MeterRegistry registry) {
        this.profileClient = profileClient;
        this.feedService = feedService;
        this.feedStore = feedStore;
        this.okTicks = Counter.builder("agent_ticks_total").tag("result", "ok").register(registry);
        this.failedTicks = Counter.builder("agent_ticks_total").tag("result", "error").register(registry);
        this.profilesQueued = Counter.builder("agent_profiles_queued_total").register(registry);
    }

    @Scheduled(fixedDelayString = "${agent.interval-seconds}", initialDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void tick() {
        Instant start = Instant.now();
        List<ProfileDto> updated;
        try {
            updated = profileClient.updatedSince(feedStore.lastRun().orElse(null));
        } catch (Exception e) {
            failedTicks.increment();
            log.warn("Could not list updated profiles, skipping this tick: {}", e.getMessage());
            return;
        }

        updated.forEach(feedService::rebuildAsync);
        feedStore.setLastRun(start);
        okTicks.increment();
        profilesQueued.increment(updated.size());
        log.info("Queued feed rebuilds for {} updated profiles", updated.size());
    }
}
