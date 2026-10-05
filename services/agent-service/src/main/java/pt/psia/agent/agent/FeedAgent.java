package pt.psia.agent.agent;

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

    public FeedAgent(ProfileClient profileClient, FeedService feedService, FeedStore feedStore) {
        this.profileClient = profileClient;
        this.feedService = feedService;
        this.feedStore = feedStore;
    }

    @Scheduled(fixedDelayString = "${agent.interval-seconds}", initialDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void tick() {
        Instant start = Instant.now();
        List<ProfileDto> updated;
        try {
            updated = profileClient.updatedSince(feedStore.lastRun().orElse(null));
        } catch (Exception e) {
            log.warn("Could not list updated profiles, skipping this tick: {}", e.getMessage());
            return;
        }

        updated.forEach(feedService::rebuildAsync);
        feedStore.setLastRun(start);
        log.info("Queued feed rebuilds for {} updated profiles", updated.size());
    }
}
