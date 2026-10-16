package pt.psia.profile.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A DTO for a profile request.
 */
public record ProfileRequest(
        @NotBlank @Size(min = 1, max = 50) String displayName,
        @Size(max = 500) String bio,
        @Size(max = 10) List<String> favoriteGenres,
        @Size(max = 50) List<Integer> favoriteMovieIds,
        @Size(max = 50) List<Integer> dislikedMovieIds
) {}
