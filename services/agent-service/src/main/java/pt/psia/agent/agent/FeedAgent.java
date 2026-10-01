package pt.psia.agent.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import pt.psia.agent.client.ProfileClient;
import pt.psia.agent.client.dto.ProfileDto;
import pt.psia.agent.feed.FeedBuilder;
import pt.psia.agent.feed.FeedStore;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class FeedAgent {

    private static final Logger log = LoggerFactory.getLogger(FeedAgent.class);

    private final ProfileClient profileClient;
    private final FeedBuilder feedBuilder;
    private final FeedStore feedStore;

    public FeedAgent(ProfileClient profileClient, FeedBuilder feedBuilder, FeedStore feedStore) {
        this.profileClient = profileClient;
        this.feedBuilder = feedBuilder;
        this.feedStore = feedStore;
    }

    @Scheduled(fixedDelayString = "${agent.interval-seconds}", initialDelay = 30, timeUnit = TimeUnit.SECONDS)
    public void tick() {
        Instant start = Instant.now();
        Map<String, ProfileDto> profiles = new LinkedHashMap<>();
        try {
            profileClient.updatedSince(feedStore.lastRun().orElse(null))
                    .forEach(p -> profiles.put(p.userId(), p));
        } catch (Exception e) {
            log.warn("Could not list updated profiles, skipping this tick: {}", e.getMessage());
            return;
        }

        for (String userId : feedStore.pending()) {
            if (profiles.containsKey(userId)) continue;
            try {
                profiles.put(userId, profileClient.get(UUID.fromString(userId)));
            } catch (Exception e) {
                // No profile yet (404) or a bad id: nothing to build from, drop it until the user asks again.
                log.info("Dropping pending user {}: {}", userId, e.getMessage());
                feedStore.clearPending(userId);
            }
        }

        int built = 0;
        for (ProfileDto profile : profiles.values()) {
            try {
                feedStore.save(feedBuilder.build(profile));
                feedStore.clearPending(profile.userId());
                built++;
            } catch (Exception e) {
                log.warn("Failed to build feed for {}: {}", profile.userId(), e.getMessage());
            }
        }

        feedStore.setLastRun(start);
        log.info("Built {} of {} feeds in {}s", built, profiles.size(), Duration.between(start, Instant.now()).toSeconds());
    }
}
