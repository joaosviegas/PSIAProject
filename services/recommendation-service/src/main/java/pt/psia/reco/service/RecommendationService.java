package pt.psia.reco.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pt.psia.reco.domain.Moods;
import pt.psia.reco.service.ProfileClient.ProfileSnapshot;
import pt.psia.reco.web.dto.MovieResponse;
import pt.psia.reco.web.dto.RecommendationRequest;
import pt.psia.reco.web.dto.RecommendationResponse;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Service for generating movie recommendations based on user preferences, mood, and notes.
 * It interacts with the LLM client and profile client to provide personalized recommendations.
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    /** How many candidates the LLM sees. Small on purpose: prompt size is most of the latency on CPU. */
    private static final int SHORTLIST_SIZE = 12;
    private static final int PICKS_REQUESTED = 5;
    /** Overviews are cut to this length so they add context without blowing up the prompt. */
    private static final int OVERVIEW_MAX_CHARS = 150;
    // The candidate list shows "Title (Year)", and small models usually copy the year too.
    private static final Pattern TRAILING_YEAR = Pattern.compile("\\s*\\(\\d{4}\\)\\s*$");

    private final MovieService movieService;
    private final LlmClient llmClient;
    private final ProfileClient profileClient;
    private final TtlCache<String, RecommendationResponse> cache = new TtlCache<>(200, Duration.ofMinutes(2));
    private final Counter aiCounter;
    private final Counter fallbackCounter;
    private final Counter quickCounter;
    private final Counter cachedCounter;

    /**
     * Constructs a new RecommendationService with the given dependencies and registers counters for metrics.
     * 
     * @param movieService the MovieService for fetching movie data
     * @param llmClient the LlmClient for interacting with the large language model
     * @param profileClient the ProfileClient for fetching user profile information
     * @param registry the MeterRegistry for recording metrics
     */
    public RecommendationService(MovieService movieService, LlmClient llmClient, ProfileClient profileClient,
                                 MeterRegistry registry) {
        this.movieService = movieService;
        this.llmClient = llmClient;
        this.profileClient = profileClient;
        this.aiCounter = Counter.builder("recommendations_total").tag("mode", "ai").register(registry);
        this.fallbackCounter = Counter.builder("recommendations_total").tag("mode", "fallback").register(registry);
        this.quickCounter = Counter.builder("recommendations_total").tag("mode", "quick").register(registry);
        this.cachedCounter = Counter.builder("recommendations_total").tag("mode", "cached").register(registry);
    }

    /** A shortlist of movies, genres, and a user's profile. */
    private record Shortlist(List<MovieResponse> movies, List<String> genres, Optional<ProfileSnapshot> profile) {
    }

    /** Returns a quick recommendation based on the user's preferences and the available movies. (No LLM) */
    public RecommendationResponse quick(RecommendationRequest request, String bearerToken) {
        Shortlist shortlist = shortlist(request, bearerToken);
        quickCounter.increment();
        return heuristic(request, shortlist);
    }

    /** Returns a recommendation based on the user's preferences and the available movies, using the LLM. */
    public RecommendationResponse recommend(RecommendationRequest request, String bearerToken) {
        // Check the cache first to avoid unnecessary LLM calls for repeated requests.
        String cacheKey = cacheKey(request, bearerToken);

        // If the recommendation is already cached, return it immediately.
        RecommendationResponse cached = cache.get(cacheKey);
        if (cached != null) {
            cachedCounter.increment();
            return cached;
        }

        // Generate a shortlist of movies based on the user's preferences and profile.
        Shortlist shortlist = shortlist(request, bearerToken);
        if (shortlist.movies().isEmpty()) {
            fallbackCounter.increment();
            return heuristic(request, shortlist);
        }

        // Attempt to get recommendations from the LLM based on the shortlist and user request.
        try {
            LlmPicks llmPicks = llmClient.pickMovies(buildPrompt(request, shortlist));
            List<String> picks = llmPicks == null || llmPicks.picks() == null ? List.of() : llmPicks.picks();
            List<MovieResponse> movies = picks.stream()
                    .filter(p -> p != null && !p.isBlank())
                    .map(pick -> matchCandidate(pick, shortlist.movies()))
                    .flatMap(Optional::stream)
                    .distinct()
                    .limit(PICKS_REQUESTED)
                    .toList();

            if (!movies.isEmpty()) {
                String summary = llmPicks.summary() == null || llmPicks.summary().isBlank()
                        ? describe(request, shortlist) : llmPicks.summary();
                RecommendationResponse response = new RecommendationResponse(summary, movies, true);
                cache.put(cacheKey, response);
                aiCounter.increment();
                return response;
            }
            log.warn("LLM picks matched none of the candidates, falling back: {}", picks);
        } catch (Exception e) {
            log.warn("LLM call failed, falling back to the heuristic ranking: {}", e.getMessage());
        }

        // Ollama is unavailable, too slow, or returned titles we couldn't match.
        RecommendationResponse fallback = heuristic(request, shortlist);
        fallbackCounter.increment();
        return new RecommendationResponse(
                "The AI assistant didn't answer in time, so these are the best matches for your taste instead.",
                fallback.movies(), false);
    }

    /** Constructs a Shortlist of movies based on the user's preferences, mood, and profile. */
    private Shortlist shortlist(RecommendationRequest request, String bearerToken) {
        // Fetch the user's profile information using the provided bearer token.
        Optional<ProfileSnapshot> profile = profileClient.fetchProfile(bearerToken);
        List<String> picked = clean(request.genres());
        List<String> saved = profile.map(ProfileSnapshot::genres).orElse(List.of());

        // Explicit choice wins; otherwise fall back to the genres saved in the profile.
        List<String> primary = picked.isEmpty() ? saved : picked;
        List<String> secondary = new ArrayList<>(Moods.genresFor(request.mood()));
        if (!picked.isEmpty()) {
            secondary.addAll(saved);
        }

        // Fetch the user's liked and disliked movies from their profile, if available.
        List<Long> liked = profile.map(ProfileSnapshot::liked).orElse(List.of());
        List<Long> disliked = profile.map(ProfileSnapshot::disliked).orElse(List.of());
        List<MovieResponse> movies = movieService.personalized(primary, secondary, liked, disliked, SHORTLIST_SIZE);
        return new Shortlist(movies, primary, profile);
    }

    /** Returns a fallback recommendation based on the user's preferences and the available movies, without using the LLM. */
    private RecommendationResponse heuristic(RecommendationRequest request, Shortlist shortlist) {
        if (shortlist.movies().isEmpty()) {
            return new RecommendationResponse(
                    "No movies in the catalog match that yet — try a broader selection.", List.of(), false);
        }
        return new RecommendationResponse(describe(request, shortlist),
                shortlist.movies().stream().limit(PICKS_REQUESTED).toList(), false);
    }

    /** Generates a description of the recommendation based on the user's request and the shortlist of movies. */
    private static String describe(RecommendationRequest request, Shortlist shortlist) {
        List<String> parts = new ArrayList<>(shortlist.genres());
        if (request.mood() != null && !request.mood().isBlank()) {
            parts.add(request.mood().trim().toLowerCase(Locale.ROOT));
        }
        return parts.isEmpty()
                ? "Top-rated picks tuned to your taste."
                : "Best matches for " + String.join(", ", parts) + ", tuned to your taste.";
    }

    /** Cleans a list of strings by removing null or blank values and trimming whitespace. */
    private static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
    }

    /** Generates a cache key for the recommendation request based on the user's preferences and bearer token. */
    private static String cacheKey(RecommendationRequest request, String bearerToken) {
        return String.join("|",
                bearerToken == null ? "" : bearerToken,
                String.join(",", clean(request.genres()).stream().sorted().toList()),
                request.mood() == null ? "" : request.mood().trim().toLowerCase(Locale.ROOT),
                request.notes() == null ? "" : request.notes().trim().toLowerCase(Locale.ROOT));
    }

    /** Builds a prompt for the LLM based on the user's request and the shortlist of movies. */
    private String buildPrompt(RecommendationRequest request, Shortlist shortlist) {
        StringBuilder sb = new StringBuilder();
        // The prompt instructs the LLM to select the best-fitting movies from the provided candidates, based on the user's preferences and profile.
        sb.append("You are a movie recommendation assistant. From the CANDIDATES list only, pick the ")
                .append(PICKS_REQUESTED)
                .append(" that best fit this user (never invent a title). ")
                .append("Each entry in \"picks\" must be copied exactly from a candidate's title, without the year. ")
                .append("Write \"summary\" as one short sentence addressed to the user.\n\n");

        // Include the user's preferences, mood, and notes in the prompt to guide the LLM's recommendations.
        sb.append("Genres: ").append(shortlist.genres().isEmpty() ? "any" : String.join(", ", shortlist.genres())).append("\n");
        sb.append("Mood: ").append(request.mood() == null || request.mood().isBlank() ? "not specified" : request.mood()).append("\n");
        if (request.notes() != null && !request.notes().isBlank()) {
            sb.append("Notes: ").append(request.notes()).append("\n");
        }
        shortlist.profile().map(ProfileSnapshot::bio).filter(b -> !b.isBlank())
                .ifPresent(b -> sb.append("About the user: ").append(b).append("\n"));

        // Include the list of candidate movies in the prompt for the LLM to choose from.
        sb.append("\nCANDIDATES:\n");
        // Each candidate is listed with its title, year, genres, rating, and overview to provide context for the LLM's selection.
        for (MovieResponse m : shortlist.movies()) {
            sb.append("- ").append(m.title());
            if (m.startYear() != null) {
                sb.append(" (").append(m.startYear()).append(")");
            }
            sb.append(" — ").append(m.genres() == null ? "n/a" : String.join(", ", m.genres()));
            if (m.avgRating() != null) {
                sb.append("; rating ").append(m.avgRating());
            }
            if (m.overview() != null && !m.overview().isBlank()) {
                String overview = m.overview().strip();
                sb.append("; ").append(overview.length() > OVERVIEW_MAX_CHARS
                        ? overview.substring(0, OVERVIEW_MAX_CHARS).stripTrailing() + "..."
                        : overview);
            }
            sb.append("\n");
        }
        // The prompt ends with a request for the LLM to provide its recommendations in JSON format, specifying the picks and summary.
        return sb.toString();
    }

    /** Matches a picked movie title to a candidate in the list. */
    private Optional<MovieResponse> matchCandidate(String pickTitle, List<MovieResponse> candidates) {
        String unquoted = pickTitle.trim().replaceAll("^[\"']+|[\"']+$", "").trim();
        String normalized = TRAILING_YEAR.matcher(unquoted).replaceFirst("").trim().toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(m -> m.title().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}