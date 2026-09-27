package pt.psia.reco.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import pt.psia.reco.domain.Movie;
import pt.psia.reco.repository.MovieRepository;
import pt.psia.reco.web.dto.MovieResponse;

@Service
public class MovieService {

    private final MovieRepository movieRepository;

    public MovieService(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    public Page<MovieResponse> search(String query, String genre, Pageable pageable) {
        String normalizedQuery = (query == null || query.isBlank()) ? null : query.trim();
        String normalizedGenre = (genre == null || genre.isBlank()) ? null : genre.trim();

        Page<Movie> page = movieRepository.search(normalizedQuery, normalizedGenre, pageable);
        return page.map(MovieResponse::from);
    }
}
