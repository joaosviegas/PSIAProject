package pt.psia.reco.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import pt.psia.reco.domain.Movie;
import pt.psia.reco.repository.MovieRepository;
import pt.psia.reco.web.dto.MovieResponse;
import pt.psia.reco.web.dto.RecommendationRequest;
import pt.psia.reco.web.dto.RecommendationResponse;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Service
public class RecommendationService {

    private static final Logger log = LoggerFactory.getLogger(RecommendationService.class);
    private static final int CANDIDATE_POOL_SIZE = 30;
    private static final int PICKS_REQUESTED = 5;
    // The candidate list shows "Title (Year)", and small models usually copy the year too.
    private static final Pattern TRAILING_YEAR = Pattern.compile("\\s*\\(\\d{4}\\)\\s*$");

    private final MovieRepository movieRepository;
    private final LlmClient llmClient;
    private final ProfileClient profileClient;

    public RecommendationService(MovieRepository movieRepository, LlmClient llmClient, ProfileClient profileClient) {
        this.movieRepository = movieRepository;
        this.llmClient = llmClient;
        this.profileClient = profileClient;
    }

    /**
     * @param bearerToken the caller's raw "Bearer &lt;token&gt;" header value,
     *                     forwarded to profile-service; null if the request
     *                     somehow reached here unauthenticated.
     */
    public RecommendationResponse recommend(RecommendationRequest request, String bearerToken) {
        String genresCsv = request.genres() == null ? "" : String.join(",", request.genres());
        List<Movie> candidates = movieRepository.findCandidates(genresCsv, CANDIDATE_POOL_SIZE);

        if (candidates.isEmpty()) {
            return new RecommendationResponse(
                    "No movies in the catalog match those genres yet — try a broader selection.",
                    List.of(), false);
        }

        Optional<String> profileSummary = profileClient.fetchInterestsSummary(bearerToken);
        String prompt = buildPrompt(request, profileSummary, candidates);

        try {
            LlmPicks llmPicks = llmClient.pickMovies(prompt);
            List<String> picks = llmPicks == null || llmPicks.picks() == null ? List.of() : llmPicks.picks();
            List<MovieResponse> movies = picks.stream()
                    .filter(p -> p != null && !p.isBlank())
                    .map(pick -> matchCandidate(pick, candidates))
                    .flatMap(Optional::stream)
                    .distinct()
                    .limit(PICKS_REQUESTED)
                    .map(MovieResponse::from)
                    .toList();

            if (!movies.isEmpty()) {
                return new RecommendationResponse(llmPicks.summary(), movies, true);
            }
            log.warn("LLM picks matched none of the candidates, falling back: {}", picks);
        } catch (Exception e) {
            log.warn("LLM call failed, falling back to top-rated candidates: {}", e.getMessage());
        }

        // Fallback: Ollama is unavailable, timed out, or returned titles we
        // couldn't match — still give the user a useful, non-AI result.
        List<MovieResponse> topByRating = candidates.stream()
                .limit(PICKS_REQUESTED)
                .map(MovieResponse::from)
                .toList();
        return new RecommendationResponse(
                "The AI assistant is unavailable right now, so here are the top-rated matches for your selection instead.",
                topByRating, false);
    }

    private String buildPrompt(RecommendationRequest request, Optional<String> profileSummary, List<Movie> candidates) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a movie recommendation assistant. Pick the best ")
                .append(PICKS_REQUESTED)
                .append(" movies for this user from the CANDIDATE list only ")
                .append("(never invent a title that isn't in the list). ")
                .append("Each entry in your \"picks\" list must be copied exactly from a candidate's title, without the year.\n\n");

        sb.append("User's requested genres: ")
                .append(request.genres() == null || request.genres().isEmpty() ? "any" : String.join(", ", request.genres()))
                .append("\n");
        sb.append("User's mood: ").append(request.mood() == null || request.mood().isBlank() ? "not specified" : request.mood()).append("\n");
        if (request.notes() != null && !request.notes().isBlank()) {
            sb.append("Additional notes from the user: ").append(request.notes()).append("\n");
        }
        profileSummary.ifPresent(s -> sb.append("User's stated interests: ").append(s).append("\n"));

        sb.append("\nCANDIDATES:\n");
        for (Movie m : candidates) {
            sb.append("- ").append(m.getTitle());
            if (m.getStartYear() != null) {
                sb.append(" (").append(m.getStartYear()).append(")");
            }
            sb.append(" — genres: ").append(m.getGenres() == null ? "n/a" : m.getGenres().replace(";", ", "));
            if (m.getAvgRating() != null) {
                sb.append(", rating: ").append(m.getAvgRating());
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private Optional<Movie> matchCandidate(String pickTitle, List<Movie> candidates) {
        String unquoted = pickTitle.trim().replaceAll("^[\"']+|[\"']+$", "").trim();
        String normalized = TRAILING_YEAR.matcher(unquoted).replaceFirst("").trim().toLowerCase(Locale.ROOT);
        return candidates.stream()
                .filter(m -> m.getTitle().toLowerCase(Locale.ROOT).equals(normalized))
                .findFirst();
    }
}