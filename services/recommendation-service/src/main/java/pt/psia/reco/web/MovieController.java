package pt.psia.reco.web;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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

    @GetMapping
    public Page<MovieResponse> search(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String genre,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        int safeSize = Math.min(Math.max(size, 1), 50);
        return movieService.search(query, genre, PageRequest.of(Math.max(page, 0), safeSize));
    }

    @GetMapping("/genres")
    public List<String> genres() {
        return GenreCatalog.ALL;
    }
}
