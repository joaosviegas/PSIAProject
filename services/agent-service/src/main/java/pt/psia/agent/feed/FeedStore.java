package pt.psia.agent.feed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * A store for personalized movie recommendation feeds.
 */
@Component
public class FeedStore {

    private static final String FEED_KEY = "feed:";
    private static final String LAST_RUN_KEY = "agent:last-run";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final Duration ttl;

    /**
     * Creates a new feed store.
     *
     * @param redis the Redis template
     * @param jsonMapper the JSON mapper
     * @param ttlHours the time-to-live in hours
     */
    public FeedStore(StringRedisTemplate redis, JsonMapper jsonMapper, @Value("${feed.ttl-hours}") long ttlHours) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.ttl = Duration.ofHours(ttlHours);
    }

    /** Saves the given feed to the store, overwriting any existing feed for the same user ID. */
    public void save(Feed feed) {
        redis.opsForValue().set(FEED_KEY + feed.userId(), jsonMapper.writeValueAsString(feed), ttl);
    }

    /** Returns the feed for the given user ID, or empty if none exists. */
    public Optional<Feed> find(String userId) {
        return Optional.ofNullable(redis.opsForValue().get(FEED_KEY + userId))
                .map(json -> jsonMapper.readValue(json, Feed.class));
    }

    /** Returns the timestamp of the last successful run of the feed agent, or empty if none exists. */
    public Optional<Instant> lastRun() {
        return Optional.ofNullable(redis.opsForValue().get(LAST_RUN_KEY)).map(Instant::parse);
    }

    /** Sets the timestamp of the last successful run of the feed agent. */
    public void setLastRun(Instant instant) {
        redis.opsForValue().set(LAST_RUN_KEY, instant.toString());
    }
}
