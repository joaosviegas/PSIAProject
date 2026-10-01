package pt.psia.agent.feed;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

@Component
public class FeedStore {

    private static final String FEED_KEY = "feed:";
    private static final String PENDING_KEY = "agent:pending";
    private static final String LAST_RUN_KEY = "agent:last-run";

    private final StringRedisTemplate redis;
    private final JsonMapper jsonMapper;
    private final Duration ttl;

    public FeedStore(StringRedisTemplate redis, JsonMapper jsonMapper, @Value("${feed.ttl-hours}") long ttlHours) {
        this.redis = redis;
        this.jsonMapper = jsonMapper;
        this.ttl = Duration.ofHours(ttlHours);
    }

    public void save(Feed feed) {
        redis.opsForValue().set(FEED_KEY + feed.userId(), jsonMapper.writeValueAsString(feed), ttl);
    }

    public Optional<Feed> find(String userId) {
        return Optional.ofNullable(redis.opsForValue().get(FEED_KEY + userId))
                .map(json -> jsonMapper.readValue(json, Feed.class));
    }

    public void markPending(String userId) {
        redis.opsForSet().add(PENDING_KEY, userId);
    }

    public Set<String> pending() {
        Set<String> members = redis.opsForSet().members(PENDING_KEY);
        return members == null ? Set.of() : members;
    }

    public void clearPending(String userId) {
        redis.opsForSet().remove(PENDING_KEY, userId);
    }

    public Optional<Instant> lastRun() {
        return Optional.ofNullable(redis.opsForValue().get(LAST_RUN_KEY)).map(Instant::parse);
    }

    public void setLastRun(Instant instant) {
        redis.opsForValue().set(LAST_RUN_KEY, instant.toString());
    }
}
