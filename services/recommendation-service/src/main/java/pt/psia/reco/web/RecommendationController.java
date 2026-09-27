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

    @PostMapping
    public ResponseEntity<RecommendationResponse> recommend(
            @Valid @RequestBody RecommendationRequest request,
            @AuthenticationPrincipal Jwt jwt
    ) {
        boolean hasGenres = request.genres() != null && !request.genres().isEmpty();
        boolean hasMood = request.mood() != null && !request.mood().isBlank();
        boolean hasNotes = request.notes() != null && !request.notes().isBlank();
        if (!hasGenres && !hasMood && !hasNotes) {
            return ResponseEntity.badRequest().build();
        }

        // jwt is only null if this somehow ran unauthenticated, which the
        // security config's anyRequest().authenticated() rule prevents.
        String bearerToken = (jwt != null) ? "Bearer " + jwt.getTokenValue() : null;
        return ResponseEntity.ok(recommendationService.recommend(request, bearerToken));
    }
}
