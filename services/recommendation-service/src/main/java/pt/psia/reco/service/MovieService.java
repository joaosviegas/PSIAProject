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

    public Page<MovieResponse> search(String query, String genre,
                                      Integer yearFrom, Integer yearTo,
                                      Double minRating, Pageable pageable) {
        String q = (query == null || query.isBlank()) ? null : query.trim();
        String g = (genre == null || genre.isBlank()) ? null : genre.trim();

        Page<Movie> page = movieRepository.search(q, g, yearFrom, yearTo, minRating, pageable);
        return page.map(MovieResponse::from);
    }
}
