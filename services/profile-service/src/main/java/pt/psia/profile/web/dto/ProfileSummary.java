package pt.psia.profile.web.dto;

import java.util.List;

public record ProfileSummary(
        String userId,
        String username,
        String displayName,
        List<String> favoriteGenres
) {}
