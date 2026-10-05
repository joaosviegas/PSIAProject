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

    @GetMapping("/personalized")
    public List<MovieResponse> personalized(
            @RequestParam(required = false) List<String> genres,
            @RequestParam(required = false) List<String> secondary,
            @RequestParam(required = false) List<Long> liked,
            @RequestParam(required = false) List<Long> disliked,
            @RequestParam(defaultValue = "12") int limit) {
        return movieService.personalized(
                genres == null ? List.of() : genres,
                secondary == null ? List.of() : secondary,
                liked == null ? List.of() : liked,
                disliked == null ? List.of() : disliked,
                Math.min(Math.max(limit, 1), 50));
    }

    @GetMapping("/discover")
    public List<MovieResponse> discover(
            @RequestParam(required = false) List<String> genres,
            @RequestParam(required = false) Set<Long> exclude,
            @RequestParam(defaultValue = "8") int limit) {
        return movieService.discover(
                genres == null ? List.of() : genres,
                exclude == null ? Set.of() : exclude,
                Math.min(Math.max(limit, 1), 30));
    }

    @GetMapping
    public List<MovieResponse> byIds(@RequestParam List<Long> ids) {
        return movieService.byIds(ids);
    }
}
