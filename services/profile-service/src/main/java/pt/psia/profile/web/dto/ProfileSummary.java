package pt.psia.profile.web.dto;

import java.util.List;

/**
 * A DTO for a user profile summary.
 */
public record ProfileSummary(
        String userId,
        String username,
        String displayName,
        List<String> favoriteGenres
) {}
