package pt.psia.reco.service;

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
 * Two-step recommendations so the UI never waits on the language model:
 * <ol>
 *   <li>{@link #quick} — taste-aware ranking straight from the database (milliseconds, no LLM);</li>
 *   <li>{@link #recommend} — the same shortlist, re-ranked and explained by the LLM, with a hard
 *       time ceiling and a fallback to step 1.</li>
 * </ol>
 */
@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    /** How many candidates the LLM sees. Small on purpose: prompt size is most of the latency on CPU. */
    private static final int SHORTLIST_SIZE = 12;
    private static final int PICKS_REQUESTED = 5;
    // The candidate list shows "Title (Year)", and small models usually copy the year too.
    private static final Pattern TRAILING_YEAR = Pattern.compile("\\s*\\(\\d{4}\\)\\s*$");

    private final MovieService movieService;
    private final LlmClient llmClient;
    private final ProfileClient profileClient;
    private final TtlCache<String, RecommendationResponse> cache = new TtlCache<>(200, Duration.ofMinutes(2));

    public RecommendationService(MovieService movieService, LlmClient llmClient, ProfileClient profileClient) {
        this.movieService = movieService;
        this.llmClient = llmClient;
        this.profileClient = profileClient;
    }

    private record Shortlist(List<MovieResponse> movies, List<String> genres, Optional<ProfileSnapshot> profile) {
    }

    /** Instant, no LLM. */
    public RecommendationResponse quick(RecommendationRequest request, String bearerToken) {
        Shortlist shortlist = shortlist(request, bearerToken);
        return heuristic(request, shortlist);
    }

    /**
     * @param bearerToken the caller's raw "Bearer &lt;token&gt;" header value,
     *                     forwarded to profile-service; null if the request
     *                     somehow reached here unauthenticated.
     */
    public RecommendationResponse recommend(RecommendationRequest request, String bearerToken) {
        String cacheKey = cacheKey(request, bearerToken);
        RecommendationResponse cached = cache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        Shortlist shortlist = shortlist(request, bearerToken);
        if (shortlist.movies().isEmpty()) {
            return heuristic(request, shortlist);
        }

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
                return response;
            }
            log.warn("LLM picks matched none of the candidates, falling back: {}", picks);
        } catch (Exception e) {
            log.warn("LLM call failed, falling back to the heuristic ranking: {}", e.getMessage());
        }

        // Ollama is unavailable, too slow, or returned titles we couldn't match.
        RecommendationResponse fallback = heuristic(request, shortlist);
        return new RecommendationResponse(
                "The AI assistant didn't answer in time, so these are the best matches for your taste instead.",
                fallback.movies(), false);
    }

    private Shortlist shortlist(RecommendationRequest request, String bearerToken) {
        Optional<ProfileSnapshot> profile = profileClient.fetchProfile(bearerToken);
        List<String> picked = clean(request.genres());
        List<String> saved = profile.map(ProfileSnapshot::genres).orElse(List.of());

        // Explicit choice wins; otherwise fall back to the genres saved in the profile.
        List<String> primary = picked.isEmpty() ? saved : picked;
        List<String> secondary = new ArrayList<>(Moods.genresFor(request.mood()));
        if (!picked.isEmpty()) {
            secondary.addAll(saved);
        }

        List<Long> liked = profile.map(ProfileSnapshot::liked).orElse(List.of());
        List<Long> disliked = profile.map(ProfileSnapshot::disliked).orElse(List.of());
        List<MovieResponse> movies = movieService.personalized(primary, secondary, liked, disliked, SHORTLIST_SIZE);
        return new Shortlist(movies, primary, profile);
    }

    private RecommendationResponse heuristic(RecommendationRequest request, Shortlist shortlist) {
        if (shortlist.movies().isEmpty()) {
            return new RecommendationResponse(
                    "No movies in the catalog match that yet — try a broader selection.", List.of(), false);
        }
        return new RecommendationResponse(describe(request, shortlist),
                shortlist.movies().stream().limit(PICKS_REQUESTED).toList(), false);
    }

    private static String describe(RecommendationRequest request, Shortlist shortlist) {
        List<String> parts = new ArrayList<>(shortlist.genres());
        if (request.mood() != null && !request.mood().isBlank()) {
            parts.add(request.mood().trim().toLowerCase(Locale.ROOT));
        }
        return parts.isEmpty()
                ? "Top-rated picks tuned to your taste."
                : "Best matches for " + String.join(", ", parts) + ", tuned to your taste.";
    }

    private static List<String> clean(List<String> values) {
        return values == null ? List.of() : values.stream().filter(v -> v != null && !v.isBlank()).map(String::trim).toList();
    }

    private static String cacheKey(RecommendationRequest request, String bearerToken) {
        return String.join("|",
                bearerToken == null ? "" : bearerToken,
                String.join(",", clean(request.genres()).stream().sorted().toList()),
                request.mood() == null ? "" : request.mood().trim().toLowerCase(Locale.ROOT),
                request.notes() == null ? "" : request.notes().trim().toLowerCase(Locale.ROOT));
    }

    private String buildPrompt(RecommendationRequest request, Shortlist shortlist) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a movie recommendation assistant. From the CANDIDATES list only, pick the ")
                .append(PICKS_REQUESTED)
                .append(" that best fit this user (never invent a title). ")
                .append("Each entry in \"picks\" must be copied exactly from a candidate's title, without the year. ")
                .append("Write \"summary\" as one short sentence addressed to the user.\n\n");

        sb.append("Genres: ").append(shortlist.genres().isEmpty() ? "any" : String.join(", ", shortlist.genres())).append("\n");
        sb.append("Mood: ").append(request.mood() == null || request.mood().isBlank() ? "not specified" : request.mood()).append("\n");
        if (request.notes() != null && !request.notes().isBlank()) {
            sb.append("Notes: ").append(request.notes()).append("\n");
        }
        shortlist.profile().map(ProfileSnapshot::bio).filter(b -> !b.isBlank())
                .ifPresent(b -> sb.append("About the user: ").append(b).append("\n"));

        sb.append("\nCANDIDATES:\n");
        for (MovieResponse m : shortlist.movies()) {
            sb.append("- ").append(m.title());
            if (m.startYear() != null) {
                sb.append(" (").append(m.startYear()).append(")");
            }
            sb.append(" — ").append(m.genres() == null ? "n/a" : String.join(", ", m.genres()));
            if (m.avgRating() != null) {
                sb.append("; rating ").append(m.avgRating());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private Optional<MovieResponse> matchCandidate(String pickTitle, List<MovieResponse> candidates) {
        String unquoted = pickTitle.trim().replaceAll("^[\"']+|[\"']+$", "").trim();
        String normalized = TRAILING_YEAR.matcher(unquoted).replaceFirst("").trim().toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(m -> m.title().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}