package pt.psia.reco.web.dto;

import java.util.List;

public record RecommendationResponse(
        String summary,
        List<MovieResponse> movies,
        boolean aiGenerated
) {
}
