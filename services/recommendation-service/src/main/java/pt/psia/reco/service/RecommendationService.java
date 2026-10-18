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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
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
    /** How many liked/disliked movies are shown to the LLM as taste examples. */
    private static final int TASTE_EXAMPLES = 5;
    // The candidate list shows "Title (Year)", and small models usually copy the year too.
    private static final Pattern TRAILING_YEAR = Pattern.compile("\\s*\\(\\d{4}\\)\\s*$");
    private static final Pattern LEADING_NUMBER = Pattern.compile("^\\s*\\d+\\s*[.)\\-:]\\s*");
    private static final String MATCH_TAG = " [matches request]";

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

    /** A shortlist of movies, genres, and a user's profile. {@code keywords} are the search words from the user's notes. */
    private record Shortlist(List<MovieResponse> movies, List<String> genres, Optional<ProfileSnapshot> profile,
                             List<MovieResponse> liked, List<MovieResponse> disliked, List<String> keywords) {
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
            List<MovieResponse> picked = picks.stream()
                    .filter(p -> p != null && !p.isBlank())
                    .map(pick -> matchCandidate(pick, shortlist.movies()))
                    .flatMap(Optional::stream)
                    .distinct()
                    .limit(PICKS_REQUESTED)
                    .toList();

            if (!picked.isEmpty()) {
                // Small models often return fewer than requested; top up from the ranked shortlist.
                List<MovieResponse> movies = fillUp(picked, shortlist.movies());
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

    /** Constructs a Shortlist of movies based on the user's preferences, mood, notes, and profile. */
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

        // The notes steer *which movies are considered*, not just what the LLM is told: without this,
        List<String> keywords = MovieScorer.keywords(request.notes());
        List<MovieResponse> movies = movieService.personalized(primary, secondary, liked, disliked, keywords, SHORTLIST_SIZE);
        return new Shortlist(movies, primary, profile, tasteExamples(liked, primary), tasteExamples(disliked, primary),
                keywords);
    }

    /** Loads the given movies, keeping only those in one of the genres (all of them when no genre is set). */
    private List<MovieResponse> tasteExamples(List<Long> ids, List<String> genres) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return movieService.byIds(ids).stream()
                .filter(m -> genres.isEmpty() || m.genres() != null && m.genres().stream()
                        .anyMatch(g -> genres.stream().anyMatch(g::equalsIgnoreCase)))
                .limit(TASTE_EXAMPLES)
                .toList();
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
        parts.addAll(shortlist.keywords());
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

    /**
     * Builds a prompt for the LLM. It is written for a very small model: short rules, the user's
     * request stated explicitly, and candidates that match the request flagged so the model doesn't have
     * to work that out from the overviews.
     */
    private String buildPrompt(RecommendationRequest request, Shortlist shortlist) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a movie recommendation assistant. Pick the ")
                .append(PICKS_REQUESTED)
                .append(" best movies for this user from the CANDIDATES list.\n")
                .append("Rules:\n")
                .append("- Use only titles from CANDIDATES, copied exactly, without the year.\n")
                .append("- The USER REQUEST matters most: prefer candidates marked [matches request].\n")
                .append("- Then fit the selected genres, the mood, and the taste shown by the liked movies.\n")
                .append("- Do not pick movies similar to the disliked ones.\n")
                .append("- \"summary\" is one short sentence addressed to the user.\n\n");

        // The user's own words come first so they are not lost behind the long candidate list.
        if (request.notes() != null && !request.notes().isBlank()) {
            sb.append("USER REQUEST: ").append(request.notes().strip()).append("\n");
        }
        sb.append("Selected genres: ").append(shortlist.genres().isEmpty() ? "any" : String.join(", ", shortlist.genres())).append("\n");
        sb.append("Mood: ").append(request.mood() == null || request.mood().isBlank() ? "not specified" : request.mood()).append("\n");
        shortlist.profile().map(ProfileSnapshot::bio).filter(b -> !b.isBlank())
                .ifPresent(b -> sb.append("About the user: ").append(b).append("\n"));

        // Movies the user liked or disliked in those genres show the LLM what their taste looks like.
        if (!shortlist.liked().isEmpty()) {
            sb.append("\nMOVIES THE USER LIKED:\n");
            shortlist.liked().forEach(m -> appendMovie(sb, m, List.of()));
        }
        if (!shortlist.disliked().isEmpty()) {
            sb.append("\nMOVIES THE USER DISLIKED (avoid similar ones):\n");
            shortlist.disliked().forEach(m -> appendMovie(sb, m, List.of()));
        }

        sb.append("\nCANDIDATES:\n");
        shortlist.movies().forEach(m -> appendMovie(sb, m, shortlist.keywords()));
        return sb.toString();
    }

    /** Appends one movie line with its title, year, genres, rating, and a shortened overview. */
    private static void appendMovie(StringBuilder sb, MovieResponse m, List<String> keywords) {
        sb.append("- ").append(m.title());
        if (m.startYear() != null) {
            sb.append(" (").append(m.startYear()).append(")");
        }
        // Right after the title, where a small model is most likely to notice it.
        if (!keywords.isEmpty() && MovieScorer.keywordMatches(m.title() + " " + m.overview(), keywords) > 0) {
            sb.append(MATCH_TAG);
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

    /** Adds the best-ranked shortlist movies not already picked until {@code PICKS_REQUESTED} is reached. */
    private static List<MovieResponse> fillUp(List<MovieResponse> picked, List<MovieResponse> shortlist) {
        Set<MovieResponse> result = new LinkedHashSet<>(picked);
        for (MovieResponse m : shortlist) {
            if (result.size() >= PICKS_REQUESTED) {
                break;
            }
            result.add(m);
        }
        return List.copyOf(result);
    }

    /**
     * Matches a picked title to a candidate. Tolerates quotes, a copied "(year)", list numbering,
     * punctuation and case; falls back to a unique partial match for slightly mangled titles.
     */
    private Optional<MovieResponse> matchCandidate(String pickTitle, List<MovieResponse> candidates) {
        String wanted = normalize(pickTitle);
        if (wanted.isEmpty()) {
            return Optional.empty();
        }
        Optional<MovieResponse> exact = candidates.stream()
                .filter(m -> normalize(m.title()).equals(wanted))
                .findFirst();
        if (exact.isPresent()) {
            return exact;
        }
        if (wanted.length() < 4) {
            return Optional.empty();
        }
        List<MovieResponse> loose = candidates.stream()
                .filter(m -> {
                    String title = normalize(m.title());
                    return title.length() >= 4 && (title.contains(wanted) || wanted.contains(title));
                })
                .toList();
        return loose.size() == 1 ? Optional.of(loose.get(0)) : Optional.empty();
    }

    /** Lower-cases and strips quotes, list numbers, a trailing "(year)" and punctuation. */
    private static String normalize(String title) {
        if (title == null) {
            return "";
        }
        String t = title.trim().replaceAll("^[\"'\u201C\u201D]+|[\"'\u201C\u201D]+$", "").trim();
        t = LEADING_NUMBER.matcher(t).replaceFirst("");
        t = TRAILING_YEAR.matcher(t).replaceFirst("");
        return t.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}