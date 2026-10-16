package pt.psia.agent.client.dto;

import java.math.BigDecimal;
import java.util.List;

/**
 * A DTO for a movie.
 */
public record MovieDto(
        Long id,
        String imdbId,
        String title,
        Integer startYear,
        List<String> genres,
        BigDecimal avgRating,
        Integer numVotes,
        String overview,
        String posterUrl,
        String directors,
        String stars,
        Integer runtimeMinutes
) {}
