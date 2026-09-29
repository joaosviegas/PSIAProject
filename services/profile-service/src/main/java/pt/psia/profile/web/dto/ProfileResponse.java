package pt.psia.profile.web.dto;

import java.util.List;

public record ProfileResponse(
        String userId,
        String username,
        String displayName,
        String bio,
        List<String> favoriteGenres,
        List<Integer> favoriteMovieIds,
        List<Integer> dislikedMovieIds,
        String updatedAt
) {}
