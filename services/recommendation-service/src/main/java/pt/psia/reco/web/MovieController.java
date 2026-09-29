package pt.psia.reco.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.*;
import pt.psia.reco.domain.GenreCatalog;
import pt.psia.reco.service.MovieService;
import pt.psia.reco.web.dto.MovieResponse;

import java.util.List;

@RestController
@RequestMapping("/api/movies")
public class MovieController {

    private final MovieService movieService;

    public MovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping("/{id}")
    public MovieResponse getById(@PathVariable Long id) {
        return movieService.getById(id);
    }

    @GetMapping
    public Page<MovieResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) Integer yearFrom,
            @RequestParam(required = false) Integer yearTo,
            @RequestParam(required = false) Double minRating,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        return movieService.search(query, genre, yearFrom, yearTo, minRating,
                PageRequest.of(Math.max(page, 0), safeSize));
    }

    @GetMapping("/genres")
    public List<String> genres() {
        return GenreCatalog.ALL;
    }
}
