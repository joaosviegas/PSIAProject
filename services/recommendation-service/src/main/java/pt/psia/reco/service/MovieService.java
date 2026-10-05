package pt.psia.reco.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pt.psia.reco.domain.Movie;
import pt.psia.reco.repository.MovieRepository;
import pt.psia.reco.web.dto.MovieResponse;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MovieService {

    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public MovieResponse getById(Long id) {
        return movieRepository.findById(id)
                .map(MovieResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));
    }

    public Page<MovieResponse> search(String query, String genre,
                                      Integer yearFrom, Integer yearTo,
                                      Double minRating, Pageable pageable) {
        String q = (query == null || query.isBlank()) ? null : query.trim();
        String g = (genre == null || genre.isBlank()) ? null : genre.trim();

        Page<Movie> page = movieRepository.search(q, g, yearFrom, yearTo, minRating, pageable);
        return page.map(MovieResponse::from);
    }

    public List<MovieResponse> candidates(List<String> genres, Collection<Long> excludeIds, int limit) {
        String genresCsv = genres == null ? "" : String.join(",", genres);
        return movieRepository.findCandidates(genresCsv, limit + excludeIds.size()).stream()
                .filter(m -> !excludeIds.contains(m.getId()))
                .limit(limit)
                .map(MovieResponse::from)
                .toList();
    }

    private static final int POOL_SIZE = 200;
    private static final int DISCOVER_MIN_VOTES = 5_000;
    private static final double DISCOVER_MIN_RATING = 6.5;

    /**
     * Instant, taste-aware ranking (see {@link MovieScorer}). {@code primary} genres are what the person
     * explicitly chose, {@code secondary} are softer hints (mood, saved profile). Liked/disliked movies
     * both steer the score and are excluded from the result.
     */
    public List<MovieResponse> personalized(Collection<String> primary, Collection<String> secondary,
                                            Collection<Long> liked, Collection<Long> disliked, int limit) {
        Set<Long> seen = new HashSet<>(liked);
        seen.addAll(disliked);
        List<Movie> likedMovies = liked.isEmpty() ? List.of() : movieRepository.findAllById(liked);
        List<Movie> dislikedMovies = disliked.isEmpty() ? List.of() : movieRepository.findAllById(disliked);

        Set<String> poolGenres = new LinkedHashSet<>(primary);
        poolGenres.addAll(secondary);
        poolGenres.addAll(MovieScorer.topGenres(likedMovies, 2));

        MovieScorer.Taste taste = MovieScorer.taste(primary, secondary, likedMovies, dislikedMovies);
        List<Movie> pool = movieRepository.findPool(String.join(",", poolGenres), csv(seen), POOL_SIZE);
        return MovieScorer.rank(pool, taste, limit).stream().map(MovieResponse::from).toList();
    }

    /** Random picks from the given genres, preferring well-rated, well-known titles; tops up with anything if the genre is small. */
    public List<MovieResponse> discover(Collection<String> genres, Collection<Long> exclude, int limit) {
        String genresCsv = String.join(",", genres);
        List<Movie> picked = new ArrayList<>(
                movieRepository.findRandom(genresCsv, csv(exclude), DISCOVER_MIN_VOTES, DISCOVER_MIN_RATING, limit));
        if (picked.size() < limit) {
            Set<Long> skip = new HashSet<>(exclude);
            picked.forEach(m -> skip.add(m.getId()));
            picked.addAll(movieRepository.findRandom(genresCsv, csv(skip), 0, 0, limit - picked.size()));
        }
        return picked.stream().map(MovieResponse::from).toList();
    }

    private static String csv(Collection<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    public List<MovieResponse> byIds(Collection<Long> ids) {
        return movieRepository.findAllById(ids).stream().map(MovieResponse::from).toList();
    }
}
