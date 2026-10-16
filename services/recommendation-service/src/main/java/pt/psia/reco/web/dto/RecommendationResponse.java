package pt.psia.reco.web.dto;

import java.util.List;

/**
 * DTO representing a recommendation response
 */
public record RecommendationResponse(
        String summary,
        List<MovieResponse> movies,
        boolean aiGenerated
) {
}
