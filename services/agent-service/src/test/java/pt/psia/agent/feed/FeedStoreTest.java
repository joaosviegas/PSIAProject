package pt.psia.agent.feed;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import pt.psia.agent.client.dto.MovieDto;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FeedStoreTest {

    StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    JsonMapper jsonMapper = new JsonMapper();
    FeedStore store;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        store = new FeedStore(redis, jsonMapper, 24);
    }

    @Test
    void saveAndFindFeed() {
        Feed feed = new Feed("u123", Instant.parse("2026-10-10T10:00:00Z"), "Summary", true,
                List.of(new MovieDto(1L, "tt1", "Arrival", 2016, List.of("Sci-Fi"), null, null, null, null, null, null, null)),
                List.of(), "v1");

        store.save(feed);
        verify(valueOps).set(eq("feed:u123"), anyString(), eq(Duration.ofHours(24)));

        String json = jsonMapper.writeValueAsString(feed);
        when(valueOps.get("feed:u123")).thenReturn(json);

        Optional<Feed> retrieved = store.find("u123");
        assertThat(retrieved).isPresent();
        assertThat(retrieved.get().userId()).isEqualTo("u123");
        assertThat(retrieved.get().picks()).extracting(MovieDto::title).containsExactly("Arrival");
    }

    @Test
    void lastRunGetAndSet() {
        Instant now = Instant.parse("2026-10-13T12:00:00Z");
        store.setLastRun(now);
        verify(valueOps).set("agent:last-run", "2026-10-13T12:00:00Z");

        when(valueOps.get("agent:last-run")).thenReturn("2026-10-13T12:00:00Z");
        assertThat(store.lastRun()).contains(now);
    }
}