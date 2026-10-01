package pt.psia.reco.web;

import org.springframework.web.bind.annotation.*;
import pt.psia.reco.service.MovieService;
import pt.psia.reco.web.dto.MovieResponse;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/internal/movies")
public class InternalMovieController {

    private final MovieService movieService;

    public InternalMovieController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping("/candidates")
    public List<MovieResponse> candidates(
            @RequestParam(required = false) List<String> genres,
            @RequestParam(required = false) Set<Long> exclude,
            @RequestParam(defaultValue = "30") int limit) {
        return movieService.candidates(genres, exclude == null ? Set.of() : exclude, Math.min(Math.max(limit, 1), 100));
    }

    @GetMapping
    public List<MovieResponse> byIds(@RequestParam List<Long> ids) {
        return movieService.byIds(ids);
    }
}
