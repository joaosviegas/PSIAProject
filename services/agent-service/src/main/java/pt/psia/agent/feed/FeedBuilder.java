package pt.psia.agent.feed;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import pt.psia.agent.client.MovieClient;
import pt.psia.agent.client.ProfileClient;
import pt.psia.agent.client.dto.MovieDto;
import pt.psia.agent.client.dto.ProfileDto;
import pt.psia.agent.llm.LlmClient;
import pt.psia.agent.llm.LlmPicks;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
public class FeedBuilder {

    private static final Logger log = LoggerFactory.getLogger(FeedBuilder.class);
    // Small on purpose: the candidates are already ranked by taste, and prompt size is most of the LLM latency on CPU.
    private static final int SHORTLIST_SIZE = 12;
    private static final int PICKS_REQUESTED = 5;
    private static final int LIKED_TITLES_IN_PROMPT = 5;
    private static final int SIMILAR_PROFILES = 5;
    private static final int SIMILAR_PICKS = 6;
    private static final Pattern TRAILING_YEAR = Pattern.compile("\\s*\\(\\d{4}\\)$");
    static final String FALLBACK_SUMMARY =
            "Our AI assistant was busy, so these are the best matches for your taste in the catalog.";

    private final ProfileClient profileClient;
    private final MovieClient movieClient;
    private final LlmClient llmClient;
    private final Timer buildTimer;
    private final Counter aiBuilds;
    private final Counter fallbackBuilds;

    public FeedBuilder(ProfileClient profileClient, MovieClient movieClient, LlmClient llmClient, MeterRegistry registry) {
        this.profileClient = profileClient;
        this.movieClient = movieClient;
        this.llmClient = llmClient;
        this.buildTimer = Timer.builder("feed_build")
                .publishPercentileHistogram().maximumExpectedValue(Duration.ofMinutes(3)).register(registry);
        this.aiBuilds = Counter.builder("feed_builds_total").tag("result", "ai").register(registry);
        this.fallbackBuilds = Counter.builder("feed_builds_total").tag("result", "fallback").register(registry);
    }

    public Feed build(ProfileDto profile) {
        Timer.Sample sample = Timer.start();
        Set<Long> seen = new HashSet<>(orEmpty(profile.favoriteMovieIds()));
        seen.addAll(orEmpty(profile.dislikedMovieIds()));

        List<MovieDto> candidates = movieClient.personalized(orEmpty(profile.favoriteGenres()),
                orEmpty(profile.favoriteMovieIds()), orEmpty(profile.dislikedMovieIds()), SHORTLIST_SIZE);
        AiPicks ai = aiPicks(profile, candidates);

        Set<Long> excluded = new HashSet<>(seen);
        ai.movies().forEach(m -> excluded.add(m.id()));
        List<Feed.SimilarPick> fromSimilar = List.of();
        try {
            fromSimilar = fromSimilarUsers(UUID.fromString(profile.userId()), excluded);
        } catch (Exception e) {
            // Nice-to-have section: never let it sink the whole feed.
            log.warn("Similar-users section failed for {}: {}", profile.userId(), e.getMessage());
        }

        sample.stop(buildTimer);
        (ai.aiGenerated() ? aiBuilds : fallbackBuilds).increment();
        return new Feed(profile.userId(), Instant.now(), ai.summary(), ai.aiGenerated(), ai.movies(), fromSimilar,
                profile.updatedAt());
    }

    private record AiPicks(String summary, boolean aiGenerated, List<MovieDto> movies) {}

    private AiPicks aiPicks(ProfileDto profile, List<MovieDto> candidates) {
        if (candidates.isEmpty()) {
            return new AiPicks("No new movies match your categories yet — try adding more to your profile.",
                    false, List.of());
        }
        try {
            LlmPicks llmPicks = llmClient.pickMovies(buildPrompt(profile, candidates));
            List<MovieDto> movies = orEmpty(llmPicks.picks()).stream()
                    .filter(pick -> pick != null && !pick.isBlank())
                    .map(pick -> matchCandidate(pick, candidates))
                    .flatMap(Optional::stream)
                    .distinct()
                    .limit(PICKS_REQUESTED)
                    .toList();
            if (!movies.isEmpty()) {
                return new AiPicks(llmPicks.summary(), true, movies);
            }
            log.warn("LLM picks for {} matched none of the candidates, falling back: {}", profile.userId(), llmPicks.picks());
        } catch (Exception e) {
            log.warn("LLM call failed for {}, falling back to top-rated candidates: {}", profile.userId(), e.getMessage());
        }
        return new AiPicks(FALLBACK_SUMMARY, false, candidates.stream().limit(PICKS_REQUESTED).toList());
    }

    private String buildPrompt(ProfileDto profile, List<MovieDto> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a movie recommendation assistant building a personal feed. Pick the best ")
                .append(PICKS_REQUESTED)
                .append(" movies for this user from the CANDIDATE list only ")
                .append("(never invent a title that isn't in the list). ")
                .append("Each entry in your \"picks\" list must be copied exactly from a candidate's title. ")
                .append("Write the \"summary\" as one or two sentences addressed to the user explaining the selection.\n\n");

        List<String> genres = orEmpty(profile.favoriteGenres());
        sb.append("User's favourite genres: ").append(genres.isEmpty() ? "any" : String.join(", ", genres)).append("\n");
        if (profile.bio() != null && !profile.bio().isBlank()) {
            sb.append("User's bio: ").append(profile.bio()).append("\n");
        }
        List<Long> liked = orEmpty(profile.favoriteMovieIds()).stream().limit(LIKED_TITLES_IN_PROMPT).toList();
        if (!liked.isEmpty()) {
            String titles = movieClient.byIds(liked).stream().map(MovieDto::title).collect(Collectors.joining(", "));
            if (!titles.isBlank()) {
                sb.append("Movies the user liked: ").append(titles).append("\n");
            }
        }

        sb.append("\nCANDIDATES:\n");
        for (MovieDto m : candidates) {
            sb.append("- ").append(m.title());
            if (m.startYear() != null) {
                sb.append(" (").append(m.startYear()).append(")");
            }
            sb.append(" — genres: ").append(m.genres() == null ? "n/a" : String.join(", ", m.genres()));
            if (m.avgRating() != null) {
                sb.append(", rating: ").append(m.avgRating());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private Optional<MovieDto> matchCandidate(String pickTitle, List<MovieDto> candidates) {
        // Small models often echo the "(year)" from the candidate list.
        String normalized = TRAILING_YEAR.matcher(pickTitle.trim()).replaceFirst("").toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(m -> m.title().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }

    private List<Feed.SimilarPick> fromSimilarUsers(UUID userId, Set<Long> excluded) {
        Map<Long, List<String>> likedBy = new HashMap<>();
        for (ProfileDto other : profileClient.similar(userId, SIMILAR_PROFILES)) {
            for (Long movieId : orEmpty(other.favoriteMovieIds())) {
                if (!excluded.contains(movieId)) {
                    likedBy.computeIfAbsent(movieId, k -> new ArrayList<>()).add(other.displayName());
                }
            }
        }
        List<Long> top = likedBy.entrySet().stream()
                .sorted(Comparator.comparingInt((Map.Entry<Long, List<String>> e) -> -e.getValue().size())
                        .thenComparing(Map.Entry::getKey))
                .limit(SIMILAR_PICKS)
                .map(Map.Entry::getKey)
                .toList();

        Map<Long, MovieDto> movies = movieClient.byIds(top).stream()
                .collect(Collectors.toMap(MovieDto::id, m -> m));
        return top.stream()
                .filter(movies::containsKey)
                .map(id -> new Feed.SimilarPick(movies.get(id), likedBy.get(id)))
                .toList();
    }

    private static <T> List<T> orEmpty(List<T> list) {
        return list == null ? List.of() : list;
    }
}
