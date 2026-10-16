package pt.psia.reco.web.dto;

import pt.psia.reco.domain.Movie;

import java.math.BigDecimal;
import java.util.List;

/**
 * DTO representing a movie response.
 */
public record MovieResponse(
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
) {
    public static MovieResponse from(Movie m) {
        return new MovieResponse(
                m.getId(), m.getImdbId(), m.getTitle(), m.getStartYear(),
                m.getGenreList(), m.getAvgRating(), m.getNumVotes(),
                m.getOverview(), m.getPosterUrl(), m.getDirectors(),
                m.getStars(), m.getRuntimeMinutes()
        );
    }
}
