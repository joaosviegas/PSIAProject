package pt.psia.agent.client.dto;

import java.util.List;

/**
 * A DTO for a user profile.
 */
public record ProfileDto(
        String userId,
        String username,
        String displayName,
        String bio,
        List<String> favoriteGenres,
        List<Long> favoriteMovieIds,
        List<Long> dislikedMovieIds,
        String updatedAt
) {}
