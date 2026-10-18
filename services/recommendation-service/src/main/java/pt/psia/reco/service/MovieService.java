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

/**
 * Service for managing movies.
 */
@Service
public class MovieService {

    private final MovieRepository movieRepository;

    /** Constructs a MovieService with the given MovieRepository. */
    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    /** Retrieves a movie by its ID, throwing a 404 error if not found. */
    public MovieResponse getById(Long id) {
        return movieRepository.findById(id)
                .map(MovieResponse::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));
    }

    /** Searches for movies based on various optional filters, returning a paginated result. */
    public Page<MovieResponse> search(String query, String genre,
                                      Integer yearFrom, Integer yearTo,
                                      Double minRating, Pageable pageable) {
        String q = (query == null || query.isBlank()) ? null : query.trim();
        String g = (genre == null || genre.isBlank()) ? null : genre.trim();

        Page<Movie> page = movieRepository.search(q, g, yearFrom, yearTo, minRating, pageable);
        return page.map(MovieResponse::from);
    }

    /** Finds candidate movies based on the given genres, excluding specified movie IDs, and limiting the result size. */
    public List<MovieResponse> candidates(List<String> genres, Collection<Long> excludeIds, int limit) {
        String genresCsv = genres == null ? "" : String.join(",", genres);
        return movieRepository.findCandidates(genresCsv, limit + excludeIds.size()).stream()
                .filter(m -> !excludeIds.contains(m.getId()))
                .limit(limit)
                .map(MovieResponse::from)
                .toList();
    }

    private static final int POOL_SIZE = 200;
    /** Extra movies pulled in because their text matches the user's notes. */
    private static final int KEYWORD_POOL_SIZE = 100;
    private static final int DISCOVER_MIN_VOTES = 5_000;
    private static final double DISCOVER_MIN_RATING = 6.5;

    /** Generates personalized movie recommendations without free-text keywords. */
    public List<MovieResponse> personalized(Collection<String> primary, Collection<String> secondary,
                                            Collection<Long> liked, Collection<Long> disliked, int limit) {
        return personalized(primary, secondary, liked, disliked, List.of(), limit);
    }

    /** Generates personalized movie recommendations based on user preferences, including liked/disliked movies and optional keywords. */
    public List<MovieResponse> personalized(Collection<String> primary, Collection<String> secondary,
                                            Collection<Long> liked, Collection<Long> disliked,
                                            Collection<String> keywords, int limit) {
        Set<Long> seen = new HashSet<>(liked);
        seen.addAll(disliked);
        List<Movie> likedMovies = liked.isEmpty() ? List.of() : movieRepository.findAllById(liked);
        List<Movie> dislikedMovies = disliked.isEmpty() ? List.of() : movieRepository.findAllById(disliked);

        Set<String> poolGenres = new LinkedHashSet<>(primary);
        poolGenres.addAll(secondary);
        poolGenres.addAll(MovieScorer.topGenres(likedMovies, 2));

        MovieScorer.Taste taste = MovieScorer.taste(primary, secondary, likedMovies, dislikedMovies);
        List<Movie> pool = new ArrayList<>(movieRepository.findPool(String.join(",", poolGenres), csv(seen), POOL_SIZE));

        List<String> safeKeywords = keywords.stream()
                .filter(k -> k != null && k.matches("[\\p{L}\\p{N}]+"))
                .toList();
        if (!safeKeywords.isEmpty()) {
            // An explicit genre choice wins
            Collection<String> keywordGenres = primary.isEmpty() ? poolGenres : primary;
            Set<Long> inPool = pool.stream().map(Movie::getId).collect(Collectors.toCollection(HashSet::new));
            movieRepository.findPoolByKeywords(String.join(",", keywordGenres), csv(seen),
                            String.join(",", safeKeywords), KEYWORD_POOL_SIZE).stream()
                    .filter(m -> inPool.add(m.getId()))
                    .forEach(pool::add);
        }
        return MovieScorer.rank(pool, taste, safeKeywords, limit).stream().map(MovieResponse::from).toList();
    }

    /** Generates a list of movies for discovery based on specified genres, excluding certain movie IDs, and limiting the result size. */
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

    /** Converts a collection of movie IDs into a comma-separated string for use in SQL queries. */
    private static String csv(Collection<Long> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** Retrieves movies by their IDs, returning a list of MovieResponse objects. */
    public List<MovieResponse> byIds(Collection<Long> ids) {
        return movieRepository.findAllById(ids).stream().map(MovieResponse::from).toList();
    }
}
