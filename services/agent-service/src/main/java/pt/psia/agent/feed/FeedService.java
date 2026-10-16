package pt.psia.agent.feed;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pt.psia.agent.client.MovieClient;
import pt.psia.agent.client.ProfileClient;
import pt.psia.agent.client.dto.MovieDto;
import pt.psia.agent.client.dto.ProfileDto;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

/**
 * Serves the feed without ever making the user wait for the LLM:
 * the answer is assembled from instant sources (heuristic picks + random discover) and the
 * AI feed is built in the background the moment it is found missing or stale.
 */
@Service
public class FeedService {

    private static final Logger log = LoggerFactory.getLogger(FeedService.class);
    static final int QUICK_PICKS = 5;
    static final int DISCOVER_SIZE = 8;

    private final ProfileClient profileClient;
    private final MovieClient movieClient;
    private final FeedBuilder feedBuilder;
    private final FeedStore feedStore;

    // LLM calls are serialized by Ollama anyway; two workers keep one slow build from blocking everyone.
    private final ExecutorService executor = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "feed-builder");
        t.setDaemon(true);
        return t;
    });
    private final Set<String> building = ConcurrentHashMap.newKeySet();
    private final MeterRegistry registry;
    private final Counter failedBuilds;

    /**
     * Creates a new feed service.
     *
     * @param profileClient the profile client
     * @param movieClient the movie client
     * @param feedBuilder the feed builder
     * @param feedStore the feed store
     * @param registry the meter registry
     */
    public FeedService(ProfileClient profileClient, MovieClient movieClient, FeedBuilder feedBuilder, FeedStore feedStore,
                       MeterRegistry registry) {
        this.profileClient = profileClient;
        this.movieClient = movieClient;
        this.feedBuilder = feedBuilder;
        this.feedStore = feedStore;
        this.registry = registry;
        this.failedBuilds = Counter.builder("feed_builds_total").tag("result", "failed").register(registry);
        Gauge.builder("feed_builds_in_progress", building, Set::size).register(registry);
    }

    /** Returns the feed for the given user ID, building it in the background if necessary. */
    public FeedResponse get(String userId) {
        Optional<ProfileDto> found = profileClient.find(UUID.fromString(userId));
        if (found.isEmpty() || !hasPreferences(found.get())) {
            countRequest(FeedResponse.NEEDS_PREFERENCES);
            return FeedResponse.needsPreferences();
        }
        ProfileDto profile = found.get();
        Set<Long> seen = seen(profile);

        Optional<Feed> fresh = feedStore.find(userId)
                .filter(f -> Objects.equals(f.profileVersion(), profile.updatedAt()));
        if (fresh.isPresent()) {
            Feed feed = fresh.get();
            Set<Long> exclude = new HashSet<>(seen);
            feed.picks().forEach(m -> exclude.add(m.id()));
            List<MovieDto> discover = movieClient.discover(genres(profile), exclude, DISCOVER_SIZE);
            countRequest(FeedResponse.READY);
            return new FeedResponse(FeedResponse.READY, feed.generatedAt(), feed.summary(), feed.aiGenerated(),
                    feed.picks(), discover, feed.fromSimilarUsers());
        }

        // Missing or built from an older profile: start the AI build now, answer instantly meanwhile.
        rebuildAsync(profile);
        List<MovieDto> quick = movieClient.personalized(genres(profile), orEmpty(profile.favoriteMovieIds()),
                orEmpty(profile.dislikedMovieIds()), QUICK_PICKS);
        Set<Long> exclude = new HashSet<>(seen);
        quick.forEach(m -> exclude.add(m.id()));
        List<MovieDto> discover = movieClient.discover(genres(profile), exclude, DISCOVER_SIZE);
        countRequest(FeedResponse.BUILDING);
        return new FeedResponse(FeedResponse.BUILDING, null, null, false, quick, discover, List.of());
    }

    /** Refreshes the feed for the given user ID if they have preferences, building it in the background. */
    public void refresh(String userId) {
        profileClient.find(UUID.fromString(userId)).filter(this::hasPreferences).ifPresent(this::rebuildAsync);
    }

    /** A fresh random batch from the user's categories (the "shuffle" button). */
    public List<MovieDto> discover(String userId) {
        Optional<ProfileDto> found = profileClient.find(UUID.fromString(userId));
        if (found.isEmpty() || !hasPreferences(found.get())) {
            return List.of();
        }
        return movieClient.discover(genres(found.get()), seen(found.get()), DISCOVER_SIZE);
    }

    /** At most one build per user at a time; extra requests while one is running are dropped. */
    public void rebuildAsync(ProfileDto profile) {
        if (!hasPreferences(profile) || !building.add(profile.userId())) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    feedStore.save(feedBuilder.build(profile));
                } catch (Exception e) {
                    failedBuilds.increment();
                    log.warn("Failed to build feed for {}: {}", profile.userId(), e.getMessage());
                } finally {
                    building.remove(profile.userId());
                }
            });
        } catch (RejectedExecutionException e) {
            building.remove(profile.userId());
        }
    }

    /** Increments the feed request counter for the given status. */
    private void countRequest(String status) {
        registry.counter("feed_requests_total", "status", status).increment();
    }

    /** Returns true if the given profile has preferences (favorite genres). */
    boolean hasPreferences(ProfileDto profile) {
        return !orEmpty(profile.favoriteGenres()).isEmpty();
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    /** Returns the favorite genres of the given profile, or an empty list if none. */
    private static List<String> genres(ProfileDto profile) {
        return orEmpty(profile.favoriteGenres());
    }

    /** Returns the set of movie IDs that the given profile has seen (liked or disliked). */
    private static Set<Long> seen(ProfileDto profile) {
        Set<Long> seen = new HashSet<>(orEmpty(profile.favoriteMovieIds()));
        seen.addAll(orEmpty(profile.dislikedMovieIds()));
        return seen;
    }

    /** Returns an empty list if the given list is null, or the list itself otherwise. */
    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
