package pt.psia.reco.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import pt.psia.reco.domain.Movie;
import pt.psia.reco.repository.MovieRepository;
import pt.psia.reco.web.dto.MovieResponse;

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
}
