package pt.psia.reco.web.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Body for POST /api/recommendations. All fields are optional individually,
 * but genres+mood+notes shouldn't all be empty (checked in the controller).
 */
public record RecommendationRequest(
        List<String> genres,
        String mood,
        @Size(max = 500, message = "Keep additional notes under 500 characters")
        String notes
) {
}
