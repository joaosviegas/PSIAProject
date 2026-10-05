package pt.psia.reco.web;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pt.psia.reco.service.RecommendationService;
import pt.psia.reco.web.dto.RecommendationRequest;
import pt.psia.reco.web.dto.RecommendationResponse;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    /** AI-curated (LLM re-ranks a taste-aware shortlist). Bounded by a hard time ceiling, falls back to the heuristic. */
    @PostMapping
    public ResponseEntity<RecommendationResponse> recommend(
            @Valid @RequestBody RecommendationRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (isEmpty(request)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(recommendationService.recommend(request, bearer(jwt)));
    }

    /** Instant taste-aware ranking, no LLM. The UI shows it right away while the AI call is still running. */
    @PostMapping("/quick")
    public ResponseEntity<RecommendationResponse> quick(
            @Valid @RequestBody RecommendationRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (isEmpty(request)) {
            return ResponseEntity.badRequest().build();
        }
        return ResponseEntity.ok(recommendationService.quick(request, bearer(jwt)));
    }

    private static boolean isEmpty(RecommendationRequest request) {
        boolean hasGenres = request.genres() != null && !request.genres().isEmpty();
        boolean hasMood = request.mood() != null && !request.mood().isBlank();
        boolean hasNotes = request.notes() != null && !request.notes().isBlank();
        return !hasGenres && !hasMood && !hasNotes;
    }

    // jwt is only null if this somehow ran unauthenticated, which the
    // security config's anyRequest().authenticated() rule prevents.
    private static String bearer(Jwt jwt) {
        return jwt != null ? "Bearer " + jwt.getTokenValue() : null;
    }
}
